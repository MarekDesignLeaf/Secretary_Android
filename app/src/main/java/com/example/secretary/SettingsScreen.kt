package com.example.secretary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Text(Strings.settings, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item { CompanyProfileSection(viewModel) }
        item { RatesSection(viewModel) }
        item { LanguageSection(sm, viewModel) }
        item { ThemeSection(sm) }
        item { VoiceSection(sm) }
        item { ServerSection(sm, viewModel, state) }
        item { CrmSection(sm) }
        item { NotificationSection(sm) }
        item { WorkProfileSection(sm) }
        item { UsersSection(sm, viewModel) }
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
    var gardenRate by remember { mutableStateOf("27") }
    var hedgeRate by remember { mutableStateOf("31") }
    var arboristRate by remember { mutableStateOf("34") }
    var wasteBag by remember { mutableStateOf("55") }
    var minCharge by remember { mutableStateOf("150") }
    var loaded by remember { mutableStateOf(false) }
    fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    LaunchedEffect(Unit) {
        try {
            val sm = viewModel.getSettingsManager() ?: return@LaunchedEffect
            val token = sm.accessToken ?: return@LaunchedEffect
            val res = viewModel.api.getDefaultRates("Bearer $token", 1)
            if (res.isSuccessful) {
                val body = res.body() ?: return@LaunchedEffect
                fun r(key: String, def: Double) = fmt((body[key] as? Map<*,*>)?.get("rate")?.toString()?.toDoubleOrNull() ?: def)
                gardenRate = r("garden_maintenance", 27.0)
                hedgeRate = r("hedge_trimming", 31.0)
                arboristRate = r("arborist_works", 34.0)
                wasteBag = r("garden_waste_bulkbag", 55.0)
                minCharge = r("minimum_charge", 150.0)
                loaded = true
            }
        } catch (_: Exception) {}
    }
    SCard(Strings.serviceRates, Icons.Default.ShoppingCart, exp, { exp = !exp }) {
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
            viewModel.updateDefaultRates(mapOf(
                "garden_maintenance" to (gardenRate.toDoubleOrNull() ?: 27.0),
                "hedge_trimming" to (hedgeRate.toDoubleOrNull() ?: 31.0),
                "arborist_works" to (arboristRate.toDoubleOrNull() ?: 34.0),
                "hourly_rate" to (gardenRate.toDoubleOrNull() ?: 27.0),
                "garden_waste_bulkbag" to (wasteBag.toDoubleOrNull() ?: 55.0),
                "minimum_charge" to (minCharge.toDoubleOrNull() ?: 150.0)
            ))
        }, modifier = Modifier.fillMaxWidth()) { Text(Strings.save) }
    }
}

// 0. JAZYK / LANGUAGE
@Composable private fun LanguageSection(sm: SettingsManager, viewModel: SecretaryViewModel) {
    var exp by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf<String?>(null) }
    val currentLang = sm.appLanguage
    val langName = Strings.languageDisplayName(currentLang)
    SCard(Strings.language + ": $langName", Icons.Default.Star, exp, { exp = !exp }) {
        val langs = listOf("en" to "🇬🇧 English", "cs" to "🇨🇿 Čeština", "pl" to "🇵🇱 Polski")
        langs.forEach { (code, label) ->
            val selected = currentLang == code
            Row(Modifier.fillMaxWidth().clickable { if (!selected) showConfirm = code }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = { if (!selected) showConfirm = code })
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
            }
        }
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
                Button(onClick = { sm.activationWord = word; wordChanged = false }) { Text(Strings.save) }
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
    SCard("CRM", Icons.Default.Person, exp, { exp = !exp }) {
        var rE by remember { mutableStateOf(false) }; val ints = listOf(0 to "Manualne", 5 to "5 min", 15 to "15 min", 30 to "30 min")
        SDrop("Auto refresh", ints.first { it.first == sm.autoRefreshInterval }.second, rE, { rE = it }, ints.map { it.second }) { sm.autoRefreshInterval = ints[it].first; rE = false }
        var tE by remember { mutableStateOf(false) }; val tabs = listOf("Klienti", "Nemovitosti", "Zakazky", "Odpady", "Finance")
        SDrop("Vychozi tab", tabs[sm.defaultCrmTab], tE, { tE = it }, tabs) { sm.defaultCrmTab = it; tE = false }
        var sE by remember { mutableStateOf(false) }; val sorts = listOf("name" to "Jmeno", "created" to "Datum", "activity" to "Aktivita")
        SDrop("Razeni", sorts.first { it.first == sm.clientSortOrder }.second, sE, { sE = it }, sorts.map { it.second }) { sm.clientSortOrder = sorts[it].first; sE = false }
    }
}

