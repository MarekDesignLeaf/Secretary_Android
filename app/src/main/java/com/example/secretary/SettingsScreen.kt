package com.example.secretary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val sm = viewModel.getSettingsManager() ?: return
    val canManageHierarchy = state.currentUserPermissions["manage_users"] == true ||
        state.currentUserRole == "admin" ||
        state.currentUserRole == "manager"
    LaunchedEffect(Unit) {
        viewModel.loadTenantConfig()
        viewModel.loadSettings()
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Text(Strings.settings, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item { RuntimeDataStatusSection(viewModel, sm) }
        item { CompanyProfileSection(viewModel, sm) }
        item { RatesSection(viewModel) }
        item { LanguageSection(sm, viewModel) }
        item { ThemeSection(sm) }
        item { VoiceSection(sm) }
        item { AssistantMemorySection(viewModel) }
        item { ServerSection(sm, viewModel, state) }
        item { CrmSection(sm) }
        item { NotificationSection(sm) }
        item { WorkProfileSection(sm) }
        item { UsersSection(sm, viewModel) }
        if (canManageHierarchy) {
            item { HierarchyIntegritySection(viewModel) }
            item { AdminActivitySection(viewModel) }
        }
        item { DataSection(sm, viewModel) }
        item { AboutSection() }
        item { VersionHistorySection() }
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) { Text(Strings.logout) }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// RATES / SAZBY
@Composable private fun RatesSection(viewModel: SecretaryViewModel) {
    var exp by remember { mutableStateOf(false) }
    var gardenRate by remember { mutableStateOf("") }
    var hedgeRate by remember { mutableStateOf("") }
    var arboristRate by remember { mutableStateOf("") }
    var wasteBag by remember { mutableStateOf("") }
    var minCharge by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    LaunchedEffect(Unit) {
        try {
            val sm = viewModel.getSettingsManager() ?: run { loadError = "Settings manager unavailable"; return@LaunchedEffect }
            val token = sm.accessToken ?: run { loadError = "Not authenticated"; return@LaunchedEffect }
            val res = viewModel.api.getDefaultRates("Bearer $token", 1)
            if (res.isSuccessful) {
                val body = res.body() ?: run { loadError = "Empty response"; return@LaunchedEffect }
                fun r(key: String): String? = (body[key] as? Map<*,*>)?.get("rate")?.toString()?.toDoubleOrNull()?.let { fmt(it) }
                gardenRate = r("garden_maintenance") ?: ""
                hedgeRate = r("hedge_trimming") ?: ""
                arboristRate = r("arborist_works") ?: ""
                wasteBag = r("garden_waste_bulkbag") ?: ""
                minCharge = r("minimum_charge") ?: ""
                loaded = true
            } else {
                loadError = "HTTP ${res.code()}"
            }
        } catch (e: Exception) { loadError = e.message ?: "Error" }
    }
    SCard(Strings.serviceRates, Icons.Default.ShoppingCart, exp, { exp = !exp }) {
        when {
            loadError != null -> Text("❌ Sazby se nepodařilo načíst ze serveru: $loadError", color = Color.Red, fontSize = 12.sp)
            !loaded -> Text("⏳ Načítání sazeb ze serveru...", color = Color.Gray, fontSize = 12.sp)
            else -> Text("✅ Sazby načteny ze serveru", color = Color(0xFF4CAF50), fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(Strings.hourlyRatesByType, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = gardenRate, onValueChange = { gardenRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("🌿 ${Strings.gardenMaintenanceRate}") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(Strings.gardenMaintenanceHint, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = hedgeRate, onValueChange = { hedgeRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("🌳 ${Strings.hedgeTrimmingRate}") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = arboristRate, onValueChange = { arboristRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("🪓 ${Strings.arboristWorksRate}") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        Text(Strings.otherRates, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = wasteBag, onValueChange = { wasteBag = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(Strings.wasteRemovalRate) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = minCharge, onValueChange = { minCharge = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(Strings.minimumJobPrice) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val gR = gardenRate.toDoubleOrNull()
            val hR = hedgeRate.toDoubleOrNull()
            val aR = arboristRate.toDoubleOrNull()
            val wB = wasteBag.toDoubleOrNull()
            val mC = minCharge.toDoubleOrNull()
            if (gR != null && hR != null && aR != null && wB != null && mC != null) {
                viewModel.updateDefaultRates(mapOf(
                    "garden_maintenance" to gR,
                    "hedge_trimming" to hR,
                    "arborist_works" to aR,
                    "hourly_rate" to gR,
                    "garden_waste_bulkbag" to wB,
                    "minimum_charge" to mC
                ))
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = loaded || (gardenRate.isNotBlank() && hedgeRate.isNotBlank())) { Text(Strings.save) }
    }
}

// 0. JAZYK / LANGUAGE
@Composable private fun LanguageSection(sm: SettingsManager, viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var exp by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf<String?>(null) }
    var showCustomerConfirm by remember { mutableStateOf<String?>(null) }
    var customerLanguageFeedback by remember { mutableStateOf<String?>(null) }
    val currentLang = state.appLanguage.ifBlank { sm.getCurrentAppLanguage() }
    val langName = Strings.languageDisplayName(currentLang)
    val canEditCustomerLanguage = state.currentUserPermissions["manage_users"] == true || state.currentUserRole == "admin"

    // Use ONLY the dedicated /tenant/languages endpoint — no fallback
    val langsData = state.tenantLanguages
    val langsError = state.settingsLoadErrors["languages"]
    @Suppress("UNCHECKED_CAST")
    val langsList = (langsData?.get("languages") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val langsFound = langsData?.get("found") as? Boolean
    val internalCodes = langsList?.filter { it["scope"] == "internal" }
        ?.mapNotNull { it["code"]?.toString() }?.filter { it.isNotBlank() }?.distinct()
    val customerCodes = langsList?.filter { it["scope"] == "customer" }
        ?.mapNotNull { it["code"]?.toString() }?.filter { it.isNotBlank() }?.distinct()
    val currentCustomerLang = state.tenantProfile?.get("default_customer_lang")?.toString()
        ?: langsData?.let {
            (it["internal_langs"] as? List<*>)?.firstOrNull()?.toString()
        } ?: ""

    SCard(Strings.language + ": $langName", Icons.Default.Star, exp, { exp = !exp }) {
        // Internal language
        Text(Strings.systemLanguage, fontWeight = FontWeight.SemiBold)
        Text(Strings.systemLanguageHint, fontSize = 12.sp, color = Color.Gray)
        when {
            langsData == null && langsError == null -> {
                Text("Načítám jazyky ze serveru…", fontSize = 12.sp, color = Color.Gray)
            }
            langsError != null -> {
                Text("❌ Chyba načítání jazyků: $langsError", fontSize = 12.sp, color = Color.Red)
            }
            langsFound == false -> {
                Text("⚠️ Jazyky nejsou nastaveny (tenant languages not set up)", fontSize = 12.sp, color = Color(0xFFFF9800))
            }
            internalCodes.isNullOrEmpty() -> {
                Text("⚠️ Žádné interní jazyky nenalezeny", fontSize = 12.sp, color = Color(0xFFFF9800))
            }
            else -> {
                Text("✅ ${internalCodes.size} interní jazyk(y) načteno ze serveru", fontSize = 11.sp, color = Color(0xFF4CAF50))
                internalCodes.forEach { code ->
                    val label = langDisplayName(code)
                    val selected = currentLang.lowercase().startsWith(code.take(2).lowercase()) || currentLang == code
                    Row(Modifier.fillMaxWidth().clickable { if (!selected) showConfirm = code }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { if (!selected) showConfirm = code })
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // Customer language
        Text(Strings.customerLanguage, fontWeight = FontWeight.SemiBold)
        Text(
            if (canEditCustomerLanguage) Strings.customerLanguageHint else Strings.adminOnlyLanguageHint,
            fontSize = 12.sp,
            color = Color.Gray
        )
        when {
            langsData == null && langsError == null -> {
                Text("Načítám zákaznické jazyky…", fontSize = 12.sp, color = Color.Gray)
            }
            customerCodes.isNullOrEmpty() && langsFound != false -> {
                Text("⚠️ Žádné zákaznické jazyky nenalezeny", fontSize = 12.sp, color = Color(0xFFFF9800))
            }
            !customerCodes.isNullOrEmpty() -> {
                customerCodes.forEach { code ->
                    val label = langDisplayName(code)
                    val selected = currentCustomerLang.lowercase().startsWith(code.take(2).lowercase()) || currentCustomerLang == code
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = canEditCustomerLanguage) {
                                if (!selected) { customerLanguageFeedback = null; showCustomerConfirm = code }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { if (canEditCustomerLanguage && !selected) { customerLanguageFeedback = null; showCustomerConfirm = code } },
                            enabled = canEditCustomerLanguage
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp,
                            color = if (canEditCustomerLanguage) Color.Unspecified else Color.Gray)
                    }
                }
            }
            else -> {}
        }
        customerLanguageFeedback?.let { Text(it, fontSize = 12.sp, color = Color.Red) }
    }
    if (showConfirm != null) {
        val newLangName = Strings.languageDisplayName(showConfirm!!)
        AlertDialog(
            onDismissRequest = { showConfirm = null },
            title = { Text(Strings.changeLanguage) },
            text = { Text("${Strings.languageChangeConfirm} $newLangName?") },
            confirmButton = { Button(onClick = { viewModel.changeLanguage(showConfirm!!); showConfirm = null }) { Text(Strings.confirm) } },
            dismissButton = { TextButton(onClick = { showConfirm = null }) { Text(Strings.cancel) } }
        )
    }
    if (showCustomerConfirm != null) {
        val newLangName = Strings.languageDisplayName(showCustomerConfirm!!)
        AlertDialog(
            onDismissRequest = { showCustomerConfirm = null },
            title = { Text(Strings.customerLanguage) },
            text = { Text("${Strings.languageChangeConfirm} $newLangName?") },
            confirmButton = {
                Button(onClick = {
                    val selectedLang = showCustomerConfirm!!
                    viewModel.updateCustomerLanguage(selectedLang) { ok, msg ->
                        customerLanguageFeedback = if (ok) null else msg
                    }
                    showCustomerConfirm = null
                }) { Text(Strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showCustomerConfirm = null }) { Text(Strings.cancel) } }
        )
    }
}

// 1. MOTIV
@Composable private fun ThemeSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(sm.themeMode) }
    SCard(Strings.appTheme, Icons.Default.Settings, exp, { exp = !exp }) {
        val options = listOf("system" to Strings.accordingToSystem, "light" to Strings.lightTheme, "dark" to Strings.darkTheme)
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { mode = value; sm.themeMode = value }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == value, onClick = { mode = value; sm.themeMode = value })
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
        Text(Strings.themeRestartHint, fontSize = 11.sp, color = Color.Gray)
    }
}

// 1. HLASOVE OVLADANI
@Composable private fun VoiceSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(true) }
    val voiceCtx = androidx.compose.ui.platform.LocalContext.current
    SCard(Strings.voiceControl, Icons.Default.Call, exp, { exp = !exp }) {
        var hw by remember { mutableStateOf(sm.hotwordEnabled) }
        SSwitch(Strings.hotwordDetection, Strings.hotwordListeningHint, hw) { hw = it; sm.hotwordEnabled = it }
        var word by remember { mutableStateOf(sm.activationWord) }
        var wordChanged by remember { mutableStateOf(false) }
        SField(Strings.activationWord, word, { word = it; wordChanged = (it != sm.activationWord) })
        if (wordChanged) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { word = sm.activationWord; wordChanged = false }) { Text(Strings.cancel) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    sm.activationWord = word
                    wordChanged = false
                    val svcIntent = android.content.Intent(voiceCtx, VoiceService::class.java)
                    voiceCtx.stopService(svcIntent)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        voiceCtx.startForegroundService(svcIntent)
                    } else {
                        voiceCtx.startService(svcIntent)
                    }
                }) { Text(Strings.save) }
            }
        }
        var rate by remember { mutableFloatStateOf(sm.ttsRate) }
        SSlider(Strings.speechRate, rate, 0.5f..2.0f, 5, "%.1fx".format(rate), { rate = it }) { sm.ttsRate = rate }
        var pitch by remember { mutableFloatStateOf(sm.ttsPitch) }
        SSlider(Strings.voicePitch, pitch, 0.5f..2.0f, 5, "%.1fx".format(pitch), { pitch = it }) { sm.ttsPitch = pitch }
        var sil by remember { mutableFloatStateOf(sm.silenceLength.toFloat()) }
        SSlider(Strings.silenceLengthLabel, sil, 1500f..10000f, 7, "%.1fs".format(sil / 1000), { sil = it }) { sm.silenceLength = sil.toLong() }
    }
}

@Composable private fun AssistantMemorySection(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var exp by remember { mutableStateOf(false) }
    var newMemory by remember { mutableStateOf("") }
    fun fmt(ts: String?) = ts?.replace("T", " ")?.replace("Z", "")?.take(16).orEmpty()
    SCard(Strings.assistantMemoryTitle, Icons.Default.Info, exp, { exp = !exp }) {
        Text(Strings.assistantMemoryHint, fontSize = 12.sp, color = Color.Gray)
        OutlinedTextField(
            value = newMemory,
            onValueChange = { newMemory = it },
            label = { Text(Strings.assistantMemoryNewPlaceholder) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    viewModel.rememberAssistantMemory(newMemory)
                    newMemory = ""
                },
                enabled = newMemory.isNotBlank() && !state.assistantMemoryLoading
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.save)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.loadAssistantMemory() }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.reload)
            }
        }
        when {
            state.assistantMemoryLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.processing, fontSize = 12.sp, color = Color.Gray)
                }
            }
            !state.assistantMemoryError.isNullOrBlank() -> {
                Text(state.assistantMemoryError ?: Strings.assistantMemoryLoadFailed, color = Color.Red, fontSize = 12.sp)
            }
            state.assistantMemory.isEmpty() -> {
                Text(Strings.assistantMemoryEmpty, color = Color.Gray)
            }
            else -> {
                state.assistantMemory.forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.content, fontWeight = FontWeight.SemiBold)
                            val savedAt = fmt(item.updated_at)
                            if (savedAt.isNotBlank()) {
                                Text("${Strings.savedAtLabel}: $savedAt", fontSize = 11.sp, color = Color.Gray)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.deleteAssistantMemory(item.id) }) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(Strings.delete)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 2. SERVER
