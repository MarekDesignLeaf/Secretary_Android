package com.example.secretary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

// ─────────────────────────────────────────────────────────────────────────────
// Data helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Map<String, Any?>.str(key: String) = this[key]?.toString() ?: ""
private fun Map<String, Any?>.int(key: String) = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.bool(key: String) = this[key] as? Boolean ?: false

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

// ─────────────────────────────────────────────────────────────────────────────
// Top-level screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPackagesScreen(viewModel: SecretaryViewModel, onBack: () -> Unit) {
    val sm = viewModel.getSettingsManager() ?: run { onBack(); return }
    val auth = "Bearer ${sm.accessToken ?: ""}"
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // ── state ──
    var tools by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showInstallSheet by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<Map<String, Any?>?>(null) }

    fun reload() {
        scope.launch {
            loading = true; errorMsg = null
            try {
                val resp = viewModel.api.listToolPackages(auth)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: emptyMap()
                    tools = body.list("tools")
                } else {
                    errorMsg = "HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // ── if a tool is selected, show detail ──
    val sel = selectedTool
    if (sel != null) {
        ToolDetailScreen(
            tool = sel,
            auth = auth,
            viewModel = viewModel,
            onBack = {
                selectedTool = null
                reload()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.t("Tool packages", "Balíčky nástrojů", "Pakiety narzędzi"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = { reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = Strings.t("Refresh", "Obnovit", "Odśwież"))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInstallSheet = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(Strings.t("Install", "Instalovat", "Zainstaluj")) }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                errorMsg != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Error, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { reload() }) { Text(Strings.t("Retry", "Zkusit znovu", "Ponów")) }
                }
                tools.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Extension, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        Strings.t("No tools installed", "Žádné nástroje nejsou nainstalovány", "Brak zainstalowanych narzędzi"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Strings.t("Tap + to install a tool package (.zip)", "Klepnutím na + nainstalujte balíček (.zip)", "Dotknij + aby zainstalować pakiet (.zip)"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tools) { tool ->
                        ToolListCard(tool = tool, onClick = { selectedTool = tool })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showInstallSheet) {
        InstallToolSheet(
            auth = auth,
            viewModel = viewModel,
            onDismiss = { showInstallSheet = false },
            onInstalled = { showInstallSheet = false; reload() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool list card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolListCard(tool: Map<String, Any?>, onClick: () -> Unit) {
    val status = tool.str("install_status")
    val riskLevel = tool.str("risk_level")
    val required = tool.int("required_slot_count")
    val filled = tool.int("secret_slots_filled")
    val missingSlots = required > filled

    val statusColor = when (status) {
        "enabled"  -> MaterialTheme.colorScheme.tertiary
        "disabled" -> MaterialTheme.colorScheme.outline
        "broken"   -> MaterialTheme.colorScheme.error
        else       -> MaterialTheme.colorScheme.outline
    }
    val riskColor = when (riskLevel) {
        "high"   -> MaterialTheme.colorScheme.error
        "medium" -> Color(0xFFF57C00)
        else     -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Extension, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tool.str("tool_name"), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                SuggestionChip(
                    onClick = {},
                    label = { Text(status, fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(labelColor = statusColor),
                    modifier = Modifier.height(24.dp)
                )
            }
            if (tool.str("description").isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(tool.str("description"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("v${tool.str("version")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                if (riskLevel.isNotBlank()) {
                    Text("•", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        Strings.t("risk: $riskLevel", "riziko: $riskLevel", "ryzyko: $riskLevel"),
                        fontSize = 12.sp, color = riskColor
                    )
                }
                if (missingSlots) {
                    Text("•", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        Strings.t("$filled/$required slots", "$filled/$required slotů", "$filled/$required slotów"),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool detail + slot config screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolDetailScreen(
    tool: Map<String, Any?>,
    auth: String,
    viewModel: SecretaryViewModel,
    onBack: () -> Unit,
) {
    val toolId = tool.str("tool_id")
    val scope = rememberCoroutineScope()

    var slots by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var slotsLoading by remember { mutableStateOf(true) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testRunning by remember { mutableStateOf(false) }
    var savingSlot by remember { mutableStateOf<String?>(null) }
    var slotValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var uninstalling by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toolId) {
        slotsLoading = true
        try {
            val resp = viewModel.api.getToolSlots(auth, toolId)
            if (resp.isSuccessful) {
                slots = resp.body()?.list("slots") ?: emptyList()
                slotValues = slots.associate { it.str("slot_name") to "" }
            }
        } catch (_: Exception) {}
        finally { slotsLoading = false }
    }

    fun saveSlot(slotName: String, value: String) {
        if (value.isBlank()) return
        scope.launch {
            savingSlot = slotName
            statusMsg = null
            try {
                val resp = viewModel.api.updateToolConfigSlot(
                    auth, toolId, slotName,
                    mapOf("value" to value, "tenant_id" to 1, "run_test" to false)
                )
                statusMsg = if (resp.isSuccessful)
                    Strings.t("Slot '$slotName' saved.", "Slot '$slotName' uložen.", "Slot '$slotName' zapisany.")
                else
                    "Error ${resp.code()}: ${resp.errorBody()?.string()}"
            } catch (e: Exception) {
                statusMsg = e.message
            } finally {
                savingSlot = null
            }
        }
    }

    fun runTest() {
        scope.launch {
            testRunning = true; testResult = null; statusMsg = null
            try {
                val resp = viewModel.api.testToolConnection(auth, toolId, mapOf("tenant_id" to 1))
                val body = resp.body()
                testResult = if (resp.isSuccessful) {
                    val passed = body?.int("tests_passed") ?: 0
                    val failed = body?.int("tests_failed") ?: 0
                    Strings.t("Tests: $passed passed, $failed failed", "Testy: $passed prošlo, $failed selhalo", "Testy: $passed zaliczono, $failed nie zdało")
                } else {
                    "Error ${resp.code()}"
                }
            } catch (e: Exception) {
                testResult = e.message
            } finally {
                testRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.str("tool_name"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(Strings.t("ID", "ID", "ID"), toolId)
                    InfoRow(Strings.t("Version", "Verze", "Wersja"), tool.str("version"))
                    InfoRow(Strings.t("Author", "Autor", "Autor"), tool.str("author"))
                    InfoRow(Strings.t("Status", "Status", "Status"), tool.str("install_status"))
                    InfoRow(Strings.t("Risk", "Riziko", "Ryzyko"), tool.str("risk_level"))
                    if (tool.str("description").isNotBlank()) {
                        InfoRow(Strings.t("Description", "Popis", "Opis"), tool.str("description"))
                    }
                }
            }

            // Slots section
            if (slotsLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (slots.isNotEmpty()) {
                Text(
                    Strings.t("Configuration slots", "Konfigurační sloty", "Sloty konfiguracyjne"),
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
                slots.forEach { slot ->
                    val name = slot.str("slot_name")
                    val isFilled = slot.bool("is_filled")
                    val isSecret = slot.bool("is_secret")
                    val required = slot.bool("required")
                    var showValue by remember { mutableStateOf(false) }
                    val value = slotValues[name] ?: ""

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (required && !isFilled)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(slot.str("display_name").ifBlank { name }, fontWeight = FontWeight.Medium)
                                        if (required) {
                                            Text("*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (slot.str("description").isNotBlank()) {
                                        Text(slot.str("description"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(
                                    if (isFilled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = if (isFilled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { slotValues = slotValues + (name to it) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(if (isFilled) "••••••••" else slot.str("default_value"), fontSize = 13.sp) },
                                    singleLine = true,
                                    visualTransformation = if (isSecret && !showValue) PasswordVisualTransformation() else VisualTransformation.None,
                                    trailingIcon = if (isSecret) ({
                                        IconButton(onClick = { showValue = !showValue }) {
                                            Icon(
                                                if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                null, Modifier.size(18.dp)
                                            )
                                        }
                                    }) else null,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                                )
                                Button(
                                    onClick = { saveSlot(name, value) },
                                    enabled = value.isNotBlank() && savingSlot != name,
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    if (savingSlot == name) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(Strings.t("Save", "Uložit", "Zapisz"), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status message
            AnimatedVisibility(visible = statusMsg != null) {
                Text(statusMsg ?: "", color = if (statusMsg?.startsWith("Error") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, fontSize = 13.sp)
            }

            // Action buttons
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { runTest() },
                    enabled = !testRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (testRunning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(Strings.t("Test", "Testovat", "Testuj"), fontSize = 13.sp)
                }
                Button(
                    onClick = { showUninstallDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Strings.t("Uninstall", "Odinstalovat", "Odinstaluj"), fontSize = 13.sp)
                }
            }

            // Test result
            AnimatedVisibility(visible = testResult != null) {
                Card(Modifier.fillMaxWidth()) {
                    Text(testResult ?: "", Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }
    }

    // Uninstall confirm dialog
    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { if (!uninstalling) showUninstallDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(Strings.t("Uninstall tool?", "Odinstalovat nástroj?", "Odinstalować narzędzie?")) },
            text = {
                Text(
                    Strings.t(
                        "This will remove '${tool.str("tool_name")}' and all its configuration. This cannot be undone.",
                        "Tím odstraníte '${tool.str("tool_name")}' a veškerou jeho konfiguraci. Tuto akci nelze vrátit.",
                        "To usunie '${tool.str("tool_name")}' i całą jego konfigurację. Tej operacji nie można cofnąć."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            uninstalling = true
                            try {
                                val phrase = "Potvrzuji odinstalaci nastroje: $toolId"
                                val resp = viewModel.api.uninstallTool(
                                    auth, toolId,
                                    mapOf("tenant_id" to 1, "confirmation" to phrase, "purge_secrets" to false)
                                )
                                if (resp.isSuccessful) {
                                    showUninstallDialog = false
                                    onBack()
                                } else {
                                    statusMsg = "Error ${resp.code()}: ${resp.errorBody()?.string()}"
                                    showUninstallDialog = false
                                }
                            } catch (e: Exception) {
                                statusMsg = e.message
                                showUninstallDialog = false
                            } finally {
                                uninstalling = false
                            }
                        }
                    },
                    enabled = !uninstalling,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (uninstalling) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(Strings.t("Uninstall", "Odinstalovat", "Odinstaluj"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }, enabled = !uninstalling) {
                    Text(Strings.t("Cancel", "Zrušit", "Anuluj"))
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Install tool bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallToolSheet(
    auth: String,
    viewModel: SecretaryViewModel,
    onDismiss: () -> Unit,
    onInstalled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var fileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        fileUri = uri
        fileName = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else "package.zip"
        } ?: "package.zip"
    }

    ModalBottomSheet(onDismissRequest = { if (!installing) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                Strings.t("Install tool package", "Instalovat balíček nástrojů", "Zainstaluj pakiet narzędzi"),
                fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
            Text(
                Strings.t(
                    "Select a .zip file containing a tool package manifest.",
                    "Vyberte soubor .zip s manifestem balíčku nástrojů.",
                    "Wybierz plik .zip z manifestem pakietu narzędzi."
                ),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !installing
            ) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(fileName.ifBlank { Strings.t("Select .zip file", "Vybrat soubor .zip", "Wybierz plik .zip") })
            }

            if (resultMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(resultMsg ?: "", Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            Button(
                onClick = {
                    val uri = fileUri ?: return@Button
                    scope.launch {
                        installing = true; resultMsg = null; isError = false
                        try {
                            val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                                ?: throw Exception("Cannot read file")
                            val mediaType = "application/zip".toMediaTypeOrNull()
                            val requestFile = bytes.toRequestBody(mediaType)
                            val part = MultipartBody.Part.createFormData("file", fileName, requestFile)
                            val tenantPart = "1".toRequestBody("text/plain".toMediaTypeOrNull())
                            val skipTestPart = "false".toRequestBody("text/plain".toMediaTypeOrNull())
                            val resp = viewModel.api.installToolPackage(
                                auth, part, tenantPart, null, skipTestPart
                            )
                            if (resp.isSuccessful) {
                                val body = resp.body()
                                resultMsg = Strings.t(
                                    "Installed: ${body?.get("tool_name") ?: "OK"}",
                                    "Instalováno: ${body?.get("tool_name") ?: "OK"}",
                                    "Zainstalowano: ${body?.get("tool_name") ?: "OK"}"
                                )
                                isError = false
                                onInstalled()
                            } else {
                                resultMsg = "Error ${resp.code()}: ${resp.errorBody()?.string()}"
                                isError = true
                            }
                        } catch (e: Exception) {
                            resultMsg = e.message ?: "Unknown error"
                            isError = true
                        } finally {
                            installing = false
                        }
                    }
                },
                enabled = fileUri != null && !installing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (installing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.t("Installing…", "Instaluji…", "Instaluję…"))
                } else {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.t("Install", "Instalovat", "Zainstaluj"))
                }
            }
        }
    }
}