// 4. NOTIFIKACE
@Composable private fun NotificationSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.notificationsLabel, Icons.Default.Notifications, exp, { exp = !exp }) {
        var p by remember { mutableStateOf(sm.persistentNotification) }
        SSwitch(Strings.persistentNotificationLabel, null, p) { p = it; sm.persistentNotification = it }
        var rE by remember { mutableStateOf(false) }; val rems = listOf(0 to "Vyp", 5 to "5m", 15 to "15m", 30 to "30m", 60 to "1h")
        SDrop(Strings.taskReminder, rems.first { it.first == sm.reminderMinutes }.second, rE, { rE = it }, rems.map { it.second }) { sm.reminderMinutes = rems[it].first; rE = false }
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
        var pE by remember { mutableStateOf(false) }; val prios = listOf("low" to "Nizka", "normal" to "Normalni", "high" to "Vysoka", "urgent" to "Urgentni")
        SDrop(Strings.defaultPriorityLabel, prios.first { it.first == sm.defaultTaskPriority }.second, pE, { pE = it }, prios.map { it.second }) { sm.defaultTaskPriority = prios[it].first; pE = false }

        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        Text(Strings.emailSignatures, fontWeight = FontWeight.SemiBold)
        var sigs by remember { mutableStateOf(sm.getSavedSignatures()) }
        var actId by remember { mutableStateOf(sm.activeSignatureId.ifBlank { sigs.firstOrNull()?.id ?: "" }) }
        var content by remember { mutableStateOf(sigs.find { it.id == actId }?.content ?: sm.emailSignature) }
        var name by remember { mutableStateOf(sigs.find { it.id == actId }?.name ?: "Podpis") }
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
    var showPwd by remember { mutableStateOf(false) }
    var showLocalUser by remember { mutableStateOf(false) }
    var showCreateBackendUser by remember { mutableStateOf(false) }
    var editLocalUser by remember { mutableStateOf<UserProfile?>(null) }
    var editBackendUser by remember { mutableStateOf<BackendUser?>(null) }
    var profiles by remember { mutableStateOf(sm.getUserProfiles()) }
    var authed by remember { mutableStateOf(sm.adminPasswordHash.isBlank()) }
    var userFeedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val roleNames = state.backendRoles.map { it.role_name }.filter { it.isNotBlank() }
    val availableRoles = if (roleNames.isNotEmpty()) roleNames else listOf("admin", "manager", "worker", "assistant", "viewer")

    LaunchedEffect(exp) {
        if (exp) {
            viewModel.loadBackendRoles()
            viewModel.loadBackendUsers()
        }
    }

    if (showPwd) PasswordDialog({ showPwd = false }, { o, n -> if (sm.verifyPassword(o)) { sm.setAdminPassword(n); showPwd = false } }, sm.adminPasswordHash.isNotBlank())
    if (showCreateBackendUser) BackendUserCreateDialog(availableRoles, { showCreateBackendUser = false }, { name, email, password, role, onResult ->
        viewModel.createBackendUser(email, password, name, role) { ok, msg ->
            userFeedback = (if (ok) Strings.backendUserCreated else (msg ?: Strings.createUserFailed)) to ok
            onResult(ok, msg)
        }
    })
    editBackendUser?.let { backendUser ->
        BackendUserEditDialog(
            user = backendUser,
            roles = availableRoles,
            onDismiss = { editBackendUser = null },
            onSave = { name, phone, role, status, onResult ->
                viewModel.updateBackendUser(backendUser.id, name, phone, role, status) { ok, msg ->
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
            }
        )
    }
    if (showLocalUser) LocalUserProfileDialog(editLocalUser, { showLocalUser = false; editLocalUser = null }, { u ->
        profiles = if (editLocalUser != null) profiles.map { if (it.id == u.id) u else it } else profiles + u
        sm.saveUserProfiles(profiles)
        showLocalUser = false
        editLocalUser = null
    },
        if (editLocalUser != null && editLocalUser?.id != "admin") {
            {
                profiles = profiles.filter { it.id != editLocalUser?.id }
                sm.saveUserProfiles(profiles)
                showLocalUser = false
                editLocalUser = null
            }
        } else null)

    SCard(Strings.usersAndPermissions, Icons.Default.Lock, exp, { exp = !exp }) {
        Text(Strings.backendUsersLabel, fontWeight = FontWeight.SemiBold)
        Text(Strings.backendUserHint, fontSize = 12.sp, color = Color.Gray)
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

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(Strings.localProfilesLabel, fontWeight = FontWeight.SemiBold)
        Text(Strings.localProfilesHint, fontSize = 12.sp, color = Color.Gray)
        if (!authed && sm.adminPasswordHash.isNotBlank()) {
            var pwd by remember { mutableStateOf("") }
            OutlinedTextField(pwd, { pwd = it }, label = { Text(Strings.password) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { if (sm.verifyPassword(pwd)) authed = true }, Modifier.fillMaxWidth()) { Text(Strings.verify) }
        } else {
            OutlinedButton(onClick = { showPwd = true }, Modifier.fillMaxWidth()) { Text(if (sm.adminPasswordHash.isBlank()) Strings.setAdminPassword else Strings.changePassword) }
            profiles.forEach { u ->
                val rl = Strings.localizeRole(u.role)
                Card(
                    Modifier.fillMaxWidth().clickable { editLocalUser = u; showLocalUser = true },
                    colors = CardDefaults.cardColors(containerColor = if (u.id == sm.activeUserId) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = if (u.role == "admin") Color(0xFFF57C00) else Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontWeight = FontWeight.SemiBold)
                            Text(rl, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (u.id == sm.activeUserId) {
                            Text(Strings.active, fontSize = 11.sp, color = Color(0xFF1976D2))
                        } else {
                            TextButton(onClick = { sm.activeUserId = u.id; profiles = sm.getUserProfiles() }) { Text(Strings.switchUser, fontSize = 11.sp) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { editLocalUser = null; showLocalUser = true }, Modifier.fillMaxWidth()) { Text("+ ${Strings.addLocalProfile}") }
        }
    }
}

@Composable private fun PasswordDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit, hasOld: Boolean) {
    var o by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (hasOld) Strings.changePassword else Strings.setAdminPassword) }, text = { Column {
        if (hasOld) { OutlinedTextField(o, { o = it }, label = { Text(Strings.currentPassword) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp)) }
        OutlinedTextField(n1, { n1 = it }, label = { Text(Strings.newPassword) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
        OutlinedTextField(n2, { n2 = it }, label = { Text(Strings.confirmPassword) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (n1.isNotBlank() && n2.isNotBlank() && n1 != n2) Text(Strings.passwordsDoNotMatch, color = Color.Red, fontSize = 12.sp)
    } }, confirmButton = { Button(onClick = { onOk(o, n1) }, enabled = n1.isNotBlank() && n1 == n2) { Text(Strings.save) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

@Composable private fun LocalUserProfileDialog(user: UserProfile?, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(user?.name ?: "") }; var role by remember { mutableStateOf(user?.role ?: "worker") }
    var perms by remember { mutableStateOf(user?.permissions ?: UserProfile.defaultPermissions("worker")) }; var rE by remember { mutableStateOf(false) }
    val roles = listOf("admin" to Strings.localizeRole("admin"), "manager" to Strings.localizeRole("manager"), "worker" to Strings.localizeRole("worker"), "viewer" to Strings.localizeRole("viewer"))
    val pl = mapOf("crm_read" to "CRM cteni", "crm_write" to "CRM zapis", "crm_delete" to "CRM mazani", "calendar_read" to "Kalendar cteni", "calendar_write" to "Kalendar zapis",
        "contacts_read" to "Kontakty cteni", "contacts_write" to "Kontakty zapis", "voice_commands" to "Hlasove prikazy", "settings_access" to "Nastaveni", "import_data" to "Import", "export_data" to "Export", "manage_users" to "Sprava uzivatelu")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (user == null) Strings.createLocalProfile else "${Strings.editLocalProfile}: ${user.name}") }, text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
        item { OutlinedTextField(name, { name = it }, label = { Text(Strings.nameField) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp)) }
        item { SDrop(Strings.role, roles.first { it.first == role }.second, rE, { rE = it }, roles.map { it.second }) { role = roles[it].first; perms = UserProfile.defaultPermissions(role); rE = false }; Spacer(Modifier.height(8.dp)); Text(Strings.permissions, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        pl.forEach { (k, l) -> item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f), fontSize = 13.sp); Switch(perms[k] ?: false, { perms = perms.toMutableMap().apply { this[k] = it } }) } } }
    } }, confirmButton = { Button(onClick = { onSave(UserProfile(id = user?.id ?: java.util.UUID.randomUUID().toString(), name = name, role = role, passwordHash = user?.passwordHash ?: "", permissions = perms)) }, enabled = name.isNotBlank()) { Text(Strings.save) } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text(Strings.delete, color = Color.Red) }; TextButton(onClick = onDismiss) { Text(Strings.cancel) } } })
}