@Composable private fun ServerSection(sm: SettingsManager, vm: SecretaryViewModel, state: UiState) {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.serverConnection, Icons.Default.Info, exp, { exp = !exp }) {
        var url by remember { mutableStateOf(sm.apiUrl) }
        SField(Strings.apiServerUrl, url, { url = it; sm.apiUrl = it })
        val cs = state.connectionStatus
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when (cs) {
            ConnectionStatus.CONNECTED -> Color(0xFFE8F5E9); ConnectionStatus.TESTING -> Color(0xFFFFF8E1)
            ConnectionStatus.DISCONNECTED -> Color(0xFFFFEBEE); ConnectionStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
        })) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(when (cs) {
                ConnectionStatus.CONNECTED -> Color(0xFF4CAF50); ConnectionStatus.TESTING -> Color(0xFFFFC107)
                ConnectionStatus.DISCONNECTED -> Color(0xFFF44336); ConnectionStatus.UNKNOWN -> Color.Gray
            })); Spacer(Modifier.width(12.dp))
            Column { Text(when (cs) { ConnectionStatus.CONNECTED -> Strings.connected; ConnectionStatus.TESTING -> Strings.testing
                ConnectionStatus.DISCONNECTED -> Strings.serverUnavailable; ConnectionStatus.UNKNOWN -> Strings.unknown }, fontWeight = FontWeight.SemiBold)
                if (cs == ConnectionStatus.CONNECTED) { val v = state.systemSettings["version"]?.toString() ?: ""; if (v.isNotBlank()) Text(Strings.serverVersion(v), fontSize = 12.sp, color = Color.Gray) }
                if (cs == ConnectionStatus.DISCONNECTED) Text(Strings.checkUrlAndServer, fontSize = 12.sp, color = Color(0xFFF44336))
            }
        } }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.testConnection() }, Modifier.fillMaxWidth(), enabled = cs != ConnectionStatus.TESTING) {
            if (cs == ConnectionStatus.TESTING) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(Strings.testConnection)
        }
        var off by remember { mutableStateOf(sm.offlineMode) }
        SSwitch(Strings.offlineMode, Strings.offlineQueueHint, off) { off = it; sm.offlineMode = it }
    }
}

