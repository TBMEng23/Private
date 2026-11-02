package com.tbm.iptv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.tbm.iptv.L
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * חשבון TBM וסנכרון בענן (Firebase):
 * users/{uid} = { data: JSON של כל ההגדרות, updated, device }
 * pair/{code} = חיבור טלוויזיה/סטרימר בקוד מהטלפון
 */
object Cloud {
    private val auth: FirebaseAuth? get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val db: FirebaseFirestore? get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    var user by mutableStateOf<FirebaseUser?>(null)
        private set
    /** הודעת מצב לתצוגה ("מסונכרן", "מעלה…", שגיאה) */
    var status by mutableStateOf("")
    var lastSync by mutableStateOf(0L)

    val signedIn get() = user?.let { !it.isAnonymous } == true
    val email get() = user?.email ?: ""

    private var applying = false
    private var uploadJob: Job? = null
    private var listener: ListenerRegistration? = null

    fun init() {
        val a = auth ?: return
        lastSync = Prefs.str("cloud_last").toLongOrNull() ?: 0L
        a.addAuthStateListener { fa ->
            user = fa.currentUser
            listener?.remove(); listener = null
            if (signedIn) startSync()
        }
        Prefs.onChange = { if (signedIn && !applying) scheduleUpload() }
    }

    private fun doc() = user?.uid?.let { db?.collection("users")?.document(it) }

    // ---------------------------------------------------------------- סנכרון
    private fun startSync() {
        val d = doc() ?: return
        Repo.scope.launch {
            try {
                status = L("מסנכרן…")
                val snap = d.get().await()
                val remote = snap.getString("data")
                if (remote != null && (snap.getLong("updated") ?: 0L) > lastSync) applyRemote(remote, snap.getLong("updated") ?: 0L)
                else if (remote == null) upload()
                status = L("מסונכרן ✓")
            } catch (e: Exception) { status = L("שגיאת סנכרון: {0}", e.message ?: "") }
            // עדכונים חיים ממכשירים אחרים
            listener = d.addSnapshotListener { s, _ ->
                if (s == null || !s.exists()) return@addSnapshotListener
                val upd = s.getLong("updated") ?: 0L
                if (s.getString("device") != Prefs.deviceId && upd > lastSync) {
                    s.getString("data")?.let { applyRemote(it, upd) }
                }
            }
        }
    }

    private fun applyRemote(data: String, updated: Long) {
        val before = Prefs.activePlaylist()
        applying = true
        try { Prefs.importCloud(data) } finally { applying = false }
        setLast(updated)
        val after = Prefs.activePlaylist()
        if (after != null && after != before) Repo.loadAsync(after)
        status = L("עודכן מהענן ✓")
    }

    private fun setLast(t: Long) { lastSync = t; Prefs.setStrQuiet("cloud_last", t.toString()) }

    private fun scheduleUpload() {
        uploadJob?.cancel()
        uploadJob = Repo.scope.launch { delay(5000); upload() }
    }

    suspend fun upload() {
        val d = doc() ?: return
        try {
            val now = System.currentTimeMillis()
            d.set(mapOf("data" to Prefs.exportCloud(), "updated" to now, "device" to Prefs.deviceId), SetOptions.merge()).await()
            setLast(now)
            status = L("מסונכרן ✓")
        } catch (e: Exception) { status = L("שגיאת סנכרון: {0}", e.message ?: "") }
    }

    fun syncNow() { Repo.scope.launch { upload() } }

    // ---------------------------------------------------------------- כניסה
    suspend fun signIn(email: String, pass: String) { auth!!.signInWithEmailAndPassword(email.trim(), pass).await() }
    suspend fun register(email: String, pass: String) { auth!!.createUserWithEmailAndPassword(email.trim(), pass).await() }
    suspend fun resetPassword(email: String) { auth!!.sendPasswordResetEmail(email.trim()).await() }
    suspend fun signInGoogle(idToken: String) { auth!!.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }

    fun signOut() {
        listener?.remove(); listener = null
        auth?.signOut()
        status = ""
    }

    /** מזהה לקוח Web (נוצר אוטומטית מ-google-services.json כשכניסת Google מופעלת) */
    fun webClientId(ctx: Context): String? {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (id == 0) null else ctx.getString(id)
    }

    /** הודעת שגיאה ידידותית */
    fun friendly(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            "password is invalid" in m || "INVALID_LOGIN_CREDENTIALS" in m || "credential is incorrect" in m -> L("אימייל או סיסמה שגויים")
            "no user record" in m -> L("אין חשבון עם האימייל הזה")
            "already in use" in m -> L("כבר קיים חשבון עם האימייל הזה – התחבר")
            "badly formatted" in m -> L("כתובת אימייל לא תקינה")
            "at least 6" in m || "WEAK_PASSWORD" in m -> L("הסיסמה צריכה לפחות 6 תווים")
            "network" in m.lowercase() -> L("אין חיבור לאינטרנט")
            "CONFIGURATION_NOT_FOUND" in m || "operation is not allowed" in m -> L("שיטת הכניסה הזו לא הופעלה ב-Firebase")
            else -> m
        }
    }

    // ---------------------------------------------------------------- חיבור טלוויזיה בקוד
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** בטלוויזיה: יוצר קוד, מחכה שהטלפון ישלח פרטי כניסה, ומתחבר */
    suspend fun tvPair(onCode: (String) -> Unit): Boolean {
        val a = auth ?: return false
        val store = db ?: return false
        if (a.currentUser == null) a.signInAnonymously().await()
        val anonUid = a.currentUser!!.uid
        val rnd = SecureRandom()
        val code = (1..8).map { CODE_CHARS[rnd.nextInt(CODE_CHARS.length)] }.joinToString("")
        val ref = store.collection("pair").document(code)
        ref.set(mapOf("tv" to anonUid, "ts" to System.currentTimeMillis())).await()
        onCode(code)
        val until = System.currentTimeMillis() + 5 * 60_000L
        try {
            while (System.currentTimeMillis() < until) {
                delay(2500)
                val s = ref.get().await()
                val payload = s.getString("p") ?: continue
                val o = JSONObject(decrypt(payload, code))
                ref.delete()
                val anon = a.currentUser
                when {
                    o.has("g") -> a.signInWithCredential(GoogleAuthProvider.getCredential(o.getString("g"), null)).await()
                    else -> a.signInWithEmailAndPassword(o.getString("e"), o.getString("pw")).await()
                }
                if (anon != null && anon.isAnonymous && anon.uid != a.currentUser?.uid) runCatching { anon.delete() }
                return true
            }
        } finally { runCatching { ref.delete() } }
        return false
    }

    /** בטלפון המחובר: שולח לטלוויזיה את פרטי הכניסה (מוצפנים בקוד) */
    suspend fun phoneSendPair(code: String, password: String?, googleToken: String?) {
        val c = code.trim().uppercase().replace(" ", "").replace("-", "")
        val o = JSONObject()
        if (googleToken != null) o.put("g", googleToken) else o.put("e", email).put("pw", password ?: "")
        db!!.collection("pair").document(c).update(mapOf("p" to encrypt(o.toString(), c))).await()
    }

    private fun key(code: String): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(code.toCharArray(), "tbm-pair-v1".toByteArray(), 20000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    private fun encrypt(text: String, code: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(code), GCMParameterSpec(128, iv))
        return android.util.Base64.encodeToString(iv + c.doFinal(text.toByteArray()), android.util.Base64.NO_WRAP)
    }

    private fun decrypt(b64: String, code: String): String {
        val all = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(code), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(c.doFinal(all.copyOfRange(12, all.size)))
    }
}