@Composable private fun BackendUserEditDialog(
    user: BackendUser,
    roles: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, String, (Boolean, String?) -> Unit) -> Unit,
    onDelete: (((Boolean, String?) -> Unit) -> Unit)
) {
    var name by remember(user.id) { mutableStateOf(user.display_name) }
    var phone by remember(user.id) { mutableStateOf(user.phone.orEmpty()) }
    var role by remember(user.id) { mutableStateOf(user.role_name ?: roles.firstOrNull() ?: "worker") }
    var status by remember(user.id) { mutableStateOf(if (user.status == "inactive") "inactive" else "active") }
    var roleExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val roleOptions = if (roles.isNotEmpty()) roles else listOf("admin", "manager", "worker", "assistant", "viewer")
    val statuses = listOf("active", "inactive")

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("${Strings.edit}: ${user.display_name.ifBlank { user.email }}") },
        text = {
            Column {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(Strings.nameField) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(user.email, {}, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = false)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it; error = null }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                SDrop(Strings.role, Strings.localizeRole(role), roleExpanded, { if (!submitting) roleExpanded = it }, roleOptions.map { Strings.localizeRole(it) }) {
                    role = roleOptions[it]
                    roleExpanded = false
                }
                Spacer(Modifier.height(8.dp))
                SDrop(Strings.status, Strings.localizeStatus(status), statusExpanded, { if (!submitting) statusExpanded = it }, statuses.map { Strings.localizeStatus(it) }) {
                    status = statuses[it]
                    statusExpanded = false
                }
                Text(Strings.roleControlsPermissionsHint, fontSize = 12.sp, color = Color.Gray)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    onSave(name, phone.ifBlank { null }, role, status) { ok, msg ->
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
    roles: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("manager") }
    var rE by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val roleOptions = if (roles.isNotEmpty()) roles else listOf("admin", "manager", "worker", "assistant", "viewer")
    LaunchedEffect(roleOptions) {
        if (role !in roleOptions) role = roleOptions.first()
    }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.createBackendUser) },
        text = {
            Column {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(Strings.nameField) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(email, { email = it; error = null }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it; error = null }, label = { Text(Strings.password) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                Spacer(Modifier.height(8.dp))
                SDrop(Strings.role, Strings.localizeRole(role), rE, { if (!submitting) rE = it }, roleOptions.map { Strings.localizeRole(it) }) {
                    role = roleOptions[it]
                    rE = false
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    onCreate(name, email, password, role) { ok, msg ->
                        submitting = false
                        if (ok) onDismiss() else error = msg ?: Strings.createUserFailed
                    }
                },
                enabled = !submitting && name.isNotBlank() && email.isNotBlank() && password.isNotBlank()
            ) { Text(if (submitting) "${Strings.processing}" else Strings.create) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) } }
    )
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
        var tE by remember { mutableStateOf(false) }; val tbls = listOf("clients" to "Klienti", "properties" to "Nemovitosti", "jobs" to "Zakazky")
        SDrop(Strings.table, tbls.first { it.first == sm.importTargetTable }.second, tE, { tE = it }, tbls.map { it.second }) { sm.importTargetTable = tbls[it].first; tE = false }
        var ai by remember { mutableStateOf(sm.autoImportEnabled) }
        SSwitch("Auto import pri spusteni", null, ai) { ai = it; sm.autoImportEnabled = it }
        Button(onClick = { vm.triggerManualImport() }, Modifier.fillMaxWidth(), enabled = path.isNotBlank()) { Text(Strings.startImport) }
        Text(Strings.voiceImportHint, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showCl = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text(Strings.clearHistory) }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { showRs = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text(Strings.restoreDefaults) }
    }
}