// 3. CRM
@Composable private fun CrmSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.crmSettingsLabel, Icons.Default.Person, exp, { exp = !exp }) {
        var rE by remember { mutableStateOf(false) }
        val refreshOptions = listOf(0, 5, 15, 30)
        SDrop(
            Strings.autoRefreshLabel,
            Strings.localizeAutoRefreshInterval(sm.autoRefreshInterval),
            rE,
            { rE = it },
            refreshOptions.map { Strings.localizeAutoRefreshInterval(it) }
        ) { sm.autoRefreshInterval = refreshOptions[it]; rE = false }
        var tE by remember { mutableStateOf(false) }
        val tabs = listOf("clients", "properties", "jobs", "waste", "finance")
        val selectedTab = tabs.getOrElse(sm.defaultCrmTab) { tabs.first() }
        SDrop(
            Strings.crmTabLabel,
            Strings.localizeCrmTab(selectedTab),
            tE,
            { tE = it },
            tabs.map { Strings.localizeCrmTab(it) }
        ) { sm.defaultCrmTab = it; tE = false }
        var sE by remember { mutableStateOf(false) }
        val sorts = listOf("name", "created", "activity")
        SDrop(
            Strings.sorting,
            Strings.localizeClientSortOrder(sm.clientSortOrder),
            sE,
            { sE = it },
            sorts.map { Strings.localizeClientSortOrder(it) }
        ) { sm.clientSortOrder = sorts[it]; sE = false }
    }
}

