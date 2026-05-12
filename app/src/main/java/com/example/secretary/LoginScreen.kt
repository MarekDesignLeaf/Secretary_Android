package com.example.secretary

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val settingsManager = remember { SettingsManager(context) }

    var biometricProfiles by remember { mutableStateOf(settingsManager.getBiometricProfiles()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var selectedPendingDisplayName by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showCurrentPassword by rememberSaveable { mutableStateOf(false) }
    var showNewPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirmPassword by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgotPassword by rememberSaveable { mutableStateOf(false) }
    var showResetPassword by rememberSaveable { mutableStateOf(false) }
    var showManualLogin by rememberSaveable { mutableStateOf(biometricProfiles.isEmpty()) }
    var showBiometricOptIn by remember { mutableStateOf(false) }
    var pendingBiometricPassword by remember { mutableStateOf("") }
    var autoBiometricTriggered by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadFirstLoginUsers()
    }

    LaunchedEffect(state.loginNotice) {
        if (!state.loginNotice.isNullOrBlank()) error = state.loginNotice
    }

    LaunchedEffect(state.mustChangePassword) {
        if (state.mustChangePassword) {
            showManualLogin = true
            if (currentPassword.isBlank()) currentPassword = password
        }
    }

    LaunchedEffect(state.awaitingBiometricEnrollment, email, pendingBiometricPassword) {
        if (state.awaitingBiometricEnrollment && email.isNotBlank() && pendingBiometricPassword.isNotBlank()) {
            showBiometricOptIn = true
        }
    }

    LaunchedEffect(activity, biometricProfiles, state.mustChangePassword, showManualLogin) {
        if (activity == null || autoBiometricTriggered || state.mustChangePassword || showManualLogin) return@LaunchedEffect
        val target = settingsManager.getLastBiometricEmail()
            ?.let { last -> biometricProfiles.firstOrNull { it.email.equals(last, ignoreCase = true) } }
            ?: biometricProfiles.singleOrNull()
        if (target != null) {
            autoBiometricTriggered = true
            tryBiometricLogin(activity, target, viewModel, settingsManager) { msg ->
                error = msg
                showManualLogin = true
            }
        }
    }

    fun choosePendingUser(user: FirstLoginUser) {
        selectedPendingDisplayName = user.display_name.ifBlank { user.email }
        email = user.email
        password = "12345"
        currentPassword = "12345"
        showManualLogin = true
        error = Strings.useTemporaryPasswordHint
    }

    fun saveBiometricChoice(enable: Boolean) {
        if (enable) {
            settingsManager.saveBiometricProfile(
                BiometricProfile(
                    userId = state.currentUserId,
                    displayName = state.currentUserDisplayName ?: selectedPendingDisplayName.ifBlank { email.substringBefore("@") },
                    email = email.trim(),
                    password = pendingBiometricPassword
                )
            )
            biometricProfiles = settingsManager.getBiometricProfiles()
        }
        showBiometricOptIn = false
        pendingBiometricPassword = ""
        viewModel.finalizeLoginAfterCredentialSetup()
    }

    if (showBiometricOptIn) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(Strings.enableBiometricLogin) },
            text = { Text(Strings.biometricLoginQuestion) },
            confirmButton = {
                Button(onClick = { saveBiometricChoice(true) }) { Text(Strings.enable) }
            },
            dismissButton = {
                TextButton(onClick = { saveBiometricChoice(false) }) { Text(Strings.notNow) }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(VersionInfo.APP_NAME) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.mustChangePassword) Strings.firstLoginChangePasswordTitle else Strings.chooseProfile,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (state.mustChangePassword) {
                Text(Strings.firstLoginChangePasswordHint, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                if (selectedPendingDisplayName.isNotBlank()) {
                    Text(selectedPendingDisplayName, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text(Strings.email) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error = null },
                    label = Strings.currentPassword,
                    visible = showCurrentPassword,
                    onToggleVisible = { showCurrentPassword = !showCurrentPassword },
                    enabled = !loading
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = Strings.newPassword,
                    visible = showNewPassword,
                    onToggleVisible = { showNewPassword = !showNewPassword },
                    enabled = !loading
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = Strings.confirmPassword,
                    visible = showConfirmPassword,
                    onToggleVisible = { showConfirmPassword = !showConfirmPassword },
                    enabled = !loading
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        when {
                            currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank() -> error = Strings.fillEmailPassword
                            newPassword != confirmPassword -> error = Strings.passwordsDoNotMatch
                            else -> {
                                loading = true
                                error = null
                                viewModel.completeFirstPasswordChange(currentPassword, newPassword) { ok, msg ->
                                    loading = false
                                    if (ok) {
                                        pendingBiometricPassword = newPassword
                                    } else {
                                        error = msg
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !loading
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text(Strings.changePassword)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.logout() }, enabled = !loading) {
                    Text(Strings.logout)
                }
            } else {
                if (biometricProfiles.isNotEmpty()) {
                    Text(Strings.biometricProfilesLabel, modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    biometricProfiles.forEach { profile ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (activity != null) {
                                        tryBiometricLogin(activity, profile, viewModel, settingsManager) { msg ->
                                            error = msg
                                            showManualLogin = true
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.displayName.ifBlank { profile.email.substringBefore("@") }, fontWeight = FontWeight.SemiBold)
                                    Text(profile.email, fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(Strings.fingerprint, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    Text(Strings.noSavedProfiles, color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))
                Text(Strings.firstLoginUsersLabel, modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.SemiBold)
                Text(Strings.firstLoginUsersHint, modifier = Modifier.align(Alignment.Start), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                if (state.firstLoginUsers.isNotEmpty()) {
                    state.firstLoginUsers.forEach { user ->
                        OutlinedButton(
                            onClick = { choosePendingUser(user) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(user.display_name.ifBlank { user.email }, fontWeight = FontWeight.SemiBold)
                                Text(user.email, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showManualLogin = !showManualLogin }) {
                    Text(if (showManualLogin) Strings.backToFingerprint else Strings.loginWithPassword)
                }

                if (showManualLogin) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null; selectedPendingDisplayName = "" },
                        label = { Text(Strings.email) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = Strings.password,
                        visible = showPassword,
                        onToggleVisible = { showPassword = !showPassword },
                        enabled = !loading,
                        onDone = {
                            if (email.isBlank() || password.isBlank()) {
                                error = Strings.fillEmailPassword
                            } else {
                                loading = true
                                error = null
                                viewModel.login(email.trim(), password, false) { msg ->
                                    loading = false
                                    error = msg
                                    if (msg == null && !viewModel.uiState.value.mustChangePassword) {
                                        pendingBiometricPassword = password
                                    }
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                error = Strings.fillEmailPassword
                                return@Button
                            }
                            loading = true
                            error = null
                            viewModel.login(email.trim(), password, false) { msg ->
                                loading = false
                                error = msg
                                if (msg == null && !viewModel.uiState.value.mustChangePassword) {
                                    pendingBiometricPassword = password
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !loading
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                        else Text(Strings.signInAs)
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Color.Red, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showForgotPassword = true }) {
                Text("Forgot password?", fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(VersionInfo.COMPANY + " CRM", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showForgotPassword) {
        ForgotPasswordDialog(
            onDismiss = { showForgotPassword = false },
            onTokenReceived = { showForgotPassword = false; showResetPassword = true }
        )
    }
    if (showResetPassword) {
        ResetPasswordDialog(onDismiss = { showResetPassword = false })
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = Strings.showPassword
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun tryBiometricLogin(
    activity: FragmentActivity,
    profile: BiometricProfile,
    viewModel: SecretaryViewModel,
    settingsManager: SettingsManager,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError(Strings.biometricUnavailable)
        return
    }
    if (profile.email.isBlank() || profile.password.isBlank()) {
        onError(Strings.loginFirstWithPassword)
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Log.d("LoginScreen", "Biometric auth succeeded, logging in as ${profile.email}")
            settingsManager.setLastBiometricEmail(profile.email)
            viewModel.login(profile.email, profile.password, true) { msg ->
                if (msg != null) onError(msg)
            }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Log.w("LoginScreen", "Biometric error $errorCode: $errString") // A14
            when (errorCode) {
                BiometricPrompt.ERROR_USER_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> { /* user dismissed */ }
                BiometricPrompt.ERROR_LOCKOUT,
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                    // A14: biometric locked out – fall back to password automatically
                    onError(Strings.biometricLockedOut)
                }
                else -> onError(Strings.biometricError(errString.toString()))
            }
        }

        override fun onAuthenticationFailed() {
            Log.d("LoginScreen", "Biometric attempt failed (wrong finger/face)") // A14
        }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(profile.displayName.ifBlank { VersionInfo.APP_NAME })
        .setSubtitle(Strings.signInWithFingerprint)
        .setNegativeButtonText(Strings.loginWithPassword)
        .build()

    prompt.authenticate(promptInfo)
}

@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onTokenReceived: () -> Unit
) {
    val context = LocalContext.current
    val api = remember {
        val settingsManager = SettingsManager(context)
        val url = settingsManager.apiUrl.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        retrofit2.Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(SecretaryApi::class.java)
    }
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forgot password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your email address and we will send a reset link.", fontSize = 13.sp)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; message = null },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !loading
                )
                if (message != null) {
                    Text(message!!, fontSize = 12.sp,
                        color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF388E3C))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && email.isNotBlank(),
                onClick = {
                    loading = true; isError = false; message = null
                    scope.launch {
                        try {
                            val res = api.requestPasswordReset(PasswordResetRequestBody(email.trim()))
                            if (res.isSuccessful) {
                                message = res.body()?.message ?: "Reset link sent."
                                // If DEV token in message, open reset screen
                                if (message?.contains("DEV MODE") == true) onTokenReceived()
                            } else {
                                isError = true; message = "Request failed (${res.code()})."
                            }
                        } catch (e: Exception) {
                            isError = true; message = e.message ?: "Network error."
                        } finally { loading = false }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Send reset link")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ResetPasswordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember {
        val settingsManager = SettingsManager(context)
        val url = settingsManager.apiUrl.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        retrofit2.Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(SecretaryApi::class.java)
    }
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            if (done) {
                Text("Password reset successfully. You can now log in.", color = Color(0xFF388E3C))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste the reset token from your email.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = token, onValueChange = { token = it; message = null },
                        label = { Text("Reset token") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !loading
                    )
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it; message = null },
                        label = { Text("New password (min 12 chars)") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !loading,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it; message = null },
                        label = { Text("Confirm new password") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !loading,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    if (message != null) {
                        Text(message!!, fontSize = 12.sp,
                            color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF388E3C))
                    }
                }
            }
        },
        confirmButton = {
            if (done) {
                Button(onClick = onDismiss) { Text("Close") }
            } else {
                Button(
                    enabled = !loading && token.isNotBlank() && newPassword.isNotBlank(),
                    onClick = {
                        when {
                            newPassword.length < 12 -> { isError = true; message = "Password must be at least 12 characters." }
                            newPassword != confirmPassword -> { isError = true; message = "Passwords do not match." }
                            else -> {
                                loading = true; isError = false; message = null
                                scope.launch {
                                    try {
                                        val res = api.confirmPasswordReset(
                                            PasswordResetConfirmBody(token = token.trim(), new_password = newPassword)
                                        )
                                        if (res.isSuccessful) { done = true }
                                        else { isError = true; message = "Reset failed (${res.code()})." }
                                    } catch (e: Exception) {
                                        isError = true; message = e.message ?: "Network error."
                                    } finally { loading = false }
                                }
                            }
                        }
                    }
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Reset password")
                }
            }
        },
        dismissButton = { if (!done) TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
