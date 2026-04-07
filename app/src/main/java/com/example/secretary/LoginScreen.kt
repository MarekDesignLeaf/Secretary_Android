package com.example.secretary

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: SecretaryViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val prefs = remember { context.getSharedPreferences("secretary_settings", 0) }
    val hasSavedCredentials = remember { prefs.getString("saved_email", null) != null }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showManualLogin by remember { mutableStateOf(!hasSavedCredentials) }

    // Try biometric on first load if credentials saved
    LaunchedEffect(Unit) {
        if (hasSavedCredentials && activity != null) {
            tryBiometricLogin(activity, prefs, viewModel) { msg -> error = msg; showManualLogin = true }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Secretary DesignLeaf") }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Přihlášení", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            if (hasSavedCredentials && !showManualLogin) {
                // Biometric login mode
                Text("Přihlaste se otiskem prstu", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        if (activity != null) {
                            tryBiometricLogin(activity, prefs, viewModel) { msg -> error = msg; showManualLogin = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("🔓", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("Otisk prstu", fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showManualLogin = true }) {
                    Text("Přihlásit se heslem")
                }
            } else {
                // Manual email/password login
                OutlinedTextField(
                    value = email, onValueChange = { email = it; error = null },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text("Heslo") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            loading = true; error = null
                            viewModel.login(email.trim(), password) { msg ->
                                loading = false; error = msg
                                if (msg == null) { prefs.edit().putString("saved_email", email.trim()).putString("saved_pass", password).apply() }
                            }
                        }
                    }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Zobrazit heslo")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) { error = "Vyplňte email a heslo"; return@Button }
                        loading = true; error = null
                        viewModel.login(email.trim(), password) { msg ->
                            loading = false; error = msg
                            if (msg == null) { prefs.edit().putString("saved_email", email.trim()).putString("saved_pass", password).apply() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !loading
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Přihlásit se", fontSize = 16.sp)
                }
                if (hasSavedCredentials) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showManualLogin = false }) {
                        Text("Zpět na otisk prstu")
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Color.Red, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text("DesignLeaf CRM", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

private fun tryBiometricLogin(
    activity: FragmentActivity,
    prefs: android.content.SharedPreferences,
    viewModel: SecretaryViewModel,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometrie není k dispozici na tomto zařízení")
        return
    }

    val email = prefs.getString("saved_email", null)
    val pass = prefs.getString("saved_pass", null)
    if (email == null || pass == null) {
        onError("Nejdříve se přihlaste heslem")
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Log.d("LoginScreen", "Biometric auth succeeded, logging in as $email")
            viewModel.login(email, pass) { msg -> if (msg != null) onError(msg) }
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                onError("Chyba biometrie: $errString")
            }
        }
        override fun onAuthenticationFailed() { /* one attempt failed, prompt stays open */ }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Secretary DesignLeaf")
        .setSubtitle("Přihlaste se otiskem prstu")
        .setNegativeButtonText("Použít heslo")
        .build()

    prompt.authenticate(promptInfo)
}