@Composable private fun AdminActivitySection(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var exp by remember { mutableStateOf(false) }
    var selectedActor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(exp) {
        if (exp) viewModel.loadAdminActivityLog()
    }
    val actors = state.adminActivityLog.map { entry ->
        val key = entry.actor_user_id?.toString() ?: entry.actor_email.ifBlank { entry.actor_display_name }
        key to entry.actor_display_name.ifBlank { entry.actor_email.ifBlank { Strings.unknown } }
    }.filter { it.first.isNotBlank() }.distinctBy { it.first }
    val effectiveActor = selectedActor?.takeIf { selected -> actors.any { it.first == selected } }
    val filteredLogs = if (effectiveActor == null) {
        state.adminActivityLog
    } else {
        state.adminActivityLog.filter { entry ->
            val key = entry.actor_user_id?.toString() ?: entry.actor_email.ifBlank { entry.actor_display_name }
            key == effectiveActor
        }
    }
    fun fmt(ts: String?) = ts?.replace("T", " ")?.replace("Z", "")?.take(16).orEmpty()

    SCard(Strings.adminUsageLogs, Icons.Default.Visibility, exp, { exp = !exp }) {
        Text(Strings.adminUsageLogsHint, fontSize = 12.sp, color = Color.Gray)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { viewModel.loadAdminActivityLog() }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.reload)
            }
        }
        when {
            state.adminActivityLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.processing, fontSize = 12.sp, color = Color.Gray)
                }
            }
            !state.adminActivityError.isNullOrBlank() -> {
                Text(state.adminActivityError ?: Strings.adminLogsLoadFailed, color = Color.Red, fontSize = 12.sp)
            }
            else -> {
                if (actors.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = if (effectiveActor == null) 0 else actors.indexOfFirst { it.first == effectiveActor } + 1,
                        edgePadding = 0.dp
                    ) {
                        Tab(selected = effectiveActor == null, onClick = { selectedActor = null }, text = { Text(Strings.allUsersLabel) })
                        actors.forEach { actor ->
                            Tab(selected = effectiveActor == actor.first, onClick = { selectedActor = actor.first }, text = { Text(actor.second) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (filteredLogs.isEmpty()) {
                    Text(Strings.noAdminLogs, color = Color.Gray)
                } else {
                    filteredLogs.forEach { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.description, fontWeight = FontWeight.SemiBold)
                                Text("${Strings.logActorLabel}: ${entry.actor_display_name.ifBlank { entry.actor_email.ifBlank { Strings.unknown } }}", fontSize = 12.sp, color = Color.Gray)
                                Text("${Strings.logActionLabel}: ${Strings.localizeAdminLogAction(entry.action)}", fontSize = 12.sp, color = Color.Gray)
                                Text("${Strings.logSourceLabel}: ${Strings.localizeAdminLogSource(entry.source_channel)}", fontSize = 12.sp, color = Color.Gray)
                                Text("${Strings.savedAtLabel}: ${fmt(entry.created_at)}", fontSize = 12.sp, color = Color.Gray)
                                if (entry.details.isNotEmpty()) {
                                    Text(Strings.logDetailsLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    entry.details.entries.forEach { detail ->
                                        Text("${detail.key}: ${detail.value}", fontSize = 11.sp, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun HierarchyIntegritySection(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var exp by remember { mutableStateOf(false) }
    LaunchedEffect(exp) {
        if (exp) viewModel.loadHierarchyIntegrity()
    }

    fun issueText(issues: List<String>): String =
        issues.distinct().joinToString("\n") { "\u2022 ${Strings.localizeHierarchyIssue(it)}" }

    fun blockedReasons(entry: BlockedUserDeactivation): String {
        val reasons = mutableListOf<String>()
        if (entry.owns_clients) reasons += Strings.orphanClientsLabel
        if (entry.owns_jobs) reasons += Strings.orphanJobsLabel
        if (entry.has_open_tasks) reasons += Strings.orphanTasksLabel
        if (entry.is_client_next_action_assignee) reasons += Strings.nextAction
        if (entry.is_job_next_action_assignee) reasons += Strings.nextActionMismatchesLabel
        return reasons.joinToString(", ")
    }

    SCard(Strings.hierarchyIntegrityTitle, Icons.Default.AccountTree, exp, { exp = !exp }) {
        Text(Strings.hierarchyIntegrityHint, fontSize = 12.sp, color = Color.Gray)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { viewModel.loadHierarchyIntegrity() }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.reload)
            }
        }
        when {
            state.hierarchyIntegrityLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.processing, fontSize = 12.sp, color = Color.Gray)
                }
            }
            !state.hierarchyIntegrityError.isNullOrBlank() -> {
                Text(state.hierarchyIntegrityError ?: Strings.hierarchyReportLoadFailed, color = Color.Red, fontSize = 12.sp)
            }
            else -> {
                val report = state.hierarchyIntegrityReport
                val summary = report?.summary
                if (report == null) {
                    Text(Strings.noIntegrityIssues, color = Color.Gray)
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Strings.hierarchyStatus, fontWeight = FontWeight.SemiBold)
                            Text("${Strings.orphanClientsLabel}: ${summary?.orphan_clients ?: 0}", fontSize = 12.sp)
                            Text("${Strings.orphanJobsLabel}: ${summary?.orphan_jobs ?: 0}", fontSize = 12.sp)
                            Text("${Strings.orphanTasksLabel}: ${summary?.orphan_tasks ?: 0}", fontSize = 12.sp)
                            Text("${Strings.blockedDeactivationsLabel}: ${summary?.blocked_user_deactivations ?: 0}", fontSize = 12.sp)
                            Text("${Strings.nextActionMismatchesLabel}: ${summary?.next_action_mismatches ?: 0}", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if ((summary?.orphan_clients ?: 0) == 0 &&
                        (summary?.orphan_jobs ?: 0) == 0 &&
                        (summary?.orphan_tasks ?: 0) == 0 &&
                        (summary?.blocked_user_deactivations ?: 0) == 0 &&
                        (summary?.next_action_mismatches ?: 0) == 0
                    ) {
                        Text(Strings.noIntegrityIssues, color = Color.Gray)
                    } else {
                        report.orphan_clients.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${Strings.orphanClientsLabel}: ${entry.display_name ?: entry.id}", fontWeight = FontWeight.SemiBold)
                                    entry.next_action_task_id?.let { Text("${Strings.nextAction}: $it", fontSize = 12.sp, color = Color.Gray) }
                                    Text(issueText(entry.issues), fontSize = 12.sp, color = Color.Red)
                                }
                            }
                        }
                        report.orphan_jobs.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${Strings.orphanJobsLabel}: ${entry.job_title ?: entry.id}", fontWeight = FontWeight.SemiBold)
                                    entry.next_action_task_id?.let { Text("${Strings.nextAction}: $it", fontSize = 12.sp, color = Color.Gray) }
                                    Text(issueText(entry.issues), fontSize = 12.sp, color = Color.Red)
                                }
                            }
                        }
                        report.orphan_tasks.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${Strings.orphanTasksLabel}: ${entry.title.ifBlank { entry.id }}", fontWeight = FontWeight.SemiBold)
                                    Text("${Strings.status}: ${Strings.localizeStatus(entry.status)}", fontSize = 12.sp, color = Color.Gray)
                                    Text(issueText(entry.issues), fontSize = 12.sp, color = Color.Red)
                                }
                            }
                        }
                        report.blocked_user_deactivations.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${Strings.blockedDeactivationsLabel}: ${entry.display_name.ifBlank { entry.email }}", fontWeight = FontWeight.SemiBold)
                                    Text("${Strings.openResponsibilitiesLabel}: ${blockedReasons(entry)}", fontSize = 12.sp, color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. NOTIFIKACE
@Composable private fun NotificationSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.notificationsLabel, Icons.Default.Notifications, exp, { exp = !exp }) {
        var p by remember { mutableStateOf(sm.persistentNotification) }
        SSwitch(Strings.persistentNotificationLabel, null, p) { p = it; sm.persistentNotification = it }
        var rE by remember { mutableStateOf(false) }
        val rems = listOf(0, 5, 15, 30, 60)
        SDrop(
            Strings.taskReminder,
            Strings.localizeReminderInterval(sm.reminderMinutes),
            rE,
            { rE = it },
            rems.map { Strings.localizeReminderInterval(it) }
        ) { sm.reminderMinutes = rems[it]; rE = false }
    }
}

