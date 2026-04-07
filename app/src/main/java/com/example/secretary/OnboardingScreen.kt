package com.example.secretary

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: SecretaryViewModel, onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Form state
    var companyName by remember { mutableStateOf("") }
    var legalType by remember { mutableStateOf("sole_trader") }
    var industryGroupId by remember { mutableStateOf<Long?>(null) }
    var industryGroupName by remember { mutableStateOf("") }
    var industrySubtypeId by remember { mutableStateOf<Long?>(null) }
    var industrySubtypeName by remember { mutableStateOf("") }
    var internalLangMode by remember { mutableStateOf("single") }
    var customerLangMode by remember { mutableStateOf("single") }
    var defaultInternalLang by remember { mutableStateOf("en") }
    var defaultCustomerLang by remember { mutableStateOf("en") }
    var workspaceMode by remember { mutableStateOf("solo") }

    // Data from server
    var industryGroups by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var industrySubtypes by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val res = viewModel.api.getIndustryGroups()
            if (res.isSuccessful) industryGroups = res.body() ?: emptyList()
        } catch (_: Exception) {}
    }

    LaunchedEffect(industryGroupId) {
        if (industryGroupId != null) {
            try {
                val res = viewModel.api.getIndustrySubtypes(industryGroupId!!)
                if (res.isSuccessful) industrySubtypes = res.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    val totalSteps = 5
    val stepTitles = listOf("Firma", "Obor", "Jazyky", "Výchozí jazyky", "Režim")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nastavení firmy — krok ${step+1}/$totalSteps") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Progress
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
            Text(stepTitles[step], fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))

            // Step content
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> StepCompany(companyName, legalType,
                        onNameChange = { companyName = it },
                        onLegalChange = { legalType = it })
                    1 -> StepIndustry(industryGroups, industrySubtypes,
                        industryGroupId, industrySubtypeId,
                        onGroupSelect = { id, name -> industryGroupId = id; industryGroupName = name; industrySubtypeId = null },
                        onSubtypeSelect = { id, name -> industrySubtypeId = id; industrySubtypeName = name })
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
                    OutlinedButton(onClick = { step-- }) { Text("Zpět") }
                } else { Spacer(Modifier.width(1.dp)) }

                if (step < totalSteps - 1) {
                    Button(onClick = {
                        if (step == 0 && companyName.isBlank()) { error = "Zadejte název firmy"; return@Button }
                        if (step == 1 && industryGroupId == null) { error = "Vyberte obor"; return@Button }
                        error = null; step++
                    }) { Text("Další") }
                } else {
                    Button(onClick = {
                        loading = true; error = null
                        viewModel.submitOnboarding(

                            companyName = companyName,
                            legalType = legalType,
                            industryGroupId = industryGroupId,
                            industrySubtypeId = industrySubtypeId,
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
                        else Text("Dokončit")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCompany(name: String, legalType: String, onNameChange: (String) -> Unit, onLegalChange: (String) -> Unit) {
    val types = listOf("sole_trader" to "Živnostník (Sole Trader)", "ltd" to "Ltd (Company)", "partnership" to "Partnerství", "other" to "Jiné")
    Column {
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Název firmy *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        Text("Právní forma", fontWeight = FontWeight.SemiBold)
        types.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().selectable(selected = legalType == value, onClick = { onLegalChange(value) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = legalType == value, onClick = { onLegalChange(value) })
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun StepIndustry(
    groups: List<Map<String, Any?>>, subtypes: List<Map<String, Any?>>,
    selectedGroup: Long?, selectedSubtype: Long?,
    onGroupSelect: (Long, String) -> Unit, onSubtypeSelect: (Long, String) -> Unit
) {
    Column {
        Text("Vyberte obor podnikání *", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(groups) { g ->
                val gid = (g["id"] as? Number)?.toLong() ?: return@items
                val gname = g["name"]?.toString() ?: ""
                val isSelected = selectedGroup == gid
                Card(
                    onClick = { onGroupSelect(gid, gname) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) { Text(gname, Modifier.padding(12.dp), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        if (selectedGroup != null && subtypes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Specializace", fontWeight = FontWeight.SemiBold)
            LazyColumn(Modifier.weight(1f)) {
                items(subtypes) { s ->
                    val sid = (s["id"] as? Number)?.toLong() ?: return@items
                    val sname = s["name"]?.toString() ?: ""
                    val isSelected = selectedSubtype == sid
                    Card(
                        onClick = { onSubtypeSelect(sid, sname) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    ) { Text(sname, Modifier.padding(12.dp), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
    }
}

@Composable
private fun StepLanguageMode(internalMode: String, customerMode: String, onInternalChange: (String) -> Unit, onCustomerChange: (String) -> Unit) {
    Column {
        Text("Interní jazyk (ve firmě)", fontWeight = FontWeight.SemiBold)
        Text("Jak komunikujete uvnitř firmy?", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = internalMode == "single", onClick = { onInternalChange("single") }, label = { Text("Jeden jazyk") })
            FilterChip(selected = internalMode == "multi", onClick = { onInternalChange("multi") }, label = { Text("Více jazyků") })
        }
        Spacer(Modifier.height(24.dp))
        Text("Jazyk pro zákazníky", fontWeight = FontWeight.SemiBold)
        Text("V jakém jazyce komunikujete se zákazníky?", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = customerMode == "single", onClick = { onCustomerChange("single") }, label = { Text("Jeden jazyk") })
            FilterChip(selected = customerMode == "multi", onClick = { onCustomerChange("multi") }, label = { Text("Více jazyků") })
        }
    }
}

@Composable
private fun StepDefaultLanguages(internalLang: String, customerLang: String, onInternalChange: (String) -> Unit, onCustomerChange: (String) -> Unit) {
    val langs = listOf("en" to "English", "cs" to "Čeština", "pl" to "Polski", "de" to "Deutsch", "fr" to "Français", "es" to "Español", "sk" to "Slovenčina")
    Column {
        Text("Hlavní interní jazyk", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        langs.forEach { (code, name) ->
            Row(Modifier.fillMaxWidth().selectable(selected = internalLang == code, onClick = { onInternalChange(code) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = internalLang == code, onClick = { onInternalChange(code) })
                Text(name, Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Hlavní jazyk pro zákazníky", fontWeight = FontWeight.SemiBold)
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
        Triple("solo", "Solo", "Jeden uživatel, ideální pro živnostníky"),
        Triple("team", "Tým", "2-5 uživatelů, malý tým"),
        Triple("business", "Firma", "6-30 uživatelů, větší firma")
    )
    Column {
        Text("Velikost firmy", fontWeight = FontWeight.SemiBold)
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
