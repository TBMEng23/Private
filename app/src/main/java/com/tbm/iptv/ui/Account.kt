package com.tbm.iptv.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import com.tbm.iptv.ui.TvButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.tbm.iptv.ui.TvIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.tbm.iptv.ui.TvOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tbm.iptv.ui.TvTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tbm.iptv.L
import com.tbm.iptv.data.Cloud
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** כניסה עם Google דרך Credential Manager – מחזיר ID token */
suspend fun googleIdToken(ctx: Context): String {
    val web = Cloud.webClientId(ctx) ?: throw IllegalStateException(L("כניסה עם Google עדיין לא הופעלה ב-Firebase"))
    val req = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(web).build()).build()
    val cred = CredentialManager.create(ctx).getCredential(ctx, req).credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        return GoogleIdTokenCredential.createFrom(cred.data).idToken
    throw IllegalStateException(L("כניסה עם Google נכשלה"))
}

/** חשבון TBM: כניסה (אימייל/Google/קוד מהטלפון), סנכרון, חיבור טלוויזיה */
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pairTv by remember { mutableStateOf(false) }
    var tvCode by remember { mutableStateOf<String?>(null) }
    fun run(block: suspend () -> Unit) {
        busy = true; msg = null
        scope.launch {
            try { block() } catch (e: Exception) { msg = Cloud.friendly(e) }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(L("חשבון וסנכרון"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                if (Cloud.signedIn) {
                    Text(L("מחובר כ-"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Cloud.email.ifBlank { L("חשבון Google") }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(Cloud.status, color = MaterialTheme.colorScheme.primary)
                    if (Cloud.lastSync > 0) Text(L("סנכרון אחרון: {0}",
                        SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(Cloud.lastSync))), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(L("נשמרים ומסתנכרנים בין כל המכשירים: מקורות תוכן, מועדפים, פרופילים וקוד הורים, הגדרות ומפתחות, ומיקום הצפייה בכל סרט ופרק."),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { Cloud.syncNow(); Toast.makeText(ctx, L("מסנכרן…"), Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.fillMaxWidth()) { Text(L("🔄 סנכרן עכשיו")) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { pairTv = true }, modifier = Modifier.fillMaxWidth()) { Text(L("📺 חבר טלוויזיה / סטרימר בקוד")) }
                    Spacer(Modifier.height(8.dp))
                    var confirmOut by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirmOut = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("התנתק"), color = MaterialTheme.colorScheme.error)
                    }
                    if (confirmOut) AlertDialog(
                        onDismissRequest = { confirmOut = false },
                        title = { Text(L("להתנתק?")) },
                        text = { Text(L("כל ההגדרות שמורות בחשבון שלך ויחזרו כשתתחבר שוב. מהמכשיר הזה הן יימחקו, כדי שמי שיתחבר אחריך לא יראה אותן.")) },
                        confirmButton = { TextButton(onClick = {
                            confirmOut = false
                            Cloud.signOut()
                            (ctx as? android.app.Activity)?.recreate()
                        }) { Text(L("התנתק"), color = MaterialTheme.colorScheme.error) } },
                        dismissButton = { TextButton(onClick = { confirmOut = false }) { Text(L("ביטול")) } },
                    )
                } else {
                    SignInForm(busy, ::run)
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(L("אולי אחר כך")) }
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(18.dp))
                    // בטלוויזיה – חיבור בקוד מהטלפון במקום להקליד עם השלט
                    Text(L("בטלוויזיה או בסטרימר?"), fontWeight = FontWeight.Bold)
                    Text(L("קבל קוד והזן אותו בטלפון שכבר מחובר – בלי להקליד עם השלט."), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(enabled = !busy, onClick = {
                        run {
                            val ok = Cloud.tvPair { tvCode = it }
                            tvCode = null
                            msg = if (ok) L("מחובר ✓") else L("הקוד פג – נסה שוב")
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(L("📺 התחבר עם קוד מהטלפון")) }
                }
                if (busy && tvCode == null) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            }
        }
    }
    tvCode?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(L("חיבור למכשיר הזה")) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(L("בטלפון: הגדרות ← חשבון וסנכרון ← \"חבר טלוויזיה / סטרימר בקוד\", והזן:"), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(code.chunked(4).joinToString(" - "), fontSize = 38.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                    Text(L("ממתין לטלפון… (הקוד בתוקף 5 דקות)"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {},
        )
    }
    if (pairTv) PhonePairDialog { pairTv = false }
}

@Composable
private fun SignInForm(busy: Boolean, run: (suspend () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Text(L("התחבר כדי לשמור ולסנכרן את כל ההגדרות בין הטלפון, הטאבלט והסטרימר"), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    if (Cloud.webClientId(ctx) != null) {
        Button(enabled = !busy, onClick = { run { Cloud.signInGoogle(googleIdToken(ctx)) } }, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White,
                contentColor = androidx.compose.ui.graphics.Color.Black)) {
            Text("G  ", fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color(0xFF4285F4))
            Text(L("המשך עם Google"), fontWeight = FontWeight.Bold)
        }
        Text(L("או"), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedTextField(email, { email = it }, label = { Text(L("אימייל")) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(pass, { pass = it }, label = { Text(L("סיסמה")) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    val ok = email.contains("@") && pass.length >= 6
    Button(enabled = ok && !busy, onClick = { run { Cloud.signIn(email, pass) } }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(L("התחבר")) }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(enabled = ok && !busy, onClick = { run { Cloud.register(email, pass) } }, modifier = Modifier.fillMaxWidth()) { Text(L("צור חשבון חדש")) }
    TextButton(enabled = email.contains("@") && !busy, onClick = {
        run { Cloud.resetPassword(email); Toast.makeText(ctx, L("נשלח מייל לאיפוס סיסמה"), Toast.LENGTH_LONG).show() }
    }, modifier = Modifier.fillMaxWidth()) { Text(L("שכחתי סיסמה")) }
}

/** בטלפון המחובר: הזנת הקוד שמוצג בטלוויזיה */
@Composable
private fun PhonePairDialog(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val google = Cloud.email.isBlank() || Cloud.user?.providerData?.any { it.providerId == "google.com" } == true
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(L("חיבור טלוויזיה / סטרימר")) },
        text = {
            Column {
                Text(L("בטלוויזיה: הגדרות ← חשבון וסנכרון ← \"התחבר עם קוד מהטלפון\". הזן כאן את הקוד שמופיע שם:"), fontSize = 13.sp)
                OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, label = { Text(L("קוד (8 תווים)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (!google) OutlinedTextField(pass, { pass = it }, label = { Text(L("הסיסמה של החשבון (לאישור)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = code.length == 8 && (google || pass.length >= 6) && !busy, onClick = {
                busy = true; err = null
                scope.launch {
                    try {
                        if (google) Cloud.phoneSendPair(code, null, googleIdToken(ctx))
                        else {
                            Cloud.signIn(Cloud.email, pass)   // מוודא שהסיסמה נכונה
                            Cloud.phoneSendPair(code, pass, null)
                        }
                        Toast.makeText(ctx, L("נשלח – הטלוויזיה תתחבר בעוד רגע"), Toast.LENGTH_LONG).show()
                        onDone()
                    } catch (e: Exception) {
                        err = if ((e.message ?: "").contains("NOT_FOUND") || (e.message ?: "").contains("No document"))
                            L("הקוד לא נמצא או שפג תוקפו") else Cloud.friendly(e)
                    }
                    busy = false
                }
            }) { Text(L("חבר")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(L("ביטול")) } },
    )
}