// 5. PRACOVNI PROFIL + PODPISY
@Composable private fun WorkProfileSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.workProfileLabel, Icons.Default.DateRange, exp, { exp = !exp }) {
        var wh by remember { mutableStateOf(sm.workHoursEnabled) }
        SSwitch(Strings.workHours, Strings.hotwordDuringWorkHours, wh) { wh = it; sm.workHoursEnabled = it }
        var st by remember { mutableStateOf(sm.workHoursStart) }
        SField(Strings.startLabel, st, { st = it; sm.workHoursStart = it }, kbt = KeyboardType.Number)
        var en by remember { mutableStateOf(sm.workHoursEnd) }
        SField(Strings.endLabel, en, { en = it; sm.workHoursEnd = it }, kbt = KeyboardType.Number)
        var pE by remember { mutableStateOf(false) }
        val prios = listOf("low", "normal", "high", "urgent")
        SDrop(
            Strings.defaultPriorityLabel,
            Strings.localizePriority(sm.defaultTaskPriority),
            pE,
            { pE = it },
            prios.map { Strings.localizePriority(it) }
        ) { sm.defaultTaskPriority = prios[it]; pE = false }

        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        Text(Strings.emailSignatures, fontWeight = FontWeight.SemiBold)
        var sigs by remember { mutableStateOf(sm.getSavedSignatures()) }
        var actId by remember { mutableStateOf(sm.activeSignatureId.ifBlank { sigs.firstOrNull()?.id ?: "" }) }
        var content by remember { mutableStateOf(sigs.find { it.id == actId }?.content ?: sm.emailSignature) }
        var name by remember { mutableStateOf(sigs.find { it.id == actId }?.name ?: Strings.signatureDefaultName()) }
        var dirty by remember { mutableStateOf(false) }

        var slE by remember { mutableStateOf(false) }
        SDrop(Strings.activeSignature, name, slE, { slE = it }, sigs.map { it.name }) { i ->
            val s = sigs[i]; actId = s.id; content = s.content; name = s.name; dirty = false
            sm.activeSignatureId = s.id; sm.emailSignature = s.content; slE = false
        }
        OutlinedTextField(name, { name = it; dirty = true }, label = { Text(Strings.signatureName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(content, { content = it; dirty = true }, label = { Text(Strings.signatureContent) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), maxLines = 10, singleLine = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { sigs = sigs.map { if (it.id == actId) it.copy(name = name, content = content) else it }; sm.saveSignatures(sigs); sm.emailSignature = content; dirty = false }, enabled = dirty, modifier = Modifier.weight(1f)) { Text(Strings.save) }
            OutlinedButton(onClick = { val o = sigs.find { it.id == actId }; content = o?.content ?: ""; name = o?.name ?: ""; dirty = false }, enabled = dirty, modifier = Modifier.weight(1f)) { Text(Strings.cancel) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { val n = SavedSignature(name = "${Strings.newSignature} ${sigs.size + 1}"); sigs = sigs + n; sm.saveSignatures(sigs); actId = n.id; content = ""; name = n.name; dirty = false; sm.activeSignatureId = n.id }, modifier = Modifier.weight(1f)) { Text("+ ${Strings.newSignature}") }
            if (sigs.size > 1) OutlinedButton(onClick = { sigs = sigs.filter { it.id != actId }; sm.saveSignatures(sigs); val f = sigs.first(); actId = f.id; content = f.content; name = f.name; sm.activeSignatureId = f.id; sm.emailSignature = f.content; dirty = false },
                modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text(Strings.delete) }
        }
    }
}

// 6. UZIVATELE A PRAVA
@Composable private fun UsersSection(sm: SettingsManager, viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var exp by remember { mutableStateOf(false) }
    var showCreateBackendUser by remember { mutableStateOf(false) }
    var editBackendUser by remember { mutableStateOf<BackendUser?>(null) }
    var userFeedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val backendRoles = if (state.backendRoles.isNotEmpty()) {
        state.backendRoles
    } else {
        listOf("admin", "manager", "worker", "assistant", "viewer").map { BackendRole(role_name = it) }
    }

    LaunchedEffect(exp) {
        if (exp) {
            viewModel.loadBackendRoles()
            viewModel.loadBackendUsers()
        }
    }

    if (showCreateBackendUser) BackendUserCreateDialog(backendRoles, { showCreateBackendUser = false }, { name, email, role, onResult ->
        viewModel.createBackendUser(email, name, role) { ok, msg ->
            userFeedback = (if (ok) Strings.backendUserCreated else (msg ?: Strings.createUserFailed)) to ok
            onResult(ok, msg)
        }
    })
    editBackendUser?.let { backendUser ->
        BackendUserEditDialog(
            user = backendUser,
            roles = backendRoles,
            onDismiss = { editBackendUser = null },
            onSave = { name, phone, role, status, permissions, onResult ->
                viewModel.updateBackendUser(backendUser.id, name, phone, role, status, permissions) { ok, msg ->
                    userFeedback = (if (ok) Strings.backendUserUpdated else (msg ?: Strings.updateUserFailed)) to ok
                    if (ok) editBackendUser = null
                    onResult(ok, msg)
                }
            },
            onDelete = { onResult ->
                viewModel.deleteBackendUser(backendUser.id) { ok, msg ->
                    userFeedback = (if (ok) Strings.backendUserDeleted else (msg ?: Strings.deleteUserFailed)) to ok
                    if (ok) editBackendUser = null
                    onResult(ok, msg)
                }
            },
            onResetPassword = { onResult ->
                viewModel.resetBackendUserPassword(backendUser.id) { ok, msg ->
                    userFeedback = (if (ok) Strings.passwordResetDone else (msg ?: Strings.passwordResetFailed)) to ok
                    onResult(ok, msg)
                }
            }
        )
    }

    SCard(Strings.usersAndPermissions, Icons.Default.Lock, exp, { exp = !exp }) {
        Text(Strings.backendUsersLabel, fontWeight = FontWeight.SemiBold)
        Text(Strings.backendUserHint, fontSize = 12.sp, color = Color.Gray)
        Text(Strings.roleControlsPermissionsHint, fontSize = 12.sp, color = Color.Gray)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    userFeedback = null
                    viewModel.loadBackendRoles()
                    viewModel.loadBackendUsers()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.reload)
            }
            Button(
                onClick = {
                    userFeedback = null
                    showCreateBackendUser = true
                },
                modifier = Modifier.weight(1f)
            ) { Text("+ ${Strings.createBackendUser}") }
        }
        userFeedback?.let { (message, ok) ->
            Text(message, color = if (ok) Color(0xFF2E7D32) else Color.Red, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(Strings.presetProfilesLabel, fontWeight = FontWeight.SemiBold)
        Text(Strings.presetProfilesHint, fontSize = 12.sp, color = Color.Gray)
        backendRoles.filter { it.role_name.isNotBlank() }.forEach { role ->
            BackendRolePresetCard(role)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        when {
            state.backendUsersLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.processing, fontSize = 12.sp, color = Color.Gray)
                }
            }
            state.backendUsers.isNotEmpty() -> {
                state.backendUsers.forEach { user ->
                    val roleLabel = Strings.localizeRole(user.role_name ?: "viewer")
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            userFeedback = null
                            editBackendUser = user
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (user.status == "active") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = if ((user.role_name ?: "") == "admin") Color(0xFFF57C00) else Color.Gray)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.display_name.ifBlank { user.email }, fontWeight = FontWeight.SemiBold)
                                Text(user.email, fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    "${roleLabel} • ${Strings.localizeStatus(user.status)}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                if (user.must_change_password) {
                                    Text(Strings.passwordChangeRequiredBadge, fontSize = 11.sp, color = Color(0xFFD84315))
                                }
                                if (user.user_permission_overrides.isNotEmpty()) {
                                    Text(Strings.customPermissionsActive, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            !state.backendUsersError.isNullOrBlank() -> {
                Text(state.backendUsersError!!, color = Color.Red, fontSize = 12.sp)
            }
            else -> {
                Text(Strings.backendUsersEmpty, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun BackendUserEditDialog(
    user: BackendUser,
    roles: List<BackendRole>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, String, Map<String, Boolean>, (Boolean, String?) -> Unit) -> Unit,
    onDelete: (((Boolean, String?) -> Unit) -> Unit),
    onResetPassword: (((Boolean, String?) -> Unit) -> Unit)
) {
    var name by remember(user.id) { mutableStateOf(user.display_name) }
    var phone by remember(user.id) { mutableStateOf(user.phone.orEmpty()) }
    val roleOptions = if (roles.isNotEmpty()) roles else listOf("admin", "manager", "worker", "assistant", "viewer").map { BackendRole(role_name = it) }
    var role by remember(user.id) { mutableStateOf(user.role_name ?: roleOptions.firstOrNull()?.role_name ?: "worker") }
    var status by remember(user.id) { mutableStateOf(if (user.status == "inactive") "inactive" else "active") }
    var roleExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val statuses = listOf("active", "inactive")
    val permissionCatalog = remember(roleOptions, user.id) {
        val fromRoles = roleOptions.flatMap { it.permission_details }.associateBy { it.permission_code }.values.toList()
        if (fromRoles.isNotEmpty()) {
            fromRoles.sortedWith(compareBy<BackendPermission> { it.module_name }.thenBy { it.permission_code })
        } else {
            user.permissions.keys.sorted().map { BackendPermission(permission_code = it, name = it) }
        }
    }
    fun permissionsForRole(roleName: String): Map<String, Boolean> {
        val selected = roleOptions.firstOrNull { it.role_name == roleName }
        val source = when {
            selected != null && selected.permissions.isNotEmpty() -> selected.permissions
            roleName == user.role_name && user.role_permissions.isNotEmpty() -> user.role_permissions
            else -> emptyMap()
        }
        return if (permissionCatalog.isNotEmpty()) {
            permissionCatalog.associate { it.permission_code to (source[it.permission_code] ?: false) }
        } else {
            source
        }
    }
    var permissionValues by remember(user.id) {
        mutableStateOf(
            if (user.permissions.isNotEmpty()) {
                permissionCatalog.associate { it.permission_code to (user.permissions[it.permission_code] ?: false) }
            } else {
                permissionsForRole(role)
            }
        )
    }
    val roleDefaults = permissionsForRole(role)
    val overrideCount = permissionCatalog.count { permissionValues[it.permission_code] != roleDefaults[it.permission_code] }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("${Strings.edit}: ${user.display_name.ifBlank { user.email }}") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(Strings.nameField) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(user.email, {}, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = false)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it; error = null }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                SDrop(Strings.role, Strings.localizeRole(role), roleExpanded, { if (!submitting) roleExpanded = it }, roleOptions.map { Strings.localizeRole(it.role_name) }) {
                    role = roleOptions[it].role_name
                    permissionValues = permissionsForRole(role)
                    roleExpanded = false
                }
                Spacer(Modifier.height(8.dp))
                SDrop(Strings.status, Strings.localizeStatus(status), statusExpanded, { if (!submitting) statusExpanded = it }, statuses.map { Strings.localizeStatus(it) }) {
                    status = statuses[it]
                    statusExpanded = false
                }
                Spacer(Modifier.height(8.dp))
                Text(Strings.roleDescription, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(Strings.localizeRoleDescription(role, roleOptions.firstOrNull { it.role_name == role }?.description), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                if (user.must_change_password) {
                    Text(Strings.passwordChangeRequiredBadge, fontSize = 12.sp, color = Color(0xFFD84315))
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        submitting = true
                        error = null
                        onResetPassword { ok, msg ->
                            submitting = false
                            if (!ok) error = msg ?: Strings.passwordResetFailed
                        }
                    },
                    enabled = !submitting
                ) { Text(Strings.resetPasswordToDefault) }
                Spacer(Modifier.height(8.dp))
                Text(Strings.roleControlsPermissionsHint, fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { permissionValues = roleDefaults },
                    enabled = !submitting && permissionCatalog.isNotEmpty()
                ) { Text(Strings.resetToRoleDefaults) }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (overrideCount > 0) "${Strings.permissionOverrides}: $overrideCount" else Strings.permissions,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                }
                if (permissionCatalog.isNotEmpty()) {
                    items(permissionCatalog) { permission ->
                        val code = permission.permission_code
                        val currentValue = permissionValues[code] ?: false
                        val baseValue = roleDefaults[code] ?: false
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Strings.localizePermission(code, permission.name), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    val description = Strings.localizePermissionDescription(code, permission.description)
                                    if (description.isNotBlank()) {
                                        Text(description, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    if (currentValue != baseValue) {
                                        Text(Strings.customPermissionsActive, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Switch(
                                    checked = currentValue,
                                    onCheckedChange = {
                                        permissionValues = permissionValues.toMutableMap().apply { this[code] = it }
                                        error = null
                                    },
                                    enabled = !submitting
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    item {
                        Text(Strings.noPermissionsAssigned, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (error != null) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    onSave(name, phone.ifBlank { null }, role, status, permissionValues) { ok, msg ->
                        submitting = false
                        if (!ok) error = msg ?: Strings.updateUserFailed
                    }
                },
                enabled = !submitting && name.isNotBlank()
            ) { Text(if (submitting) Strings.processing else Strings.save) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        submitting = true
                        error = null
                        onDelete { ok, msg ->
                            submitting = false
                            if (!ok) error = msg ?: Strings.deleteUserFailed
                        }
                    },
                    enabled = !submitting
                ) { Text(Strings.delete, color = Color.Red) }
                TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) }
            }
        }
    )
}

@Composable private fun BackendUserCreateDialog(
    roles: List<BackendRole>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("manager") }
    var rE by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val roleOptions = if (roles.isNotEmpty()) roles else listOf("admin", "manager", "worker", "assistant", "viewer").map { BackendRole(role_name = it) }
    val selectedRole = roleOptions.firstOrNull { it.role_name == role }
    LaunchedEffect(roleOptions) {
        if (roleOptions.none { it.role_name == role }) role = roleOptions.first().role_name
    }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.createBackendUser) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                item {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(Strings.nameField) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(email, { email = it; error = null }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(8.dp))
                SDrop(Strings.role, Strings.localizeRole(role), rE, { if (!submitting) rE = it }, roleOptions.map { Strings.localizeRole(it.role_name) }) {
                    role = roleOptions[it].role_name
                    rE = false
                }
                Spacer(Modifier.height(8.dp))
                Text(Strings.defaultPasswordInfo, fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text(Strings.roleDescription, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(Strings.localizeRoleDescription(role, selectedRole?.description), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text(Strings.includedPermissions, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                BackendRolePermissionSummary(selectedRole)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color.Red, fontSize = 12.sp)
                }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    onCreate(name, email, role) { ok, msg ->
                        submitting = false
                        if (ok) onDismiss() else error = msg ?: Strings.createUserFailed
                    }
                },
                enabled = !submitting && name.isNotBlank() && email.isNotBlank()
            ) { Text(if (submitting) "${Strings.processing}" else Strings.create) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) } }
    )
}

