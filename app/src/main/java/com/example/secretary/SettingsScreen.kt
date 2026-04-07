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
        item { UsersSection(sm) }
        item { DataSection(sm, viewModel) }
        item { AboutSection() }
        item { VersionHistorySection() }
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) { Text("Odhlásit se") }
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
    SCard("Sazby služeb", Icons.Default.ShoppingCart, exp, { exp = !exp }) {
        Text("Hodinové sazby podle typu práce", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = gardenRate, onValueChange = { gardenRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("🌿 Garden Maintenance (£/h)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Cleaning, weeding, planting, grass strimming", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = hedgeRate, onValueChange = { hedgeRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("🌳 Hedge Trimming & Pruning (£/h)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = arboristRate, onValueChange = { arboristRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("🪓 Arboristic Works / Tree Surgeon (£/h)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        Text("Ostatní sazby", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = wasteBag, onValueChange = { wasteBag = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Odvoz odpadu za bulk bag (£)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = minCharge, onValueChange = { minCharge = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Minimální cena zakázky (£)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
    val langName = when(currentLang) { "cs" -> "Čeština"; "pl" -> "Polski"; else -> "English" }
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
        val newLangName = when(showConfirm) { "cs" -> "Čeština"; "pl" -> "Polski"; else -> "English" }
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
    SCard("Motiv aplikace", Icons.Default.Settings, exp, { exp = !exp }) {
        val options = listOf("system" to "Podle systemu", "light" to "Svetly", "dark" to "Tmavy")
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
        Text("Zmena se projevi po restartu aplikace", fontSize = 11.sp, color = Color.Gray)
    }
}

// 1. HLASOVE OVLADANI
@Composable private fun VoiceSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(true) }
    SCard("Hlasove ovladani", Icons.Default.Call, exp, { exp = !exp }) {
        var hw by remember { mutableStateOf(sm.hotwordEnabled) }
        SSwitch("Detekce aktivacniho slova", "Nasloucha na hot word", hw) { hw = it; sm.hotwordEnabled = it }
        var word by remember { mutableStateOf(sm.activationWord) }
        var wordChanged by remember { mutableStateOf(false) }
        SField("Aktivacni slovo", word, { word = it; wordChanged = (it != sm.activationWord) })
        if (wordChanged) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { word = sm.activationWord; wordChanged = false }) { Text("Zrusit") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { sm.activationWord = word; wordChanged = false }) { Text("Ulozit") }
            }
        }
        var rate by remember { mutableFloatStateOf(sm.ttsRate) }
        SSlider("Rychlost reci", rate, 0.5f..2.0f, 5, "%.1fx".format(rate), { rate = it }) { sm.ttsRate = rate }
        var pitch by remember { mutableFloatStateOf(sm.ttsPitch) }
        SSlider("Vyska hlasu", pitch, 0.5f..2.0f, 5, "%.1fx".format(pitch), { pitch = it }) { sm.ttsPitch = pitch }
        var sil by remember { mutableFloatStateOf(sm.silenceLength.toFloat()) }
        SSlider("Delka ticha", sil, 1500f..10000f, 7, "%.1fs".format(sil / 1000), { sil = it }) { sm.silenceLength = sil.toLong() }
    }
}

// 2. SERVER
@Composable private fun ServerSection(sm: SettingsManager, vm: SecretaryViewModel, state: UiState) {
    var exp by remember { mutableStateOf(false) }
    SCard("Server a pripojeni", Icons.Default.Info, exp, { exp = !exp }) {
        var url by remember { mutableStateOf(sm.apiUrl) }
        SField("URL API serveru", url, { url = it; sm.apiUrl = it })
        val cs = state.connectionStatus
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when (cs) {
            ConnectionStatus.CONNECTED -> Color(0xFFE8F5E9); ConnectionStatus.TESTING -> Color(0xFFFFF8E1)
            ConnectionStatus.DISCONNECTED -> Color(0xFFFFEBEE); ConnectionStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
        })) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(when (cs) {
                ConnectionStatus.CONNECTED -> Color(0xFF4CAF50); ConnectionStatus.TESTING -> Color(0xFFFFC107)
                ConnectionStatus.DISCONNECTED -> Color(0xFFF44336); ConnectionStatus.UNKNOWN -> Color.Gray
            })); Spacer(Modifier.width(12.dp))
            Column { Text(when (cs) { ConnectionStatus.CONNECTED -> "Pripojeno"; ConnectionStatus.TESTING -> "Testuji..."
                ConnectionStatus.DISCONNECTED -> "Server nedostupny"; ConnectionStatus.UNKNOWN -> "Neznam" }, fontWeight = FontWeight.SemiBold)
                if (cs == ConnectionStatus.CONNECTED) { val v = state.systemSettings["version"]?.toString() ?: ""; if (v.isNotBlank()) Text("Server: $v", fontSize = 12.sp, color = Color.Gray) }
                if (cs == ConnectionStatus.DISCONNECTED) Text("Zkontrolujte URL a ze server bezi", fontSize = 12.sp, color = Color(0xFFF44336))
            }
        } }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.testConnection() }, Modifier.fillMaxWidth(), enabled = cs != ConnectionStatus.TESTING) {
            if (cs == ConnectionStatus.TESTING) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text("Testovat spojeni")
        }
        var off by remember { mutableStateOf(sm.offlineMode) }
        SSwitch("Offline mod", "Fronta prikazu az do pripojeni", off) { off = it; sm.offlineMode = it }
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
    SCard("Notifikace", Icons.Default.Notifications, exp, { exp = !exp }) {
        var p by remember { mutableStateOf(sm.persistentNotification) }
        SSwitch("Trvala notifikace", null, p) { p = it; sm.persistentNotification = it }
        var rE by remember { mutableStateOf(false) }; val rems = listOf(0 to "Vyp", 5 to "5m", 15 to "15m", 30 to "30m", 60 to "1h")
        SDrop("Pripomenuti ukolu", rems.first { it.first == sm.reminderMinutes }.second, rE, { rE = it }, rems.map { it.second }) { sm.reminderMinutes = rems[it].first; rE = false }
    }
}

