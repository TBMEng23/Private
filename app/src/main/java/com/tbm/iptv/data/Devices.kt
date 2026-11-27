package com.tbm.iptv.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * רישום התקנות – כל מכשיר שפותח את האפליקציה נרשם ב-devices/{androidId}
 * (שם, דגם, גרסה, מתי נראה לאחרונה, אימייל אם מחובר). רק המנהל יכול לקרוא את הרשימה ולחסום מכשיר.
 */
object Devices {
    data class Dev(
        val id: String, val name: String, val model: String, val android: String, val app: String,
        val tv: Boolean, val email: String, val first: Long, val last: Long, val blocked: Boolean, val note: String,
    )

    private val db: FirebaseFirestore? get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    private val auth: FirebaseAuth? get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: ListenerRegistration? = null

    /** המכשיר הזה נחסם על ידי המנהל */
    var blocked by mutableStateOf(Prefs.str("local_dev_blocked") == "1")
        private set
    /** המשתמש המחובר הוא המנהל (מותר לו לקרוא את רשימת המכשירים) */
    var isAdmin by mutableStateOf(Prefs.str("local_admin") == "1")
        private set

    @SuppressLint("HardwareIds")
    fun id(ctx: Context): String = runCatching {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" } ?: Prefs.deviceId

    private fun name(ctx: Context): String {
        val n = runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
            ?: runCatching { Settings.Secure.getString(ctx.contentResolver, "bluetooth_name") }.getOrNull()
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return n?.takeIf { it.isNotBlank() } ?: model
    }

    private fun isTv(ctx: Context) = runCatching {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)

    /** נקרא בכל פתיחה של האפליקציה */
    private var lastReg = 0L
    fun register(ctx: Context) {
        val app = ctx.applicationContext
        val t = System.currentTimeMillis()
        if (listener != null && t - lastReg < 30 * 60_000L) return
        lastReg = t
        scope.launch {
            runCatching {
                val a = auth ?: return@launch
                val store = db ?: return@launch
                if (a.currentUser == null) a.signInAnonymously().await()
                val id = id(app)
                val ref = store.collection("devices").document(id)
                val now = System.currentTimeMillis()
                val data = hashMapOf<String, Any>(
                    "name" to name(app),
                    "model" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    "android" to Build.VERSION.RELEASE,
                    "app" to com.tbm.iptv.BuildConfigLite.VERSION,
                    "tv" to isTv(app),
                    "email" to (a.currentUser?.email ?: ""),
                    "uid" to (a.currentUser?.uid ?: ""),
                    "last" to now,
                )
                if (Prefs.str("local_dev_first").isBlank()) {
                    val exists = runCatching { ref.get().await().exists() }.getOrDefault(true)
                    if (!exists) data["first"] = now
                    Prefs.setStrQuiet("local_dev_first", "1")
                }
                ref.set(data, SetOptions.merge()).await()
                watch(id)
                checkAdmin()
            }
        }
    }

    private fun watch(id: String) {
        if (listener != null) return
        listener = db?.collection("devices")?.document(id)?.addSnapshotListener { s, _ ->
            val b = s?.getBoolean("blocked") == true
            Prefs.setStrQuiet("local_dev_blocked", if (b) "1" else "")
            blocked = b
        }
    }

    /** בודק אם המשתמש המחובר מורשה לקרוא את רשימת המכשירים (נקבע בכללי Firebase לפי האימייל) */
    fun checkAdmin() {
        scope.launch {
            val ok = Cloud.signedInNow() && runCatching {
                db!!.collection("devices").limit(1).get().await(); true
            }.getOrDefault(false)
            Prefs.setStrQuiet("local_admin", if (ok) "1" else "")
            isAdmin = ok
        }
    }

    suspend fun list(): List<Dev> {
        val snap = db!!.collection("devices").orderBy("last", Query.Direction.DESCENDING).get().await()
        return snap.documents.map { d ->
            Dev(
                id = d.id, name = d.getString("name") ?: "", model = d.getString("model") ?: "",
                android = d.getString("android") ?: "", app = d.getString("app") ?: "",
                tv = d.getBoolean("tv") == true, email = d.getString("email") ?: "",
                first = d.getLong("first") ?: 0L, last = d.getLong("last") ?: 0L,
                blocked = d.getBoolean("blocked") == true, note = d.getString("note") ?: "",
            )
        }
    }

    suspend fun setBlocked(id: String, b: Boolean) {
        db!!.collection("devices").document(id).set(mapOf("blocked" to b), SetOptions.merge()).await()
    }

    suspend fun setNote(id: String, note: String) {
        db!!.collection("devices").document(id).set(mapOf("note" to note), SetOptions.merge()).await()
    }

    suspend fun delete(id: String) { db!!.collection("devices").document(id).delete().await() }
}