// ============================================================
// 8a. PROFIL FIRMY (z tenant config)
// ============================================================

@Composable private fun CompanyProfileSection(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val config = state.tenantConfig ?: return
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.companyProfile, Icons.Default.AccountCircle, exp, { exp = !exp }) {
        val wsMode = config["workspace_mode"]?.toString() ?: "-"
        val wsLabel = Strings.localizeWorkspaceMode(wsMode)
        ARow(Strings.workspaceMode, wsLabel)
        ARow(Strings.internalLanguage, config["default_internal_lang"]?.toString()?.uppercase() ?: "-")
        ARow(Strings.customerLanguage, config["default_customer_lang"]?.toString()?.uppercase() ?: "-")
        ARow(Strings.internalLanguageMode, config["internal_language_mode"]?.toString() ?: "-")
        ARow(Strings.customerLanguageMode, config["customer_language_mode"]?.toString() ?: "-")
        @Suppress("UNCHECKED_CAST")
        val limits = config["limits"] as? Map<String, Any?>
        if (limits != null) {
            Spacer(Modifier.height(8.dp))
            Text(Strings.limits, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            ARow(Strings.maxUsers, limits["max_users"]?.toString() ?: "-")
            ARow(Strings.maxClients, limits["max_clients"]?.toString() ?: "-")
            ARow(Strings.maxJobsPerMonth, limits["max_jobs_per_month"]?.toString() ?: "-")
            ARow(Strings.voiceMinutes, limits["max_voice_minutes"]?.toString() ?: "-")
        }
        @Suppress("UNCHECKED_CAST")
        val warnings = config["warnings"] as? List<String>
        if (!warnings.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            warnings.forEach { Text(it, color = Color.Red, fontSize = 12.sp) }
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
        Text(VersionInfo.APP_DESCRIPTION, fontSize = 13.sp, color = Color.Gray)
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
            ARow(Strings.codingLabel, latest.coder)
        }
        Spacer(Modifier.height(12.dp))

        Text(Strings.technologies, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        ARow(Strings.platformLabel, VersionInfo.PLATFORM)
        ARow("Backend", VersionInfo.BACKEND)
        ARow(Strings.architectureLabel, VersionInfo.ARCHITECTURE)
        ARow("AI engine", VersionInfo.AI_ENGINE)
        ARow(Strings.databaseLabel, VersionInfo.DB_ENGINE)
        ARow("Min SDK", "${VersionInfo.MIN_SDK}")
        ARow("Target SDK", "${VersionInfo.TARGET_SDK}")
        Spacer(Modifier.height(12.dp))

        Text(Strings.projectStructureLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.PROJECT_STRUCTURE.forEach { ARow(it.filename, it.description) }
        Spacer(Modifier.height(12.dp))

        Text(Strings.licenseTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(VersionInfo.LICENSE, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
    }
}

// ============================================================
// 9. HISTORIE VERZI (audit log)
// ============================================================

@Composable private fun VersionHistorySection() {
    var exp by remember { mutableStateOf(false) }
    SCard(Strings.versionHistory, Icons.Default.List, exp, { exp = !exp }) {
        // Pravidla verzovani
        Text(Strings.versioningRules, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.VERSIONING_RULES.forEach { rule ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(rule.type, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(60.dp), color = when (rule.type) { "PATCH" -> Color(0xFF4CAF50); "MINOR" -> Color(0xFF2196F3); else -> Color(0xFFF44336) })
                Column { Text(rule.example, fontSize = 12.sp); Text(rule.description, fontSize = 11.sp, color = Color.Gray) }
            }
        }

        Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        // Povinne kroky
        Text(Strings.mandatoryStepsOnChange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.MANDATORY_STEPS.forEach { step ->
            Text(step, fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 1.dp))
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
                    Text(entry.summary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("${Strings.authorLabel}: ${entry.author}", fontSize = 11.sp, color = Color.Gray)
                    if (entry.coder.isNotBlank()) Text("${Strings.codingLabel}: ${entry.coder}", fontSize = 11.sp, color = Color.Gray)

                    // Rozbalitelny detail
                    var showDetail by remember { mutableStateOf(false) }
                    TextButton(onClick = { showDetail = !showDetail }) { Text(if (showDetail) Strings.hideChanges() else Strings.showChanges(entry.changes.size), fontSize = 11.sp) }
                    if (showDetail) {
                        entry.changes.forEach { change -> Text("  * $change", fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) }
                        if (entry.knownIssues.isNotEmpty()) {
                            Text(Strings.knownIssuesLabel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 4.dp))
                            entry.knownIssues.forEach { issue -> Text("  ! $issue", fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
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
