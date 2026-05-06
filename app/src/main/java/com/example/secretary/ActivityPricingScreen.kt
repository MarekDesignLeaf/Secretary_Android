package com.example.secretary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// ─── Data helpers ───────────────────────────────────────────────────────────

private fun Map<String, Any?>.str(key: String) = this[key]?.toString() ?: ""
private fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.double(key: String) = (this[key] as? Number)?.toDouble() ?: 0.0

// Pricing method display labels
private val PRICING_LABELS = mapOf(
    "hourly"       to "Hourly (£/hr)",
    "per_m2"       to "Per m²",
    "per_m"        to "Per m (linear)",
    "per_visit"    to "Per visit",
    "per_item"     to "Per item / unit",
    "per_m3"       to "Per m³",
    "per_tonne"    to "Per tonne",
    "per_bulk_bag" to "Per bulk bag",
    "fixed"        to "Fixed price",
    "callout"      to "Callout fee",
    "subscription" to "Subscription",
    "per_day"      to "Per day",
    "per_half_day" to "Per half day",
    "per_tree"     to "Per tree",
    "per_panel"    to "Per panel",
    "per_post"     to "Per post",
    "per_roll"     to "Per roll"
)

private val ALL_PRICING_METHODS = PRICING_LABELS.keys.toList()

// Supplementary item keys and their display labels
private val SUPPLEMENTARY_ITEMS = listOf(
    "material"        to "Material cost",
    "waste_disposal"  to "Waste disposal",
    "travel"          to "Travel charge",
    "minimum_charge"  to "Minimum charge",
    "fuel_surcharge"  to "Fuel surcharge",
    "permit"          to "Permit / licence",
    "risk_premium"    to "Risk premium",
    "overtime"        to "Overtime rate",
    "weekend_rate"    to "Weekend rate",
    "emergency_surcharge" to "Emergency surcharge",
    "equipment_hire"  to "Equipment hire",
    "disposal_levy"   to "Disposal levy"
)