// 5. PRACOVNI PROFIL + PODPISY
@Composable private fun WorkProfileSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    SCard("Pracovni profil", Icons.Default.DateRange, exp, { exp = !exp }) {
        var wh by remember { mutableStateOf(sm.workHoursEnabled) }
        SSwitch("Pracovni hodiny", "Hotword jen v pracovni dobe", wh) { wh = it; sm.workHoursEnabled = it }
        var st by remember { mutableStateOf(sm.workHoursStart) }
        SField("Zacatek", st, { st = it; sm.workHoursStart = it }, kbt = KeyboardType.Number)
        var en by remember { mutableStateOf(sm.workHoursEnd) }
        SField("Konec", en, { en = it; sm.workHoursEnd = it }, kbt = KeyboardType.Number)
        var pE by remember { mutableStateOf(false) }; val prios = listOf("low" to "Nizka", "normal" to "Normalni", "high" to "Vysoka", "urgent" to "Urgentni")
        SDrop("Vychozi priorita", prios.first { it.first == sm.defaultTaskPriority }.second, pE, { pE = it }, prios.map { it.second }) { sm.defaultTaskPriority = prios[it].first; pE = false }

        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        Text("Emailove podpisy", fontWeight = FontWeight.SemiBold)
        var sigs by remember { mutableStateOf(sm.getSavedSignatures()) }
        var actId by remember { mutableStateOf(sm.activeSignatureId.ifBlank { sigs.firstOrNull()?.id ?: "" }) }
        var content by remember { mutableStateOf(sigs.find { it.id == actId }?.content ?: sm.emailSignature) }
        var name by remember { mutableStateOf(sigs.find { it.id == actId }?.name ?: "Podpis") }
        var dirty by remember { mutableStateOf(false) }

        var slE by remember { mutableStateOf(false) }
        SDrop("Aktivni podpis", name, slE, { slE = it }, sigs.map { it.name }) { i ->
            val s = sigs[i]; actId = s.id; content = s.content; name = s.name; dirty = false
            sm.activeSignatureId = s.id; sm.emailSignature = s.content; slE = false
        }
        OutlinedTextField(name, { name = it; dirty = true }, label = { Text("Nazev") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(content, { content = it; dirty = true }, label = { Text("Obsah podpisu") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), maxLines = 10, singleLine = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { sigs = sigs.map { if (it.id == actId) it.copy(name = name, content = content) else it }; sm.saveSignatures(sigs); sm.emailSignature = content; dirty = false }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Ulozit") }
            OutlinedButton(onClick = { val o = sigs.find { it.id == actId }; content = o?.content ?: ""; name = o?.name ?: ""; dirty = false }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Zrusit") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { val n = SavedSignature(name = "Podpis ${sigs.size + 1}"); sigs = sigs + n; sm.saveSignatures(sigs); actId = n.id; content = ""; name = n.name; dirty = false; sm.activeSignatureId = n.id }, modifier = Modifier.weight(1f)) { Text("+ Novy") }
            if (sigs.size > 1) OutlinedButton(onClick = { sigs = sigs.filter { it.id != actId }; sm.saveSignatures(sigs); val f = sigs.first(); actId = f.id; content = f.content; name = f.name; sm.activeSignatureId = f.id; sm.emailSignature = f.content; dirty = false },
                modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Smazat") }
        }
    }
}

// 6. UZIVATELE A PRAVA
@Composable private fun UsersSection(sm: SettingsManager) {
    var exp by remember { mutableStateOf(false) }
    var showPwd by remember { mutableStateOf(false) }
    var showUser by remember { mutableStateOf(false) }
    var editUser by remember { mutableStateOf<UserProfile?>(null) }
    var profiles by remember { mutableStateOf(sm.getUserProfiles()) }
    var authed by remember { mutableStateOf(sm.adminPasswordHash.isBlank()) }

    if (showPwd) PasswordDialog({ showPwd = false }, { o, n -> if (sm.verifyPassword(o)) { sm.setAdminPassword(n); showPwd = false } }, sm.adminPasswordHash.isNotBlank())
    if (showUser) UserEditDialog(editUser, { showUser = false; editUser = null }, { u -> profiles = if (editUser != null) profiles.map { if (it.id == u.id) u else it } else profiles + u; sm.saveUserProfiles(profiles); showUser = false; editUser = null },
        if (editUser != null && editUser?.id != "admin") { { profiles = profiles.filter { it.id != editUser?.id }; sm.saveUserProfiles(profiles); showUser = false; editUser = null } } else null)

    SCard("Uzivatele a prava", Icons.Default.Lock, exp, { exp = !exp }) {
        if (!authed && sm.adminPasswordHash.isNotBlank()) {
            var pwd by remember { mutableStateOf("") }
            Text("Zadejte heslo spravce", color = Color.Gray)
            OutlinedTextField(pwd, { pwd = it }, label = { Text("Heslo") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { if (sm.verifyPassword(pwd)) authed = true }, Modifier.fillMaxWidth()) { Text("Overit") }
        } else {
            OutlinedButton(onClick = { showPwd = true }, Modifier.fillMaxWidth()) { Text(if (sm.adminPasswordHash.isBlank()) "Nastavit heslo spravce" else "Zmenit heslo") }
            Spacer(Modifier.height(8.dp)); Text("Uzivatele", fontWeight = FontWeight.SemiBold)
            profiles.forEach { u -> val rl = when (u.role) { "admin" -> "Spravce"; "manager" -> "Manazer"; "worker" -> "Pracovnik"; else -> "Nahled" }
                Card(Modifier.fillMaxWidth().clickable { editUser = u; showUser = true }, colors = CardDefaults.cardColors(containerColor = if (u.id == sm.activeUserId) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Person, null, tint = if (u.role == "admin") Color(0xFFF57C00) else Color.Gray); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(u.name, fontWeight = FontWeight.SemiBold); Text(rl, fontSize = 12.sp, color = Color.Gray) }
                        if (u.id == sm.activeUserId) Text("Aktivni", fontSize = 11.sp, color = Color(0xFF1976D2))
                        else TextButton(onClick = { sm.activeUserId = u.id; profiles = sm.getUserProfiles() }) { Text("Prepnout", fontSize = 11.sp) }
                    }
                }; Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { editUser = null; showUser = true }, Modifier.fillMaxWidth()) { Text("+ Pridat uzivatele") }
        }
    }
}

@Composable private fun PasswordDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit, hasOld: Boolean) {
    var o by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (hasOld) "Zmenit heslo" else "Nastavit heslo") }, text = { Column {
        if (hasOld) { OutlinedTextField(o, { o = it }, label = { Text("Soucasne") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp)) }
        OutlinedTextField(n1, { n1 = it }, label = { Text("Nove heslo") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
        OutlinedTextField(n2, { n2 = it }, label = { Text("Potvrdit") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (n1.isNotBlank() && n2.isNotBlank() && n1 != n2) Text("Neshoduji se", color = Color.Red, fontSize = 12.sp)
    } }, confirmButton = { Button(onClick = { onOk(o, n1) }, enabled = n1.isNotBlank() && n1 == n2) { Text("Ulozit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Zrusit") } })
}

@Composable private fun UserEditDialog(user: UserProfile?, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(user?.name ?: "") }; var role by remember { mutableStateOf(user?.role ?: "worker") }
    var perms by remember { mutableStateOf(user?.permissions ?: UserProfile.defaultPermissions("worker")) }; var rE by remember { mutableStateOf(false) }
    val roles = listOf("admin" to "Spravce", "manager" to "Manazer", "worker" to "Pracovnik", "viewer" to "Nahled")
    val pl = mapOf("crm_read" to "CRM cteni", "crm_write" to "CRM zapis", "crm_delete" to "CRM mazani", "calendar_read" to "Kalendar cteni", "calendar_write" to "Kalendar zapis",
        "contacts_read" to "Kontakty cteni", "contacts_write" to "Kontakty zapis", "voice_commands" to "Hlasove prikazy", "settings_access" to "Nastaveni", "import_data" to "Import", "export_data" to "Export", "manage_users" to "Sprava uzivatelu")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (user == null) "Novy uzivatel" else "Upravit: ${user.name}") }, text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
        item { OutlinedTextField(name, { name = it }, label = { Text("Jmeno") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp)) }
        item { SDrop("Role", roles.first { it.first == role }.second, rE, { rE = it }, roles.map { it.second }) { role = roles[it].first; perms = UserProfile.defaultPermissions(role); rE = false }; Spacer(Modifier.height(8.dp)); Text("Opravneni", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        pl.forEach { (k, l) -> item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f), fontSize = 13.sp); Switch(perms[k] ?: false, { perms = perms.toMutableMap().apply { this[k] = it } }) } } }
    } }, confirmButton = { Button(onClick = { onSave(UserProfile(id = user?.id ?: java.util.UUID.randomUUID().toString(), name = name, role = role, passwordHash = user?.passwordHash ?: "", permissions = perms)) }, enabled = name.isNotBlank()) { Text("Ulozit") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Smazat", color = Color.Red) }; TextButton(onClick = onDismiss) { Text("Zrusit") } } })
}

// 7. DATA
@Composable private fun DataSection(sm: SettingsManager, vm: SecretaryViewModel) {
    var exp by remember { mutableStateOf(false) }
    var showCl by remember { mutableStateOf(false) }; var showRs by remember { mutableStateOf(false) }
    if (showCl) AlertDialog(onDismissRequest = { showCl = false }, title = { Text("Vymazat historii?") }, confirmButton = { Button(onClick = { vm.clearHistory(); showCl = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Smazat") } }, dismissButton = { TextButton(onClick = { showCl = false }) { Text("Zrusit") } })
    if (showRs) AlertDialog(onDismissRequest = { showRs = false }, title = { Text("Obnovit vychozi?") }, confirmButton = { Button(onClick = { vm.resetSettings(); showRs = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Obnovit") } }, dismissButton = { TextButton(onClick = { showRs = false }) { Text("Zrusit") } })
    SCard("Data a uloziste", Icons.Default.AccountBox, exp, { exp = !exp }) {
        Button(onClick = { vm.exportCrmData() }, Modifier.fillMaxWidth()) { Text("Exportovat CRM (CSV)") }
        Spacer(Modifier.height(8.dp))
        val ctx = LocalContext.current
        var syncResult by remember { mutableStateOf<String?>(null) }
        var filterUk by remember { mutableStateOf(true) }
        Text("Import kontaktů z telefonu", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = filterUk, onCheckedChange = { filterUk = it })
            Spacer(Modifier.width(8.dp))
            Text(if (filterUk) "Pouze UK čísla (+44, 07, 01, 02)" else "Všechna čísla", fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            vm.syncPhoneContacts(ctx, filterUk)
            syncResult = "Synchronizace spuštěna..."
        }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Importovat kontakty") }
        if (syncResult != null) { Text(syncResult!!, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text("Import databaze", fontWeight = FontWeight.SemiBold)
        var path by remember { mutableStateOf(sm.importFilePath) }
        SField("Cesta k CSV", path, { path = it; sm.importFilePath = it })
        var tE by remember { mutableStateOf(false) }; val tbls = listOf("clients" to "Klienti", "properties" to "Nemovitosti", "jobs" to "Zakazky")
        SDrop("Tabulka", tbls.first { it.first == sm.importTargetTable }.second, tE, { tE = it }, tbls.map { it.second }) { sm.importTargetTable = tbls[it].first; tE = false }
        var ai by remember { mutableStateOf(sm.autoImportEnabled) }
        SSwitch("Auto import pri spusteni", null, ai) { ai = it; sm.autoImportEnabled = it }
        Button(onClick = { vm.triggerManualImport() }, Modifier.fillMaxWidth(), enabled = path.isNotBlank()) { Text("Spustit import") }
        Text("Hlasem: 'importuj databazi klientu'", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showCl = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Vymazat historii") }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { showRs = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Obnovit vychozi nastaveni") }
    }
}

// ============================================================
// 8a. PROFIL FIRMY (z tenant config)
// ============================================================

@Composable private fun CompanyProfileSection(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val config = state.tenantConfig ?: return
    var exp by remember { mutableStateOf(false) }
    SCard("Profil firmy", Icons.Default.AccountCircle, exp, { exp = !exp }) {
        val wsMode = config["workspace_mode"]?.toString() ?: "-"
        val wsLabel = when(wsMode) { "solo" -> "Solo (1 uživatel)"; "team" -> "Tým (2-5)"; "business" -> "Firma (6-30)"; else -> wsMode }
        ARow("Režim", wsLabel)
        ARow("Interní jazyk", config["default_internal_lang"]?.toString()?.uppercase() ?: "-")
        ARow("Zákaznický jazyk", config["default_customer_lang"]?.toString()?.uppercase() ?: "-")
        ARow("Int. jazykový mód", config["internal_language_mode"]?.toString() ?: "-")
        ARow("Zák. jazykový mód", config["customer_language_mode"]?.toString() ?: "-")
        @Suppress("UNCHECKED_CAST")
        val limits = config["limits"] as? Map<String, Any?>
        if (limits != null) {
            Spacer(Modifier.height(8.dp))
            Text("Limity", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            ARow("Max uživatelů", limits["max_users"]?.toString() ?: "-")
            ARow("Max klientů", limits["max_clients"]?.toString() ?: "-")
            ARow("Max zakázek/měsíc", limits["max_jobs_per_month"]?.toString() ?: "-")
            ARow("Hlasové minuty", limits["max_voice_minutes"]?.toString() ?: "-")
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
    SCard("O aplikaci", Icons.Default.Info, exp, { exp = !exp }) {
        Text(VersionInfo.APP_NAME, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(VersionInfo.APP_DESCRIPTION, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        ARow("Verze", "${VersionInfo.VERSION_NAME} (build ${VersionInfo.VERSION_CODE})")
        ARow("Datum vydani", VersionInfo.BUILD_DATE)
        ARow("Balicek", VersionInfo.PACKAGE_NAME)
        Spacer(Modifier.height(12.dp))

        Text("Autor a kod", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        ARow("Struktura a kod", VersionInfo.AUTHOR_NAME)
        ARow("Email", VersionInfo.AUTHOR_EMAIL)
        ARow("Telefon", VersionInfo.AUTHOR_PHONE)
        ARow("Web", VersionInfo.AUTHOR_WEB)
        ARow("Firma", VersionInfo.COMPANY)

        val latest = VersionInfo.getLatestChanges()
        if (latest != null && latest.coder.isNotBlank()) {
            ARow("Kodovani", latest.coder)
        }
        Spacer(Modifier.height(12.dp))

        Text("Technologie", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        ARow("Platforma", VersionInfo.PLATFORM)
        ARow("Backend", VersionInfo.BACKEND)
        ARow("Architektura", VersionInfo.ARCHITECTURE)
        ARow("AI engine", VersionInfo.AI_ENGINE)
        ARow("Databaze", VersionInfo.DB_ENGINE)
        ARow("Min SDK", "${VersionInfo.MIN_SDK}")
        ARow("Target SDK", "${VersionInfo.TARGET_SDK}")
        Spacer(Modifier.height(12.dp))

        Text("Struktura projektu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.PROJECT_STRUCTURE.forEach { ARow(it.filename, it.description) }
        Spacer(Modifier.height(12.dp))

        Text("Licence", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(VersionInfo.LICENSE, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
    }
}

// ============================================================
// 9. HISTORIE VERZI (audit log)
// ============================================================

@Composable private fun VersionHistorySection() {
    var exp by remember { mutableStateOf(false) }
    SCard("Historie verzi", Icons.Default.List, exp, { exp = !exp }) {
        // Pravidla verzovani
        Text("Pravidla verzovani", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.VERSIONING_RULES.forEach { rule ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(rule.type, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(60.dp), color = when (rule.type) { "PATCH" -> Color(0xFF4CAF50); "MINOR" -> Color(0xFF2196F3); else -> Color(0xFFF44336) })
                Column { Text(rule.example, fontSize = 12.sp); Text(rule.description, fontSize = 11.sp, color = Color.Gray) }
            }
        }

        Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        // Povinne kroky
        Text("Povinne kroky pri zmene", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.MANDATORY_STEPS.forEach { step ->
            Text(step, fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 1.dp))
        }

        Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

        // Changelog
        Text("Changelog", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        VersionInfo.CHANGELOG.forEach { entry ->
            val typeColor = when (entry.type) { ChangeType.PATCH -> Color(0xFF4CAF50); ChangeType.MINOR -> Color(0xFF2196F3); ChangeType.MAJOR -> Color(0xFFF44336) }
            val typeLabel = when (entry.type) { ChangeType.PATCH -> "OPRAVA"; ChangeType.MINOR -> "FUNKCE"; ChangeType.MAJOR -> "ARCHITEKTURA" }

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
                    Text("Autor: ${entry.author}", fontSize = 11.sp, color = Color.Gray)
                    if (entry.coder.isNotBlank()) Text("Kod: ${entry.coder}", fontSize = 11.sp, color = Color.Gray)

                    // Rozbalitelny detail
                    var showDetail by remember { mutableStateOf(false) }
                    TextButton(onClick = { showDetail = !showDetail }) { Text(if (showDetail) "Skryt zmeny" else "Zobrazit zmeny (${entry.changes.size})", fontSize = 11.sp) }
                    if (showDetail) {
                        entry.changes.forEach { change -> Text("  * $change", fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) }
                        if (entry.knownIssues.isNotEmpty()) {
                            Text("Zname problemy:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 4.dp))
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
