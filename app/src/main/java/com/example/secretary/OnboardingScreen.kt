package com.example.secretary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One selected industry combo — (group, optional subtype). */
data class IndustryEntry(
    val groupId: Long,
    val groupName: String,
    val subtypeId: Long? = null,
    val subtypeName: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: SecretaryViewModel, onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Form state
    var companyName by remember { mutableStateOf("") }
    var legalType by remember { mutableStateOf("sole_trader") }
    var selectedIndustries by remember { mutableStateOf<List<IndustryEntry>>(emptyList()) }
    var internalLangMode by remember { mutableStateOf("single") }
    var customerLangMode by remember { mutableStateOf("single") }
    var defaultInternalLang by remember { mutableStateOf("en") }
    var defaultCustomerLang by remember { mutableStateOf("en") }
    var workspaceMode by remember { mutableStateOf("solo") }

    // Data from server
    var industryGroups by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val res = viewModel.api.getIndustryGroups()
            if (res.isSuccessful) industryGroups = res.body() ?: emptyList()
        } catch (_: Exception) {}
    }

    val totalSteps = 5

    Scaffold(
        topBar = { TopAppBar(title = { Text(Strings.onboardingTitle(step + 1, totalSteps)) }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Progress
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
            Text(Strings.onboardingStepTitle(step), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))

            // Step content
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> StepCompany(companyName, legalType,
                        onNameChange = { companyName = it },
                        onLegalChange = { legalType = it })
                    1 -> StepIndustry(
                        groups = industryGroups,
                        selectedIndustries = selectedIndustries,
                        onToggleEntry = { entry ->
                            selectedIndustries = if (selectedIndustries.any {
                                    it.groupId == entry.groupId && it.subtypeId == entry.subtypeId
                                }) {
                                selectedIndustries.filter {
                                    !(it.groupId == entry.groupId && it.subtypeId == entry.subtypeId)
                                }
                            } else {
                                selectedIndustries + entry
                            }
                        },
                        fetchSubtypes = { groupId ->
                            try {
                                val res = viewModel.api.getIndustrySubtypes(groupId)
                                if (res.isSuccessful) res.body() ?: emptyList() else emptyList()
                            } catch (_: Exception) { emptyList() }
                        }
                    )
                    2 -> StepLanguageMode(internalLangMode, customerLangMode,
                        onInternalChange = { internalLangMode = it },
                        onCustomerChange = { customerLangMode = it })
                    3 -> StepDefaultLanguages(defaultInternalLang, defaultCustomerLang,
                        onInternalChange = { defaultInternalLang = it },
                        onCustomerChange = { defaultCustomerLang = it })
                    4 -> StepWorkspace(workspaceMode, onSelect = { workspaceMode = it })
                }
            }

            if (error != null) {
                Text(error!!, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }

            // Navigation buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) { Text(Strings.back) }
                } else { Spacer(Modifier.width(1.dp)) }

                if (step < totalSteps - 1) {
                    Button(onClick = {
                        if (step == 0 && companyName.isBlank()) { error = Strings.enterCompanyNameError; return@Button }
                        if (step == 1 && selectedIndustries.isEmpty()) { error = Strings.selectIndustryError; return@Button }
                        error = null; step++
                    }) { Text(Strings.next) }
                } else {
                    Button(onClick = {
                        loading = true; error = null
                        viewModel.submitOnboarding(
                            companyName = companyName,
                            legalType = legalType,
                            industries = selectedIndustries,
                            internalLangMode = internalLangMode,
                            customerLangMode = customerLangMode,
                            defaultInternalLang = defaultInternalLang,
                            defaultCustomerLang = defaultCustomerLang,
                            workspaceMode = workspaceMode,
                            onSuccess = { loading = false; onComplete() },
                            onError = { msg -> loading = false; error = msg }
                        )
                    }, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp))
                        else Text(Strings.finish)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndustry(
    groups: List<Map<String, Any?>>,
    selectedIndustries: List<IndustryEntry>,
    onToggleEntry: (IndustryEntry) -> Unit,
    fetchSubtypes: suspend (Long) -> List<Map<String, Any?>>
) {
    // Track which groups are expanded
    var expandedGroups by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // Lazy-loaded subtypes per group
    var subtypesByGroup by remember { mutableStateOf<Map<Long, List<Map<String, Any?>>>>(emptyMap()) }
    // Track which groups are currently loading subtypes
    var loadingGroups by remember { mutableStateOf<Set<Long>>(emptySet()) }

    Column {
        Text(Strings.chooseIndustry, fontWeight = FontWeight.SemiBold)
        if (selectedIndustries.isNotEmpty()) {
            Text(
                "${selectedIndustries.size} ${Strings.selectedCount(selectedIndustries.size)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(groups) { g ->
                val gid = (g["id"] as? Number)?.toLong() ?: return@items
                val gname = g["name"]?.toString() ?: ""
                val isExpanded = gid in expandedGroups
                val subtypes = subtypesByGroup[gid] ?: emptyList()
                val isLoading = gid in loadingGroups

                // Any selection for this group?
                val groupEntries = selectedIndustries.filter { it.groupId == gid }
                val groupOnlyChecked = groupEntries.any { it.subtypeId == null }
                val anyChecked = groupEntries.isNotEmpty()

                // GROUP ROW
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (anyChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    tonalElevation = if (anyChecked) 2.dp else 0.dp
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Toggle expand; load subtypes on first expand
                                expandedGroups = if (isExpanded) expandedGroups - gid else expandedGroups + gid
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox for group-level selection (null subtype)
                        Checkbox(
                            checked = groupOnlyChecked,
                            onCheckedChange = {
                                onToggleEntry(IndustryEntry(gid, gname, null, null))
                            }
                        )
                        Text(
                            gname,
                            Modifier.weight(1f).padding(start = 4.dp),
                            fontWeight = if (anyChecked) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Load subtypes when group is expanded for first time
                if (isExpanded && subtypes.isEmpty() && gid !in loadingGroups) {
                    LaunchedEffect(gid) {
                        loadingGroups = loadingGroups + gid
                        val loaded = fetchSubtypes(gid)
                        subtypesByGroup = subtypesByGroup + (gid to loaded)
                        loadingGroups = loadingGroups - gid
                    }
                }

                // SUBTYPE ROWS (shown when expanded and subtypes loaded)
                if (isExpanded && subtypes.isNotEmpty()) {
                    subtypes.forEach { s ->
                        val sid = (s["id"] as? Number)?.toLong() ?: return@forEach
                        val sname = s["name"]?.toString() ?: ""
                        val subtypeChecked = selectedIndustries.any { it.groupId == gid && it.subtypeId == sid }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 0.dp, top = 1.dp, bottom = 1.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = if (subtypeChecked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (subtypeChecked) 1.dp else 0.dp
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleEntry(IndustryEntry(gid, gname, sid, sname)) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = subtypeChecked,
                                    onCheckedChange = { onToggleEntry(IndustryEntry(gid, gname, sid, sname)) }
                                )
                                Text(
                                    sname,
                                    Modifier.padding(start = 4.dp),
                                    fontSize = 14.sp,
                                    fontWeight = if (subtypeChecked) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StepCompany(name: String, legalType: String, onNameChange: (String) -> Unit, onLegalChange: (String) -> Unit) {
    val types = listOf(
        "sole_trader" to "${Strings.soleTrader} (Sole Trader)",
        "ltd" to Strings.ltdCompany,
        "partnership" to Strings.partnership,
        "other" to Strings.otherOption
    )
    Column {
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text(Strings.companyNameRequired) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        Text(Strings.legalForm, fontWeight = FontWeight.SemiBold)
        types.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().selectable(selected = legalType == value, onClick = { onLegalChange(value) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = legalType == value, onClick = { onLegalChange(value) })
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun StepLanguageMode(internalMode: String, customerMode: String, onInternalChange: (String) -> Unit, onCustomerChange: (String) -> Unit) {
    Column {
        Text(Strings.internalLanguageCompany, fontWeight = FontWeight.SemiBold)
        Text(Strings.internalLanguageQuestion, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = internalMode == "single", onClick = { onInternalChange("single") }, label = { Text(Strings.singleLanguage) })
            FilterChip(selected = internalMode == "multi", onClick = { onInternalChange("multi") }, label = { Text(Strings.multipleLanguages) })
        }
        Spacer(Modifier.height(24.dp))
        Text(Strings.customerLanguage, fontWeight = FontWeight.SemiBold)
        Text(Strings.customerLanguageQuestion, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = customerMode == "single", onClick = { onCustomerChange("single") }, label = { Text(Strings.singleLanguage) })
            FilterChip(selected = customerMode == "multi", onClick = { onCustomerChange("multi") }, label = { Text(Strings.multipleLanguages) })
        }
    }
}

@Composable
private fun StepDefaultLanguages(internalLang: String, customerLang: String, onInternalChange: (String) -> Unit, onCustomerChange: (String) -> Unit) {
    val langs = listOf("en" to "English", "cs" to "Čeština", "pl" to "Polski", "de" to "Deutsch", "fr" to "Français", "es" to "Español", "sk" to "Slovenčina")
    Column {
        Text(Strings.primaryInternalLanguage, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        langs.forEach { (code, name) ->
            Row(Modifier.fillMaxWidth().selectable(selected = internalLang == code, onClick = { onInternalChange(code) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = internalLang == code, onClick = { onInternalChange(code) })
                Text(name, Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(Strings.primaryCustomerLanguage, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        langs.forEach { (code, name) ->
            Row(Modifier.fillMaxWidth().selectable(selected = customerLang == code, onClick = { onCustomerChange(code) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = customerLang == code, onClick = { onCustomerChange(code) })
                Text(name, Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun StepWorkspace(mode: String, onSelect: (String) -> Unit) {
    val modes = listOf(
        Triple("solo", Strings.localizeWorkspaceMode("solo"), Strings.workspaceSoloDesc),
        Triple("team", Strings.localizeWorkspaceMode("team"), Strings.workspaceTeamDesc),
        Triple("business", Strings.localizeWorkspaceMode("business"), Strings.workspaceBusinessDesc)
    )
    Column {
        Text(Strings.companySize, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        modes.forEach { (value, title, desc) ->
            val isSelected = mode == value
            Card(
                onClick = { onSelect(value) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = { onSelect(value) })
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(desc, fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
