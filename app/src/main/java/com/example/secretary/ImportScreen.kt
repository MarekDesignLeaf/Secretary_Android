package com.example.secretary

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

// ──────────────────────────────────────────────────────────────────────��──────
// Data helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Map<String, Any?>.str(key: String) = this[key]?.toString() ?: ""
private fun Map<String, Any?>.int(key: String) = (this[key] as? Number)?.toInt() ?: 0

private val TARGET_TABLES = listOf(
    "crm.contacts",
    "crm.clients",
)

private val SOURCE_TYPES = listOf(
    "csv" to "CSV (.csv)",
    "excel" to "Excel (.xlsx)",
    "json" to "JSON (.json)",
)

private val CONTACT_FIELDS = listOf(
    "first_name", "last_name", "email", "phone", "notes"
)
private val CLIENT_FIELDS = listOf(
    "name", "email", "phone", "address", "notes"
)

private fun targetFields(table: String): List<String> = when (table) {
    "crm.contacts" -> CONTACT_FIELDS
    "crm.clients"  -> CLIENT_FIELDS
    else           -> emptyList()
}

// ─────────────────────────────────────────────────────────────────────────────
// ImportScreen — top-level composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(viewModel: SecretaryViewModel, onBack: () -> Unit) {
    val sm     = viewModel.getSettingsManager() ?: run { onBack(); return }
    val token  = sm.accessToken?.let { "Bearer $it" } ?: run { onBack(); return }
    val api    = viewModel.api
    val scope  = rememberCoroutineScope()
    val ctx    = LocalContext.current

    // Wizard step: setup | uploading | mapping | validating | preview | applying | done | error
    var step           by remember { mutableStateOf("setup") }
    var sessionId      by remember { mutableStateOf<String?>(null) }
    var sessionData    by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }
    var busy           by remember { mutableStateOf(false) }

    // Setup state
    var sourceType     by remember { mutableStateOf("csv") }
    var targetTable    by remember { mutableStateOf("crm.contacts") }
    var sessionName    by remember { mutableStateOf("") }
    var pickedUri      by remember { mutableStateOf<Uri?>(null) }
    var pickedName     by remember { mutableStateOf<String?>(null) }

    // Mapping state: source_column → target_field (null = skip)
    var sourceColumns  by remember { mutableStateOf<List<String>>(emptyList()) }
    var mappings       by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    // Preview state
    var previewRows    by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var previewTotal   by remember { mutableStateOf(0) }
    var validRows      by remember { mutableStateOf(0) }
    var invalidRows    by remember { mutableStateOf(0) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri  = uri
            pickedName = resolveFileName(ctx, uri)
        }
    }

    // Helper: refresh session from backend
    suspend fun refreshSession(id: String) {
        val res = api.getImportSession(token, id)
        if (res.isSuccessful) {
            sessionData = res.body() ?: emptyMap()
            validRows   = sessionData.int("valid_rows")
            invalidRows = sessionData.int("invalid_rows")
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import dat", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (step) {

                // ── STEP 1: Setup ──────────────────────────────────────────
                "setup" -> ImportSetupStep(
                    sourceType    = sourceType,
                    targetTable   = targetTable,
                    sessionName   = sessionName,
                    pickedName    = pickedName,
                    busy          = busy,
                    errorMsg      = errorMsg,
                    onSourceType  = { sourceType = it },
                    onTargetTable = { targetTable = it },
                    onSessionName = { sessionName = it },
                    onPickFile    = {
                        val mimes = when (sourceType) {
                            "excel" -> arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            )
                            "json"  -> arrayOf("application/json", "text/plain")
                            else    -> arrayOf("text/csv", "text/plain", "*/*")
                        }
                        fileLauncher.launch(mimes)
                    },
                    onNext = {
                        val uri = pickedUri ?: run {
                            errorMsg = "Vyber soubor pro import."; return@ImportSetupStep
                        }
                        errorMsg = null
                        busy = true
                        scope.launch {
                            try {
                                // 1. Create session
                                val createRes = api.createImportSession(
                                    token,
                                    body = mapOf(
                                        "source_type"   to sourceType,
                                        "target_table"  to targetTable,
                                        "session_name"  to sessionName.ifBlank { pickedName ?: "Import" },
                                        "import_mode"   to "semi_automatic",
                                    )
                                )
                                if (!createRes.isSuccessful) {
                                    errorMsg = "Chyba při vytváření session: ${createRes.code()}"
                                    busy = false; return@launch
                                }
                                val sid = createRes.body()?.str("id") ?: run {
                                    errorMsg = "Server nevrátil session ID"; busy = false; return@launch
                                }
                                sessionId = sid

                                // 2. Upload file
                                val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: run {
                                    errorMsg = "Nepodařilo se přečíst soubor"; busy = false; return@launch
                                }
                                val mediaType = when (sourceType) {
                                    "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    "json"  -> "application/json"
                                    else    -> "text/csv"
                                }
                                val reqBody  = bytes.toRequestBody(mediaType.toMediaType())
                                val part     = MultipartBody.Part.createFormData(
                                    "file", pickedName ?: "upload", reqBody
                                )
                                step = "uploading"
                                val uploadRes = api.uploadImportFile(token, sid, file = part)
                                if (!uploadRes.isSuccessful) {
                                    errorMsg = "Chyba při nahrávání souboru: ${uploadRes.code()}"
                                    step = "setup"; busy = false; return@launch
                                }
                                refreshSession(sid)

                                // 3. Detect columns from first preview row (raw_json keys)
                                val prevRes = api.getImportPreview(token, sid, page = 1, pageSize = 5)
                                val rows = prevRes.body()?.get("rows") as? List<*>
                                val firstRow = (rows?.firstOrNull() as? Map<*, *>)
                                val rawJson  = firstRow?.get("raw_json") as? Map<*, *>
                                sourceColumns = rawJson?.keys?.map { it.toString() } ?: emptyList()

                                // Auto-map columns that match target field names
                                val fields = targetFields(targetTable)
                                mappings = sourceColumns.associateWith { col ->
                                    fields.firstOrNull { it.equals(col, ignoreCase = true) }
                                }

                                step = "mapping"
                            } catch (e: Exception) {
                                errorMsg = "Chyba: ${e.message}"
                                step = "setup"
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                "uploading" -> ImportBusyStep("Nahrávám a parsuju soubor…")

                // ── STEP 2: Mapping ────────────────────────────────────────
                "mapping" -> ImportMappingStep(
                    sourceColumns  = sourceColumns,
                    targetTable    = targetTable,
                    mappings       = mappings,
                    busy           = busy,
                    errorMsg       = errorMsg,
                    onMappingChange = { col, field -> mappings = mappings + (col to field) },
                    onNext = {
                        val sid = sessionId ?: return@ImportMappingStep
                        errorMsg = null; busy = true
                        scope.launch {
                            try {
                                val mappingList = mappings.map { (src, tgt) ->
                                    mapOf(
                                        "source_column"  to src,
                                        "target_field"   to tgt,
                                        "transform_type" to "trim",
                                        "required"       to false,
                                        "sort_order"     to sourceColumns.indexOf(src)
                                    )
                                }
                                val saveRes = api.saveImportMappings(
                                    token, sid,
                                    body = mapOf("mappings" to mappingList)
                                )
                                if (!saveRes.isSuccessful) {
                                    errorMsg = "Chyba při ukládání mapování: ${saveRes.code()}"
                                    busy = false; return@launch
                                }
                                step = "validating"
                                val valRes = api.validateImport(token, sid)
                                if (!valRes.isSuccessful) {
                                    errorMsg = "Chyba při validaci: ${valRes.code()}"
                                    step = "mapping"; busy = false; return@launch
                                }
                                refreshSession(sid)

                                // Load preview rows
                                val prevRes = api.getImportPreview(token, sid, page = 1, pageSize = 30)
                                val body = prevRes.body()
                                previewTotal = (body?.get("total") as? Number)?.toInt() ?: 0
                                @Suppress("UNCHECKED_CAST")
                                previewRows = (body?.get("rows") as? List<Map<String, Any?>>) ?: emptyList()

                                step = "preview"
                            } catch (e: Exception) {
                                errorMsg = "Chyba: ${e.message}"
                                step = "mapping"
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                "validating" -> ImportBusyStep("Validuji řádky…")

                // ── STEP 3: Preview ────────────────────────────────────────
                "preview" -> ImportPreviewStep(
                    previewRows  = previewRows,
                    previewTotal = previewTotal,
                    validRows    = validRows,
                    invalidRows  = invalidRows,
                    targetTable  = targetTable,
                    busy         = busy,
                    errorMsg     = errorMsg,
                    onBack       = { step = "mapping" },
                    onApprove = {
                        val sid = sessionId ?: return@ImportPreviewStep
                        errorMsg = null; busy = true
                        scope.launch {
                            try {
                                val confirmPhrase = "Potvrzuji import dat do $targetTable"
                                val approveRes = api.approveImport(
                                    token, sid,
                                    body = mapOf("confirmation" to confirmPhrase)
                                )
                                if (!approveRes.isSuccessful) {
                                    errorMsg = "Chyba při schválení: ${approveRes.code()} — ${approveRes.errorBody()?.string()}"
                                    busy = false; return@launch
                                }
                                step = "applying"
                                val applyRes = api.applyImport(token, sid, body = emptyMap())
                                if (!applyRes.isSuccessful) {
                                    errorMsg = "Chyba při aplikaci: ${applyRes.code()}"
                                    step = "preview"; busy = false; return@launch
                                }
                                refreshSession(sid)
                                step = "done"
                            } catch (e: Exception) {
                                errorMsg = "Chyba: ${e.message}"
                                step = "preview"
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                "applying" -> ImportBusyStep("Importuji data do databáze…")

                // ── STEP 4: Done ───────────────────────────────────────────
                "done" -> ImportDoneStep(
                    sessionData = sessionData,
                    onBack      = onBack,
                    onRollback  = {
                        val sid = sessionId ?: return@ImportDoneStep
                        busy = true; errorMsg = null
                        scope.launch {
                            try {
                                api.rollbackImport(token, sid, body = emptyMap())
                                refreshSession(sid)
                                step = "done"
                            } catch (e: Exception) {
                                errorMsg = "Chyba při rollbacku: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                "error" -> ImportErrorStep(errorMsg ?: "Neznámá chyba", onBack = onBack)
            }

            // Overlay busy indicator
            if (busy && step !in listOf("uploading", "validating", "applying")) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step composables
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSetupStep(
    sourceType: String, targetTable: String, sessionName: String,
    pickedName: String?, busy: Boolean, errorMsg: String?,
    onSourceType: (String) -> Unit, onTargetTable: (String) -> Unit,
    onSessionName: (String) -> Unit, onPickFile: () -> Unit, onNext: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ImportStepHeader(1, 4, "Nastavení importu") }

        item {
            OutlinedTextField(
                value     = sessionName,
                onValueChange = onSessionName,
                label     = { Text("Název importu (volitelný)") },
                modifier  = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Text("Typ souboru", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOURCE_TYPES.forEach { (type, label) ->
                    FilterChip(
                        selected = sourceType == type,
                        onClick  = { onSourceType(type) },
                        label    = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            Text("Cílová tabulka", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TARGET_TABLES.forEach { table ->
                    FilterChip(
                        selected = targetTable == table,
                        onClick  = { onTargetTable(table) },
                        label    = { Text(table.removePrefix("crm."), fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            OutlinedCard(Modifier.fillMaxWidth().clickable { onPickFile() }) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            pickedName ?: "Vybrat soubor…",
                            fontWeight = if (pickedName != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (pickedName != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pickedName == null) {
                            Text("Klepněte pro výběr",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (errorMsg != null) {
            item {
                Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }

        item {
            Button(
                onClick  = onNext,
                enabled  = !busy && pickedName != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Nahrát a pokračovat →")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportMappingStep(
    sourceColumns: List<String>, targetTable: String,
    mappings: Map<String, String?>, busy: Boolean, errorMsg: String?,
    onMappingChange: (String, String?) -> Unit, onNext: () -> Unit
) {
    val fields = listOf("(přeskočit)") + targetFields(targetTable)

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ImportStepHeader(2, 4, "Mapování sloupců → ${targetTable.removePrefix("crm.")}") }

        item {
            Text(
                "Přiřaď zdrojové sloupce k polím záznamu. " +
                "Sloupce označené '(přeskočit)' nebudou importovány.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        itemsIndexed(sourceColumns) { _, col ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(col, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                var expanded by remember { mutableStateOf(false) }
                val selected = mappings[col]
                Box(Modifier.weight(1.2f)) {
                    OutlinedCard(
                        Modifier.fillMaxWidth().clickable { expanded = true }
                    ) {
                        Text(
                            selected ?: "(přeskočit)",
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontSize = 13.sp,
                            color = if (selected != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        fields.forEach { field ->
                            DropdownMenuItem(
                                text  = { Text(field, fontSize = 13.sp) },
                                onClick = {
                                    onMappingChange(col, if (field == "(přeskočit)") null else field)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (errorMsg != null) {
            item { Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }

        item {
            Button(
                onClick  = onNext,
                enabled  = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Validovat a zobrazit náhled →")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewStep(
    previewRows: List<Map<String, Any?>>, previewTotal: Int,
    validRows: Int, invalidRows: Int, targetTable: String,
    busy: Boolean, errorMsg: String?,
    onBack: () -> Unit, onApprove: () -> Unit
) {
    val fields = targetFields(targetTable)

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ImportStepHeader(3, 4, "Náhled importu") }

        item {
            // Summary chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label  = { Text("Celkem: $previewTotal", fontSize = 12.sp) }
                )
                SuggestionChip(
                    onClick = {},
                    label  = {
                        Text("✓ $validRows",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                )
                if (invalidRows > 0) {
                    SuggestionChip(
                        onClick = {},
                        label  = {
                            Text("✗ $invalidRows",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }

        if (invalidRows > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "$invalidRows řádků je neplatných a nebude importováno. " +
                        "Vrať se zpět a uprav mapování, nebo pokračuj bez nich.",
                        Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Table header
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("#", Modifier.width(32.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Status", Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                fields.forEach { f ->
                    Text(f, Modifier.width(100.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Rows
        itemsIndexed(previewRows) { idx, row ->
            val status  = row.str("validation_status")
            val mapped  = row["mapped_json"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val errors  = (row["validation_errors"] as? List<*>)?.isNotEmpty() ?: false
            val rowBg   = when {
                status == "invalid" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                status == "valid"   -> Color.Transparent
                else                -> Color.Transparent
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${row.int("row_index") + 1}", Modifier.width(32.dp), fontSize = 11.sp)
                Box(Modifier.width(56.dp)) {
                    Text(
                        if (status == "valid") "✓" else if (status == "invalid") "✗" else "…",
                        fontSize = 13.sp,
                        color = when (status) {
                            "valid"   -> MaterialTheme.colorScheme.primary
                            "invalid" -> MaterialTheme.colorScheme.error
                            else      -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                fields.forEach { f ->
                    Text(
                        mapped[f]?.toString() ?: "",
                        Modifier.width(100.dp),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (idx < previewRows.lastIndex) HorizontalDivider(thickness = 0.5.dp)
        }

        if (previewRows.size < previewTotal) {
            item {
                Text(
                    "Zobrazeno ${previewRows.size} z $previewTotal řádků.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (errorMsg != null) {
            item { Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("← Zpět")
                }
                Button(
                    onClick  = onApprove,
                    enabled  = !busy && validRows > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Importovat $validRows záznamů")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportDoneStep(
    sessionData: Map<String, Any?>,
    onBack: () -> Unit,
    onRollback: () -> Unit
) {
    val status       = sessionData.str("status")
    val imported     = sessionData.int("imported_rows")
    val rolledBack   = sessionData.int("rolled_back_rows")
    val isRolledBack = status == "rolled_back"
    val hasError     = sessionData.str("error_message").isNotBlank()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    if (isRolledBack) Icons.AutoMirrored.Filled.Undo
                    else if (hasError) Icons.Default.Warning
                    else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = when {
                        isRolledBack -> MaterialTheme.colorScheme.onSurfaceVariant
                        hasError     -> MaterialTheme.colorScheme.error
                        else         -> MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    when {
                        isRolledBack -> "Import vrácen zpět"
                        hasError     -> "Import dokončen s chybami"
                        else         -> "Import úspěšný"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isRolledBack)
                        "Bylo vráceno $rolledBack záznamů."
                    else
                        "Importováno: $imported záznamů",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasError) {
                    Text(
                        sessionData.str("error_message"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (!isRolledBack && imported > 0) {
            item {
                OutlinedButton(
                    onClick  = onRollback,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Vrátit import zpět (rollback)")
                }
            }
        }

        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Hotovo")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportBusyStep(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportErrorStep(message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Error, null, Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error)
        Text("Chyba importu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Button(onClick = onBack) { Text("Zpět") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportStepHeader(current: Int, total: Int, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { current.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Krok $current z $total",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun resolveFileName(ctx: Context, uri: Uri): String {
    return try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (idx >= 0) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "soubor"
    } catch (e: Exception) {
        uri.lastPathSegment ?: "soubor"
    }
}
