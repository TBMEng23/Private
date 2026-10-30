package com.tbm.iptv.ui

import com.tbm.iptv.L

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tbm.iptv.data.Parental
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Profile

/** חלון הזנת קוד הורים – מוצג מעל כל המסכים כש-Parental.request פעיל */
@Composable
fun PinDialog() {
    val req = Parental.request ?: return
    val ctx = LocalContext.current
    var pin by remember(req) { mutableStateOf("") }
    var err by remember(req) { mutableStateOf(false) }

    fun ok() { Parental.unlocked = true; Parental.request = null; req.onOk() }

    LaunchedEffect(req) {
        val act = ctx as? FragmentActivity
        if (Prefs.biometric && act != null &&
            BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (Parental.request === req) ok()
                    }
                })
            runCatching {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(req.title)
                        .setSubtitle(L("TBM Player – בקרת הורים"))
                        .setNegativeButtonText(L("הזן קוד"))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build()
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { Parental.request = null },
        title = { Text(req.title) },
        text = {
            Column {
                Text(L("הזן את קוד ההורים"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    pin, { v -> pin = v.filter { it.isDigit() }.take(8); err = false },
                    singleLine = true, isError = err,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err) Text(L("קוד שגוי"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (Parental.check(pin)) ok() else err = true }) { Text(L("אישור")) }
        },
        dismissButton = { TextButton(onClick = { Parental.request = null }) { Text(L("ביטול")) } },
    )
}

/** "מי צופה?" – בחירת פרופיל. מעבר לפרופיל מבוגר דורש קוד */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(onDone: () -> Unit) {
    val ver = Prefs.version.intValue
    val list = remember(ver) { Prefs.profiles() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TBM Player", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(L("מי צופה?"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            list.forEach { p -> ProfileTile(p) { choose(p, onDone) } }
        }
    }
}

fun choose(p: Profile, onDone: () -> Unit) {
    val switchToAdult = !p.kids && (Prefs.isKids || Prefs.profiles().any { it.kids })
    if (switchToAdult && Parental.hasPin()) {
        Parental.requireAlways(L("כניסה לפרופיל {0}", p.name)) { Parental.switchProfile(p); Parental.unlocked = true; onDone() }
    } else {
        Parental.switchProfile(p); onDone()
    }
}

@Composable
private fun ProfileTile(p: Profile, onClick: () -> Unit) {
    FocusItem(onClick = onClick) {
        Column(Modifier.padding(10.dp).width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(CircleShape)
                    .background(if (p.kids) Color(0xFF00A86B) else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (p.kids) Icons.Filled.ChildCare else Icons.Filled.Person, null, tint = Color.White,
                    modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(p.name, fontWeight = FontWeight.Bold)
            Text(if (p.kids) L("ילדים – תוכן מאושר") else L("מבוגר – תוכן מלא"), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