// ─── Top-level screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityPricingScreen(viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()

    // Navigation state: null = group list, groupId set = subtype list, both set = activity list
    var selectedGroup by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var selectedSubtype by remember { mutableStateOf<Map<String, Any?>?>(null) }

    // Industry groups (reuse existing onboarding endpoint)
    var groups by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var groupsLoading by remember { mutableStateOf(true) }
    var subtypes by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var subtypesLoading by remember { mutableStateOf(false) }

    // Editing state
    var editingTemplate by remember { mutableStateOf<Map<String, Any?>?>(null) }

    // Error feedback
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(errorMsg) {
        errorMsg?.let { snackbarHostState.showSnackbar(it); errorMsg = null }
    }

    LaunchedEffect(Unit) {
        try {
            val res = viewModel.getApi().getIndustryGroups()
            if (res.isSuccessful) groups = res.body() ?: emptyList()
            else errorMsg = "Failed to load groups (${res.code()})"
        } catch (e: Exception) { errorMsg = "Network error: ${e.message}" }
        groupsLoading = false
    }

    LaunchedEffect(selectedGroup) {
        val gId = selectedGroup?.long("id") ?: return@LaunchedEffect
        subtypesLoading = true
        subtypes = emptyList()
        try {
            val res = viewModel.getApi().getIndustrySubtypes(gId)
            if (res.isSuccessful) subtypes = res.body() ?: emptyList()
            else errorMsg = "Failed to load subtypes (${res.code()})"
        } catch (e: Exception) { errorMsg = "Network error: ${e.message}" }
        subtypesLoading = false
    }

    LaunchedEffect(selectedSubtype) {
        val code = selectedSubtype?.str("code")
        if (code == null) {
            viewModel.clearActivityTemplates()
        } else {
            viewModel.loadActivityTemplates(subtypeCode = code)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(when {
                        selectedSubtype != null -> selectedSubtype!!.str("name").ifBlank { "Activities" }
                        selectedGroup != null   -> selectedGroup!!.str("name").ifBlank { "Subtypes" }
                        else -> "Activity Pricing"
                    })
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            editingTemplate != null -> editingTemplate = null
                            selectedSubtype != null -> { selectedSubtype = null }
                            selectedGroup != null   -> { selectedGroup = null }
                            else -> navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                editingTemplate != null -> {
                    val tmpl = editingTemplate!!
                    val tenantOverride = state.tenantActivityPricing.firstOrNull {
                        it.long("template_id") == tmpl.long("id")
                    }
                    ActivityEditorPanel(
                        template = tmpl,
                        override = tenantOverride,
                        onSave = { data ->
                            viewModel.upsertActivityPricing(tmpl.long("id"), data) {
                                // Reload with current subtype
                                viewModel.loadActivityTemplates(subtypeCode = selectedSubtype?.str("code"))
                            }
                            editingTemplate = null
                        },
                        onReset = {
                            viewModel.resetActivityPricing(tmpl.long("id")) {
                                viewModel.loadActivityTemplates(subtypeCode = selectedSubtype?.str("code"))
                            }
                            editingTemplate = null
                        },
                        onDismiss = { editingTemplate = null }
                    )
                }
                selectedSubtype != null -> {
                    val templates = state.activityTemplates
                    val overrides = state.tenantActivityPricing
                    if (state.activityTemplatesLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Text(
                                    "${templates.size} activities",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            items(templates) { tmpl ->
                                val override = overrides.firstOrNull { it.long("template_id") == tmpl.long("id") }
                                ActivityRow(
                                    template = tmpl,
                                    override = override,
                                    onClick = { editingTemplate = tmpl }
                                )
                            }
                        }
                    }
                }
                selectedGroup != null -> {
                    if (subtypesLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(subtypes) { subtype ->
                                SubtypeRow(subtype = subtype, onClick = { selectedSubtype = subtype })
                            }
                        }
                    }
                }
                else -> {
                    if (groupsLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(groups) { group ->
                                GroupRow(group = group, onClick = { selectedGroup = group })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Row composables ─────────────────────────────────────────────────────────

@Composable
private fun GroupRow(group: Map<String, Any?>, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(group.str("name"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val code = group.str("code")
                if (code.isNotBlank()) Text(code, fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
private fun SubtypeRow(subtype: Map<String, Any?>, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(subtype.str("name"), fontSize = 14.sp)
                val code = subtype.str("code")
                if (code.isNotBlank()) Text(code, fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
private fun ActivityRow(
    template: Map<String, Any?>,
    override: Map<String, Any?>?,
    onClick: () -> Unit
) {
    val method = override?.str("pricing_method")?.takeIf { it.isNotBlank() }
        ?: template.str("default_pricing_method")
    val methodLabel = PRICING_LABELS[method] ?: method
    val rate = override?.double("rate")?.let { if (it > 0) "£%.2f".format(it) else null }
    val isCustomised = override != null

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCustomised) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(template.str("name"), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCustomised) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            methodLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = if (isCustomised) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    if (rate != null) {
                        Text(rate, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    if (isCustomised) {
                        Text("✎", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Activity editor bottom-sheet style panel ────────────────────────────────

@Composable
private fun ActivityEditorPanel(
    template: Map<String, Any?>,
    override: Map<String, Any?>?,
    onSave: (Map<String, Any?>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val defaultMethod = template.str("default_pricing_method").ifBlank { "hourly" }
    val allowedRaw = (template["allowed_pricing_methods"] as? List<*>)?.mapNotNull { it?.toString() }
    val allowedMethods = if (allowedRaw.isNullOrEmpty()) ALL_PRICING_METHODS else allowedRaw

    var selectedMethod by remember {
        mutableStateOf(override?.str("pricing_method")?.takeIf { it.isNotBlank() } ?: defaultMethod)
    }
    var rate by remember {
        mutableStateOf(override?.double("rate")?.let { if (it > 0) "%.2f".format(it) else "" } ?: "")
    }
    var customName by remember { mutableStateOf(override?.str("custom_name") ?: "") }
    var isActive by remember { mutableStateOf((override?.get("is_active") as? Boolean) ?: true) }
    var notes by remember { mutableStateOf(override?.str("notes") ?: "") }
    @Suppress("UNCHECKED_CAST")
    val voiceAliasesRaw = (override?.get("voice_aliases") as? List<*>)
        ?.joinToString(", ") { it.toString() } ?: ""
    var voiceAliases by remember { mutableStateOf(voiceAliasesRaw) }

    // Parse existing supplementary map — items stored as Map{"enabled":true,"rate":x} or false
    @Suppress("UNCHECKED_CAST")
    val existingSupp = (override?.get("supplementary") as? Map<String, Any?>) ?: emptyMap()
    val suppEnabled = remember {
        mutableStateMapOf<String, Boolean>().also { m ->
            SUPPLEMENTARY_ITEMS.forEach { (key, _) ->
                m[key] = when (val entry = existingSupp[key]) {
                    is Boolean -> entry
                    is Map<*, *> -> (entry["enabled"] as? Boolean) ?: true
                    else -> false
                }
            }
        }
    }
    val suppRates = remember {
        mutableStateMapOf<String, String>().also { m ->
            SUPPLEMENTARY_ITEMS.forEach { (key, _) ->
                val entry = existingSupp[key]
                val v = when (entry) {
                    is Map<*, *> -> (entry["rate"] as? Number)?.toDouble()
                    else -> null
                }
                m[key] = v?.let { "%.2f".format(it) } ?: ""
            }
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to default?") },
            text = { Text("This will remove all customisations for \"${template.str("name")}\" and revert to system defaults.") },
            confirmButton = {
                Button(
                    onClick = { showResetConfirm = false; onReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(template.str("name"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Default: ${PRICING_LABELS[defaultMethod] ?: defaultMethod}", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
        }

        // Pricing method selection
        item {
            Text("Pricing method", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            allowedMethods.forEach { method ->
                val label = PRICING_LABELS[method] ?: method
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedMethod = method }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMethod == method,
                        onClick = { selectedMethod = method }
                    )
                    Text(label, fontSize = 13.sp)
                    if (method == defaultMethod) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "default",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Rate input
        item {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Rate (£)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("£") }
            )
        }

        // Supplementary items
        item {
            Spacer(Modifier.height(8.dp))
            Text("Supplementary items", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Enable extras that can be added to this activity's quote line.", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
        }

        items(SUPPLEMENTARY_ITEMS) { (key, label) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = suppEnabled[key] == true,
                    onCheckedChange = { suppEnabled[key] = it }
                )
                Text(label, Modifier.weight(1f), fontSize = 13.sp)
                if (suppEnabled[key] == true) {
                    OutlinedTextField(
                        value = suppRates[key] ?: "",
                        onValueChange = { suppRates[key] = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("£/unit") },
                        singleLine = true,
                        modifier = Modifier.width(100.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }

        // Custom name + active toggle
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Custom name (optional)") },
                placeholder = { Text(template.str("name"), color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { isActive = !isActive },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                Column {
                    Text("Active", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Show this activity in quotes and voice commands", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        // Voice aliases
        item {
            Spacer(Modifier.height(8.dp))
            Text("Voice aliases", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Comma-separated phrases that trigger this activity by voice.", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = voiceAliases,
                onValueChange = { voiceAliases = it },
                label = { Text("e.g. mow lawn, cut grass, mowing") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        // Notes
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (internal)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        // Action buttons
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val suppMap = SUPPLEMENTARY_ITEMS.associate { (key, _) ->
                            val enabled = suppEnabled[key] == true
                            val r = suppRates[key]?.toDoubleOrNull() ?: 0.0
                            key to if (enabled) mapOf("enabled" to true, "rate" to r) else false
                        }
                        val aliases = voiceAliases.split(",")
                            .map { it.trim() }.filter { it.isNotBlank() }
                        onSave(mapOf(
                            "pricing_method" to selectedMethod,
                            "rate" to (rate.toDoubleOrNull() ?: 0.0),
                            "supplementary" to suppMap,
                            "custom_name" to customName.trim().ifBlank { null },
                            "is_active" to isActive,
                            "voice_aliases" to aliases,
                            "notes" to notes.trim().ifBlank { null }
                        ))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
            if (override != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("Reset to system default") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
