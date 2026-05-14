package com.example.secretary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.secretary.ui.theme.SecretaryTheme
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * UninstallActivity — launched from Settings when the user taps "Uninstall / Remove app".
 *
 * Flow:
 * 1. Explain what will be deleted.
 * 2. Offer backup options (local / server / both) — based on the user's role.
 * 3. If the user accepts, create the backup, then open the system uninstall dialog.
 * 4. If the user skips backup, go straight to system uninstall.
 *
 * The activity is standalone so it can be launched even outside MainActivity.
 */
class UninstallActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)

        val baseUrl = settingsManager.apiUrl.let {
            if (it.endsWith("/")) it else "$it/"
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(SecretaryApi::class.java)

        backupManager = BackupManager(this, api, settingsManager)

        setContent {
            SecretaryTheme {
                UninstallScreen(
                    settingsManager = settingsManager,
                    backupManager = backupManager,
                    onFinish = {
                        // Open system uninstall dialog for this app
                        val uri = Uri.fromParts("package", packageName, null)
                        val intent = Intent(Intent.ACTION_DELETE, uri)
                        startActivity(intent)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallScreen(
    settingsManager: SettingsManager,
    backupManager: BackupManager,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<BackupResult?>(null) }
    var storageChoice by remember { mutableStateOf("both") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Determine user role from token (best-effort; fall back to "unknown")
    val userRole = remember {
        settingsManager.currentBackendUserRole.ifBlank { "unknown" }
    }
    val isAdmin = userRole in listOf("owner", "admin")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uninstall Secretary") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "This will remove the app",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "All local data will be deleted. Your server data " +
                                    "(if connected) remains intact.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Backup section
            if (backupResult == null) {
                Text(
                    "Create a backup before uninstalling",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    if (isAdmin)
                        "As an admin, your backup will include all user credentials, " +
                                "biometric hashes, and the database connection reference."
                    else
                        "Your backup will include your own credentials and biometric hashes. " +
                                "Only an admin can include the full database reference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Storage location radio group
                Text("Save backup to:", fontWeight = FontWeight.Medium)

                listOf(
                    "both" to "Local device + Server (recommended)",
                    "local" to "Local device only",
                    "server" to "Server only",
                ).forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = storageChoice == value,
                            onClick = { storageChoice = value }
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Create backup button
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = backupManager.createBackup(storageChoice)
                            if (result.success) {
                                backupResult = result
                            } else {
                                errorMessage = result.errorMessage ?: "Backup failed"
                            }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.SaveAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLoading) "Creating backup…" else "Create backup & continue")
                }

                // Skip backup — go straight to uninstall
                TextButton(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Skip backup and uninstall",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Backup success panel
            backupResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Backup created",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Scope: ${result.backupScope ?: "personal"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        result.localFilePath?.let {
                            Text(
                                "Local: install_data/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        result.restoreToken?.let {
                            Text(
                                "Server restore token saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (result.includesDbReference) {
                            Text(
                                "✓ Includes database connection reference",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Proceed with uninstall
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uninstall app")
                }
            }

            // Cancel button
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel — keep the app")
            }
        }
    }

    // Confirm skipping backup
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Skip backup?") },
            text = {
                Text(
                    "Without a backup you will not be able to restore your credentials " +
                            "or biometric access after reinstalling. Continue without backup?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onFinish()
                }) {
                    Text("Uninstall anyway", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