@Composable private fun BackendRolePresetCard(role: BackendRole) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Strings.localizeRole(role.role_name), fontWeight = FontWeight.SemiBold)
            if (!role.description.isNullOrBlank()) {
                Text(Strings.localizeRoleDescription(role.role_name, role.description), fontSize = 12.sp, color = Color.Gray)
            }
            Text(Strings.includedPermissions, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            BackendRolePermissionSummary(role)
        }
    }
}

@Composable private fun BackendRolePermissionSummary(role: BackendRole?) {
    val permissionDetails = role?.permission_details.orEmpty()
    val permissionMap = role?.permissions.orEmpty()
    val labels = permissionDetails
        .filter { permissionMap[it.permission_code] == true }
        .map { Strings.localizePermission(it.permission_code, it.name) }
    if (labels.isEmpty()) {
        Text(Strings.noPermissionsAssigned, fontSize = 12.sp, color = Color.Gray)
    } else {
        Text(labels.joinToString(" • "), fontSize = 12.sp, color = Color.Gray)
    }
}

// 7. DATA
@Composable private fun DataSection(sm: SettingsManager, vm: SecretaryViewModel) {
    var exp by remember { mutableStateOf(false) }
    var showCl by remember { mutableStateOf(false) }; var showRs by remember { mutableStateOf(false) }
    if (showCl) AlertDialog(onDismissRequest = { showCl = false }, title = { Text(Strings.clearHistoryQuestion) }, confirmButton = { Button(onClick = { vm.clearHistory(); showCl = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(Strings.delete) } }, dismissButton = { TextButton(onClick = { showCl = false }) { Text(Strings.cancel) } })
    if (showRs) AlertDialog(onDismissRequest = { showRs = false }, title = { Text(Strings.restoreDefaultsQuestion) }, confirmButton = { Button(onClick = { vm.resetSettings(); showRs = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(Strings.restoreDefaults) } }, dismissButton = { TextButton(onClick = { showRs = false }) { Text(Strings.cancel) } })
    SCard(Strings.dataStorage, Icons.Default.AccountBox, exp, { exp = !exp }) {
        Button(onClick = { vm.exportCrmData() }, Modifier.fillMaxWidth()) { Text(Strings.exportCrmCsv) }
        Spacer(Modifier.height(8.dp))
        val ctx = LocalContext.current
        var syncResult by remember { mutableStateOf<String?>(null) }
        var filterUk by remember { mutableStateOf(true) }
        Text(Strings.importContactsFromPhone, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = filterUk, onCheckedChange = { filterUk = it })
            Spacer(Modifier.width(8.dp))
            Text(if (filterUk) Strings.onlyUkNumbers else Strings.allNumbers, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            vm.syncPhoneContacts(ctx, filterUk)
            syncResult = Strings.syncStarted
        }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text(Strings.importContacts) }
        if (syncResult != null) { Text(syncResult!!, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text(Strings.importDatabase, fontWeight = FontWeight.SemiBold)
        var path by remember { mutableStateOf(sm.importFilePath) }
        SField(Strings.csvPath, path, { path = it; sm.importFilePath = it })
        var tE by remember { mutableStateOf(false) }
        val tbls = listOf("clients", "properties", "jobs")
        SDrop(
            Strings.table,
            Strings.localizeCrmTab(sm.importTargetTable),
            tE,
            { tE = it },
            tbls.map { Strings.localizeCrmTab(it) }
        ) { sm.importTargetTable = tbls[it]; tE = false }
        var ai by remember { mutableStateOf(sm.autoImportEnabled) }
        SSwitch(Strings.autoImportOnStartup, null, ai) { ai = it; sm.autoImportEnabled = it }
        Button(onClick = { vm.triggerManualImport() }, Modifier.fillMaxWidth(), enabled = path.isNotBlank()) { Text(Strings.startImport) }
        Text(Strings.voiceImportHint, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showCl = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text(Strings.clearHistory) }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { showRs = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text(Strings.restoreDefaults) }
    }
}

// ============================================================
// 8a. PROFIL FIRMY (z /tenant/profile)
// ============================================================

@Composable private fun CompanyProfileSection(viewModel: SecretaryViewModel, sm: SettingsManager) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.tenantProfile
    val err = state.settingsLoadErrors["profile"]
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.companyProfile, Icons.Default.AccountCircle, exp, { exp = !exp }) {
        when {
            profile == null && err == null -> {
                Text("⏳ Načítání profilu firmy ze serveru...", color = Color.Gray, fontSize = 13.sp)
            }
            err != null && profile == null -> {
                Text("❌ Profil firmy se nepodařilo načíst: $err", color = Color.Red, fontSize = 13.sp)
            }
            profile != null -> {
                val found = profile["found"] as? Boolean ?: false
                if (!found) {
                    Text("⚠️ Profil firmy nebyl nalezen.", color = Color(0xFFFF9800), fontSize = 13.sp)
                    Text("   Onboarding ještě nebyl dokončen.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    // From tenants table
                    profile["tenant_name"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Tenant", it) }
                    // From crm.tenant_settings
                    profile["company_name"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Firma", it) }
                    profile["contact_name"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Kontakt", it) }
                    profile["phone"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Telefon", it) }
                    profile["currency"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Měna", it) }
                    profile["location"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Lokace", it) }
                    profile["industry"]?.toString()?.takeIf { it.isNotBlank() }?.let { ARow("Obor", it) }
                    Spacer(Modifier.height(4.dp))
                    // From tenant_operating_profile
                    profile["workspace_mode"]?.toString()?.let {
                        ARow(Strings.workspaceMode, Strings.localizeWorkspaceMode(it))
                    }
                    profile["internal_language_mode"]?.toString()?.let {
                        ARow(Strings.internalLanguageMode, Strings.localizeLanguageMode(it))
                    }
                    profile["customer_language_mode"]?.toString()?.let {
                        ARow(Strings.customerLanguageMode, Strings.localizeLanguageMode(it))
                    }
                    profile["default_internal_lang"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                        ARow(Strings.internalLanguage, Strings.languageDisplayName(it))
                    }
                    profile["default_customer_lang"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                        ARow(Strings.customerLanguage, Strings.languageDisplayName(it))
                    }
                    // Limits from subscription_limits
                    @Suppress("UNCHECKED_CAST")
                    val limits = profile["limits"] as? Map<String, Any?>
                    if (limits != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.limits, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        limits["max_users"]?.toString()?.let { ARow(Strings.maxUsers, it) }
                        limits["max_clients"]?.toString()?.let { ARow(Strings.maxClients, it) }
                        limits["max_jobs_per_month"]?.toString()?.let { ARow(Strings.maxJobsPerMonth, it) }
                        limits["max_voice_minutes"]?.toString()?.let { ARow(Strings.voiceMinutes, it) }
                    }
                }
                profile["error"]?.toString()?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("⚠️ $it", color = Color(0xFFFF9800), fontSize = 11.sp)
                }
            }
        }
    }
}

// ============================================================
// 8. O APLIKACI (pouziva VersionInfo)
// ============================================================

@Composable private fun AboutSection() {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.aboutApp, Icons.Default.Info, exp, { exp = !exp }) {
        Text(VersionInfo.APP_NAME, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(VersionInfo.appDescription(), fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        ARow(Strings.versionLabel, "${VersionInfo.VERSION_NAME} (build ${VersionInfo.VERSION_CODE})")
        ARow(Strings.releaseDate, VersionInfo.BUILD_DATE)
        ARow(Strings.packageLabel, VersionInfo.PACKAGE_NAME)
        Spacer(Modifier.height(12.dp))

        Text(Strings.authorAndCode, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        ARow(Strings.structureAndCode, VersionInfo.AUTHOR_NAME)
        ARow(Strings.email, VersionInfo.AUTHOR_EMAIL)
        ARow(Strings.phone, VersionInfo.AUTHOR_PHONE)
        ARow(Strings.webLabel, VersionInfo.AUTHOR_WEB)
        ARow(Strings.companyLabel, VersionInfo.COMPANY)

        val latest = VersionInfo.getLatestChanges()
        if (latest != null && latest.coder.isNotBlank()) {
            ARow(Strings.codingLabel, VersionInfo.localizeCoder(latest.coder))
        }
        Spacer(Modifier.height(12.dp))

        Text(Strings.technologies, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        ARow(Strings.platformLabel, VersionInfo.platformValue())
        ARow(Strings.backendLabel, VersionInfo.backendValue())
        ARow(Strings.architectureLabel, VersionInfo.architectureValue())
        ARow(Strings.aiEngineLabel, VersionInfo.aiEngineValue())
        ARow(Strings.databaseLabel, VersionInfo.dbEngineValue())
        ARow(Strings.minSdkLabel, "${VersionInfo.MIN_SDK}")
        ARow(Strings.targetSdkLabel, "${VersionInfo.TARGET_SDK}")
        Spacer(Modifier.height(12.dp))

        Text(Strings.projectStructureLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.PROJECT_STRUCTURE.forEach { ARow(it.filename, VersionInfo.localizeProjectDescription(it.filename, it.description)) }
        Spacer(Modifier.height(12.dp))

        Text(Strings.licenseTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(VersionInfo.licenseText(), fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
    }
}

// ============================================================
// 9. HISTORIE VERZI (audit log)
// ============================================================

@Composable private fun VersionHistorySection() {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.versionHistory, Icons.AutoMirrored.Filled.List, exp, { exp = !exp }) {
        // Pravidla verzovani
        Text(Strings.versioningRules, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.VERSIONING_RULES.forEach { rule ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(Strings.localizeVersionRuleType(rule.type), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(110.dp), color = when (rule.type) { "PATCH" -> Color(0xFF4CAF50); "MINOR" -> Color(0xFF2196F3); else -> Color(0xFFF44336) })
                Column { Text(rule.example, fontSize = 12.sp); Text(VersionInfo.localizeVersionRuleDescription(rule.type, rule.description), fontSize = 11.sp, color = Color.Gray) }
            }
        }

        Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        // Povinne kroky
        Text(Strings.mandatoryStepsOnChange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.MANDATORY_STEPS.forEach { step ->
            Text(VersionInfo.localizeMandatoryStep(step), fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 1.dp))
        }

        Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        // Changelog
        Text(Strings.changelogTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.CHANGELOG.forEach { entry ->
            val typeColor = when (entry.type) { ChangeType.PATCH -> Color(0xFF4CAF50); ChangeType.MINOR -> Color(0xFF2196F3); ChangeType.MAJOR -> Color(0xFFF44336) }
            val typeLabel = Strings.versionTypeLabel(entry.type)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("v${entry.version}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(typeLabel, fontSize = 10.sp, color = Color.White, modifier = Modifier.background(typeColor, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        Spacer(Modifier.weight(1f))
                        Text(entry.date, fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(VersionInfo.localizeChangelogSummary(entry.summary), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("${Strings.authorLabel}: ${entry.author}", fontSize = 11.sp, color = Color.Gray)
                    if (entry.coder.isNotBlank()) Text("${Strings.codingLabel}: ${VersionInfo.localizeCoder(entry.coder)}", fontSize = 11.sp, color = Color.Gray)

                    // Rozbalitelny detail
                    var showDetail by remember { mutableStateOf(false) }
                    TextButton(onClick = { showDetail = !showDetail }) { Text(if (showDetail) Strings.hideChanges() else Strings.showChanges(entry.changes.size), fontSize = 11.sp) }
                    if (showDetail) {
                        entry.changes.forEach { change -> Text("  * ${VersionInfo.localizeChangelogChange(change)}", fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) }
                        if (entry.knownIssues.isNotEmpty()) {
                            Text(Strings.knownIssuesLabel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 4.dp))
                            entry.knownIssues.forEach { issue -> Text("  ! ${VersionInfo.localizeKnownIssue(issue)}", fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ============================================================
// RUNTIME DATA STATUS
// ============================================================

private fun langDisplayName(code: String): String {
    val lc = code.lowercase()
    return when {
        lc.startsWith("en") -> "🇬🇧 English ($code)"
        lc.startsWith("cs") || lc.startsWith("cz") -> "🇨🇿 Čeština ($code)"
        lc.startsWith("pl") -> "🇵🇱 Polski ($code)"
        lc.startsWith("de") -> "🇩🇪 Deutsch ($code)"
        lc.startsWith("fr") -> "🇫🇷 Français ($code)"
        lc.startsWith("es") -> "🇪🇸 Español ($code)"
        lc.startsWith("it") -> "🇮🇹 Italiano ($code)"
        lc.startsWith("nl") -> "🇳🇱 Nederlands ($code)"
        lc.startsWith("pt") -> "🇵🇹 Português ($code)"
        lc.startsWith("ru") -> "🇷🇺 Русский ($code)"
        lc.startsWith("sk") -> "🇸🇰 Slovenčina ($code)"
        lc.startsWith("uk") -> "🇺🇦 Українська ($code)"
        else -> code.uppercase()
    }
}

@Composable private fun RuntimeDataStatusSection(viewModel: SecretaryViewModel, sm: SettingsManager) {
    val state by viewModel.uiState.collectAsState()
    val versionInfo = state.serverVersionInfo
    val versionErr = state.settingsLoadErrors["version"]
    val refreshMs = state.settingsLastRefreshMs
    var exp by remember { mutableStateOf(true) }
    val serverVersion = versionInfo?.get("server_version")?.toString()
    val latestMigration = versionInfo?.get("latest_migration")?.toString()
    val appliedAt = versionInfo?.get("applied_at")?.toString()
    val statusColor = when {
        versionInfo != null && versionErr == null -> Color(0xFF4CAF50)
        versionErr != null -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }
    SCard("🔧 Runtime Data Status", Icons.Default.Info, exp, { exp = !exp }) {
        ARow("APK", "${VersionInfo.VERSION_NAME} (build ${VersionInfo.VERSION_CODE})")
        ARow("Backend URL", sm.apiUrl.let { if (it.isBlank()) BuildConfig.BASE_URL else it })
        ARow("Tenant ID", "1")
        Spacer(Modifier.height(4.dp))
        // Server version from /version endpoint
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            val statusText = when {
                versionInfo != null && versionErr == null -> "✅ Server připojen"
                versionErr != null -> "❌ $versionErr"
                else -> "⏳ Načítám verzi serveru…"
            }
            Text(statusText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        if (serverVersion != null) ARow("Server verze", serverVersion)
        if (!latestMigration.isNullOrBlank()) ARow("Poslední migrace", latestMigration)
        if (!appliedAt.isNullOrBlank()) ARow("Aplikováno", appliedAt.take(19).replace("T", " "))
        if (refreshMs > 0L) {
            val sec = (System.currentTimeMillis() - refreshMs) / 1000
            Text("Poslední refresh: ${sec}s zpět", fontSize = 11.sp, color = Color.Gray)
        }
        // Quick status of other sections
        Spacer(Modifier.height(4.dp))
        ARow("Profil firmy", when {
            state.tenantProfile?.get("found") == true -> "✅ načteno"
            state.settingsLoadErrors.containsKey("profile") -> "❌ chyba"
            state.tenantProfile != null -> "⚠️ not found"
            else -> "⏳"
        })
        ARow("Jazyky", when {
            state.tenantLanguages?.get("found") == true -> "✅ načteno"
            state.settingsLoadErrors.containsKey("languages") -> "❌ chyba"
            state.tenantLanguages != null -> "⚠️ not set up"
            else -> "⏳"
        })
        Spacer(Modifier.height(4.dp))
        Button(onClick = { viewModel.loadSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text("🔄 Refresh settings ze serveru")
        }
    }
}

// ============================================================
// REUSABLE
// ============================================================

@Composable private fun SCard(title: String, icon: ImageVector, exp: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Column {
        Row(Modifier.fillMaxWidth().clickable { toggle() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(if (exp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray) }
        if (exp) { HorizontalDivider(Modifier.padding(horizontal = 16.dp)); Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
    } }
}

@Composable private fun SSwitch(l: String, d: String?, c: Boolean, f: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(l); if (d != null) Text(d, fontSize = 11.sp, color = Color.Gray) }; Switch(c, f) } }
@Composable private fun SField(l: String, v: String, f: (String) -> Unit, ph: String = "", kbt: KeyboardType = KeyboardType.Text) { OutlinedTextField(v, f, label = { Text(l) }, placeholder = { Text(ph, color = Color.LightGray) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = kbt)) }
@Composable private fun ARow(l: String, v: String) { Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { Text(l, Modifier.width(110.dp), fontSize = 12.sp, color = Color.Gray); Text(v, fontSize = 12.sp) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SDrop(l: String, cur: String, exp: Boolean, onExp: (Boolean) -> Unit, opts: List<String>, onSel: (Int) -> Unit) {
    ExposedDropdownMenuBox(exp, onExp) { OutlinedTextField(cur, {}, readOnly = true, label = { Text(l) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(exp, { onExp(false) }) { opts.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSel(i) }) } } }
}

@Composable private fun SSlider(l: String, v: Float, r: ClosedFloatingPointRange<Float>, s: Int, vl: String, f: (Float) -> Unit, done: () -> Unit) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(l); Text(vl, color = MaterialTheme.colorScheme.primary) }; Slider(v, f, valueRange = r, steps = s, onValueChangeFinished = done) }
}
