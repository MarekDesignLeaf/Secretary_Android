package com.example.secretary

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.secretary.ui.theme.SecretaryTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var voiceService: VoiceService? = null
    private var serviceBound = false
    private lateinit var calendarManager: CalendarManager
    private lateinit var contactManager: ContactManager
    private lateinit var mailManager: MailManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: SecretaryViewModel

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as VoiceService.VoiceBinder).getService()
            voiceService = service
            serviceBound = true
            initVoiceInService(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            serviceBound = false
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Voice service will be started after login, permissions just need to be granted
        Log.d("MainActivity", "Permissions result: RECORD_AUDIO=${permissions[Manifest.permission.RECORD_AUDIO]}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarManager = CalendarManager(this)
        contactManager = ContactManager(this)
        mailManager = MailManager(this)
        settingsManager = SettingsManager(this)
        Strings.setLanguage(settingsManager!!.appLanguage)
        viewModel = androidx.lifecycle.ViewModelProvider(this)[SecretaryViewModel::class.java]

        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) perms.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        
        requestPermissionsLauncher.launch(perms.toTypedArray())
        // Voice service will be started after login, not here

        setContent {
            val themeMode = settingsManager.themeMode
            SecretaryTheme(themeMode = themeMode) {
                val vm: SecretaryViewModel = viewModel()
                val navController = rememberNavController()
                
                LaunchedEffect(Unit) {
                    vm.setManagers(null, calendarManager, contactManager, mailManager, settingsManager)
                    vm.setOnShutdown {
                        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
                        val intent = Intent(this@MainActivity, VoiceService::class.java)
                        stopService(intent)
                        finishAffinity()
                    }
                }

                val state by vm.uiState.collectAsState()
                
                // Start VoiceService only after login, stop on logout
                LaunchedEffect(state.loggedIn) {
                    if (state.loggedIn == true && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        startAndBindVoiceService()
                    } else if (state.loggedIn == false) {
                        // Stop voice service on logout
                        voiceService?.voiceManager?.stop()
                        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
                        stopService(Intent(this@MainActivity, VoiceService::class.java))
                        voiceServiceStarted = false
                    }
                }
                
                when (state.loggedIn) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    false -> LoginScreen(vm)
                    true -> when (state.onboardingComplete) {
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        false -> OnboardingScreen(vm) {}
                        true -> {
                            LaunchedEffect(Unit) { vm.loadTenantConfig() }
                            MainAppScaffold(vm, navController)
                        }
                    }
                }
            }
        }
    }

    private var voiceServiceStarted = false
    private fun startAndBindVoiceService() {
        if (voiceServiceStarted) return
        voiceServiceStarted = true
        val intent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initVoiceInService(service: VoiceService) {
        service.initVoiceManager(
            onResult = { text -> viewModel.onVoiceInput(text) },
            onReady = { viewModel.setListening(true) },
            onRecognizerError = { viewModel.setListening(false) },
            onHotwordDetected = { viewModel.startListening() },
            onStatusChange = { status -> viewModel.setStatus(status) }
        )
        service.voiceManager?.let { vm ->
            viewModel.setManagers(vm, calendarManager, contactManager, mailManager, settingsManager)
        }
    }

    override fun onDestroy() {
        if (serviceBound) unbindService(serviceConnection)
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: SecretaryViewModel, navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showAddClientDialog by remember { mutableStateOf(false) }

    if (showAddClientDialog) {
        AddClientDialog(
            onDismiss = { showAddClientDialog = false },
            onConfirm = { name, email, phone -> 
                viewModel.createClientManual(name, email, phone)
                showAddClientDialog = false
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in navItems.map { it.route }) {
                NavigationBar {
                    navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, null) },
                            label = { Text(screen.title, fontSize = 10.sp) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) { 
                LaunchedEffect(Unit) { viewModel.updateContext(null, null) }
                HomeScreen(viewModel) 
            }
            composable(Screen.Crm.route) { 
                LaunchedEffect(Unit) { viewModel.updateContext(null, null) }
                CrmHubScreen(viewModel, navController) 
            }
            composable(Screen.Tasks.route) { 
                LaunchedEffect(Unit) { viewModel.updateContext(null, null) }
                TasksScreen(viewModel) 
            }
            composable(Screen.Calendar.route) { 
                LaunchedEffect(Unit) { viewModel.updateContext(null, null) }
                CalendarScreen(viewModel) 
            }
            composable(Screen.Settings.route) { 
                LaunchedEffect(Unit) { 
                    viewModel.updateContext(null, null)
                    viewModel.loadSettings()
                }
                SettingsScreen(viewModel) 
            }
            composable(
                route = Screen.ClientDetail.route,
                arguments = listOf(navArgument("clientId") { type = NavType.LongType })
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getLong("clientId") ?: 0L
                LaunchedEffect(clientId) { viewModel.updateContext(clientId, "client") }
                ClientDetailScreen(clientId, viewModel, navController)
            }
            composable(
                route = Screen.JobDetail.route,
                arguments = listOf(navArgument("jobId") { type = NavType.LongType })
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getLong("jobId") ?: 0L
                JobDetailScreen(jobId, viewModel, navController)
            }
            composable(
                route = "task/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                TaskDetailScreen(taskId, viewModel, navController)
            }
        }
    }
}

@Composable
fun CalendarScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val calendarText = remember { mutableStateOf("Načítám kalendář...") }
    LaunchedEffect(Unit) {
        val ctx = viewModel.getCalendarText(7)
        calendarText.value = ctx
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kalendář", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val scheduledTasks = state.tasks.filter { it.plannedDate != null || it.deadline != null }
        if (scheduledTasks.isNotEmpty()) {
            Text("Naplánované úkoly", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            scheduledTasks.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(t.deadline ?: t.plannedDate ?: "", fontSize = 12.sp, modifier = Modifier.width(80.dp), color = MaterialTheme.colorScheme.primary)
                    Text(t.title, fontSize = 14.sp)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        Text("Události z kalendáře", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
        Text(calendarText.value)
    }
}

@Composable
fun AddClientDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.newClientTitle) },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text(Strings.clientName) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = email, onValueChange = { email = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = phone, onValueChange = { phone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, email, phone) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    clients: List<Client>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long?, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("interni_poznamka") }
    var selectedPrio by remember { mutableStateOf("bezna") }
    var deadline by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }
    var prioExpanded by remember { mutableStateOf(false) }
    var clientExpanded by remember { mutableStateOf(false) }
    
    val types = listOf("volat" to "📞 ${Strings.call}", "email" to "📧 Email", "schuzka" to "📅 ${Strings.meeting}",
        "objednat_material" to "🧱 ${Strings.orderMaterial}", "navsteva_klienta" to "🏠 ${Strings.visit}",
        "realizace" to "🔨 ${Strings.workExecution}", "kontrola" to "✅ ${Strings.inspection}", "vytvorit_kalkulaci" to "💰 ${Strings.calculation}",
        "pripomenout_se" to "🔔 ${Strings.remind}", "interni_poznamka" to "📋 ${Strings.noteLabel}")
    val prios = listOf("nizka" to Strings.low, "bezna" to Strings.normal, "vysoka" to Strings.high, "urgentni" to Strings.urgent, "kriticka" to Strings.critical)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.newTask) },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("${Strings.taskTitle} *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                // Typ
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    TextField(value = types.first { it.first == selectedType }.second, onValueChange = {}, readOnly = true, label = { Text(Strings.taskType) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) })
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedType = k; typeExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Priorita
                ExposedDropdownMenuBox(expanded = prioExpanded, onExpandedChange = { prioExpanded = it }) {
                    TextField(value = prios.first { it.first == selectedPrio }.second, onValueChange = {}, readOnly = true, label = { Text(Strings.priority) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prioExpanded) })
                    ExposedDropdownMenu(expanded = prioExpanded, onDismissRequest = { prioExpanded = false }) {
                        prios.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedPrio = k; prioExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Klient
                if (clients.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        TextField(value = selectedClientName ?: Strings.noClient, onValueChange = {}, readOnly = true, label = { Text(Strings.client) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            DropdownMenuItem(text = { Text(Strings.noClient) }, onClick = { selectedClientId = null; selectedClientName = null; clientExpanded = false })
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextField(value = deadline, onValueChange = { deadline = it }, label = { Text("${Strings.deadline} (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank()) onConfirm(title, selectedType, selectedPrio, selectedClientId, selectedClientName, deadline.ifBlank { null }) }, enabled = title.isNotBlank()) { Text(Strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@Composable
fun HomeScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.status.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (state.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(48.dp))
        Card(Modifier.fillMaxWidth().height(120.dp), colors = CardDefaults.cardColors(containerColor = if (state.isListening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(state.lastAiReply, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
        
        if (state.contactResults.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("NALEZENÉ KONTAKTY", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    state.contactResults.forEach { contact ->
                        ListItem(
                            headlineContent = { Text(contact["name"] ?: "") },
                            supportingContent = { Text(contact["phone"] ?: "") },
                            trailingContent = { 
                                IconButton(onClick = { 
                                    val phone = contact["phone"] ?: return@IconButton
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }) { Icon(Icons.Default.Call, null) } 
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.startListening() },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(if (state.isListening) Icons.Default.Refresh else Icons.Default.Call, contentDescription = null, modifier = Modifier.size(48.dp))
        }
        
        // Ovladaci tlacitka - radek 1
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { viewModel.toggleBackground() }) {
                Icon(Icons.Default.Notifications, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.isBackgroundActive) "Pozadi ZAP" else "Pozadi VYP", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { viewModel.restartVoice() }) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restart", fontSize = 11.sp)
            }
        }
        // Ovladaci tlacitka - radek 2
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(
                onClick = { viewModel.shutdownApp() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Zavřít", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Odhlásit", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(Strings.history, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(state.history.reversed()) { item -> 
                Text("${if(item.role == "user") "Marek" else "Sekretářka"}: ${item.content}", Modifier.padding(6.dp))
                HorizontalDivider() 
            }
        }
    }
}

@Composable
fun CrmHubScreen(viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddClient by remember { mutableStateOf(false) }
    var showAddJob by remember { mutableStateOf(false) }
    var showAddLead by remember { mutableStateOf(false) }
    var showAddInvoice by remember { mutableStateOf(false) }
    var showAddWorkReport by remember { mutableStateOf(false) }
    var showAddQuote by remember { mutableStateOf(false) }
    var showLogComm by remember { mutableStateOf(false) }
    var showEditLead by remember { mutableStateOf<Lead?>(null) }
    var showInvoiceStatus by remember { mutableStateOf<Invoice?>(null) }
    val tabs = listOf(Strings.today, Strings.clients, Strings.jobs, Strings.tasks, Strings.leads, Strings.quotes, Strings.invoices, Strings.workReports, Strings.communications)

    LaunchedEffect(Unit) { viewModel.refreshCrmData() }

    Scaffold(
        floatingActionButton = {
            when (selectedTab) {
                1 -> FloatingActionButton(onClick = { showAddClient = true }) { Icon(Icons.Default.Add, "Klient") }
                2 -> FloatingActionButton(onClick = { showAddJob = true }) { Icon(Icons.Default.Add, "Zakázka") }
                4 -> FloatingActionButton(onClick = { showAddLead = true }) { Icon(Icons.Default.Add, "Lead") }
                5 -> FloatingActionButton(onClick = { showAddQuote = true }) { Icon(Icons.Default.Add, "Nabídka") }
                6 -> FloatingActionButton(onClick = { showAddInvoice = true }) { Icon(Icons.Default.Add, "Faktura") }
                7 -> FloatingActionButton(onClick = { showAddWorkReport = true }) { Icon(Icons.Default.Add, "Výkaz") }
                8 -> FloatingActionButton(onClick = { showLogComm = true }) { Icon(Icons.Default.Add, "Komunikace") }
                else -> {}
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 12.sp) }) }
            }
            Box(Modifier.padding(8.dp).weight(1f)) {
                when (selectedTab) {
                    0 -> DashboardTab(state, viewModel, navController)
                    1 -> ClientsListTab(state.clients, navController, viewModel)
                    2 -> JobsListTab(state.jobs, navController, viewModel)
                    3 -> TasksTab(state, viewModel)
                    4 -> LeadsListTab(state.leads, viewModel, onEditLead = { showEditLead = it })
                    5 -> QuotesListTab(state.quotes, viewModel)
                    6 -> {
                        val ctx = LocalContext.current
                        InvoicesListTab(state.invoices, navController = navController, onClickInvoice = { showInvoiceStatus = it }, onSendInvoice = { inv, method ->
                            val text = "Faktura ${inv.invoice_number}\nČástka: £${inv.grand_total}\nSplatnost: ${inv.due_date ?: "N/A"}\n\nDesignLeaf Ltd"
                            val intent = when(method) {
                                "sms" -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("sms:")).apply { putExtra("sms_body", text) }
                                "whatsapp" -> android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; `package` = "com.whatsapp"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
                                else -> android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_SUBJECT, "Faktura ${inv.invoice_number} — DesignLeaf"); putExtra(android.content.Intent.EXTRA_TEXT, text) }
                            }
                            try { ctx.startActivity(intent) } catch (_: Exception) { try { ctx.startActivity(android.content.Intent.createChooser(intent, "Odeslat fakturu")) } catch (_: Exception) {} }
                        })
                    }
                    7 -> WorkReportsTab(state.workReports, viewModel, navController)
                    8 -> CommunicationTab(state, viewModel, navController)
                }
            }
        }
    }

    if (showAddClient) {
        AddClientDialog(onDismiss = { showAddClient = false },
            onConfirm = { n, e, p -> viewModel.createClientManual(n, e, p); showAddClient = false })
    }
    if (showAddJob) {
        AddJobDialog(clients = state.clients, onDismiss = { showAddJob = false },
            onConfirm = { title, clientId, date -> viewModel.createJobManual(title, clientId, date); showAddJob = false })
    }
    if (showAddLead) {
        AddLeadDialog(onDismiss = { showAddLead = false },
            onConfirm = { name, source, email, phone, desc -> viewModel.createLeadManual(name, source, email, phone, desc); showAddLead = false })
    }
    if (showAddInvoice) {
        CreateInvoiceDialog(clients = state.clients, onDismiss = { showAddInvoice = false },
            onConfirm = { clientId, amount, dueDate -> viewModel.createInvoiceManual(clientId, amount, dueDate); showAddInvoice = false })
    }
    if (showAddQuote) {
        CreateQuoteDialog(clients = state.clients, onDismiss = { showAddQuote = false },
            onConfirm = { clientId, title -> viewModel.createQuote(clientId, title); showAddQuote = false })
    }
    if (showAddWorkReport) {
        CreateWorkReportDialog(clients = state.clients, onDismiss = { showAddWorkReport = false },
            onConfirm = { clientId, date, hrs, price, notes -> viewModel.createWorkReportManual(clientId, date, hrs, price, notes); showAddWorkReport = false })
    }
    if (showLogComm) {
        GlobalLogCommDialog(clients = state.clients, onDismiss = { showLogComm = false },
            onSave = { clientId, type, subj, msg, dir -> viewModel.logCommunication(clientId, null, type, subj, msg, dir); showLogComm = false })
    }
    if (showEditLead != null) {
        EditLeadDialog(lead = showEditLead!!, onDismiss = { showEditLead = null },
            onSave = { data -> viewModel.updateLead(showEditLead!!.id, data); showEditLead = null })
    }
    if (showInvoiceStatus != null) {
        InvoiceStatusDialog(currentStatus = showInvoiceStatus!!.status, onDismiss = { showInvoiceStatus = null },
            onSelect = { status -> viewModel.updateInvoiceStatus(showInvoiceStatus!!.id, status); showInvoiceStatus = null })
    }
}

@Composable
fun ClientsListTab(clients: List<Client>, navController: NavHostController, viewModel: SecretaryViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val filtered = if (searchQuery.isBlank()) clients
        else clients.filter {
            it.display_name.contains(searchQuery, ignoreCase = true) ||
            (it.client_code ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.email_primary ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.phone_primary ?: "").contains(searchQuery, ignoreCase = true)
        }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text(Strings.searchClients, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.syncPhoneContacts(ctx) }) {
                Icon(Icons.Default.Refresh, "Sync kontaktů", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noClients, color = Color.Gray) }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { client ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable { navController.navigate("client/${client.id}") },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                null, modifier = Modifier.size(40.dp).padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(client.display_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    client.client_code?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                                    if (client.is_commercial) Text(Strings.commercial, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                                }
                                client.phone_primary?.let { Text("\u260E $it", fontSize = 13.sp, maxLines = 1) }
                                client.email_primary?.let { Text("\u2709 $it", fontSize = 13.sp, color = Color.Gray, maxLines = 1) }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JobsListTab(jobs: List<Job>, navController: NavHostController, viewModel: SecretaryViewModel) {
    var editJob by remember { mutableStateOf<Job?>(null) }
    if (editJob != null) {
        JobEditDialog(job = editJob!!, onDismiss = { editJob = null },
            onSave = { data -> viewModel.updateJob(editJob!!.id, data); editJob = null })
    }
    if (jobs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noJobs, color = Color.Gray) }
    } else {
        LazyColumn {
            items(jobs) { job ->
                Card(Modifier.fillMaxWidth().padding(bottom = 4.dp).clickable { navController.navigate("job/${job.id}") }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.job_title, fontWeight = FontWeight.SemiBold)
                            if (!job.client_name.isNullOrBlank()) {
                                Text("👤 ${job.client_name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { job.client_id?.let { navController.navigate("client/$it") } })
                            }
                            Text("${job.job_number ?: ""} · ${job.start_date_planned ?: "bez termínu"}", fontSize = 12.sp, color = Color.Gray)
                        }
                        val statusColor = when {
                            job.job_status.contains("dokonceno") || job.job_status.contains("uzavren") -> Color(0xFF4CAF50)
                            job.job_status.contains("realizaci") -> Color(0xFF2196F3)
                            job.job_status.contains("ceka") -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                            Text(Strings.localizeStatus(job.job_status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = statusColor)
                        }
                        IconButton(onClick = { editJob = job }) { Icon(Icons.Default.Edit, "Upravit", tint = Color.Gray) }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoicesListTab(invoices: List<Invoice>, navController: NavHostController? = null, onClickInvoice: (Invoice) -> Unit = {}, onSendInvoice: (Invoice, String) -> Unit = { _, _ -> }) {
    if (invoices.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noInvoices, color = Color.Gray) }
    } else {
        LazyColumn {
            items(invoices) { inv ->
                Card(Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { onClickInvoice(inv) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inv.invoice_number ?: "Bez čísla", fontWeight = FontWeight.Bold)
                                if (!inv.client_name.isNullOrBlank()) {
                                    Text("👤 ${inv.client_name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { inv.client_id?.let { navController?.navigate("client/$it") } })
                                }
                                Text(Strings.localizeStatus(inv.status), fontSize = 12.sp, color = Color.Gray)
                                Text("Splatnost: ${inv.due_date ?: "?"}", fontSize = 12.sp, color = if (inv.status == "po_splatnosti") Color.Red else Color.Gray)
                            }
                            Text("£${inv.grand_total}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (inv.status == "po_splatnosti") Color.Red else MaterialTheme.colorScheme.primary)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onSendInvoice(inv, "sms") }) { Text("📱SMS", fontSize = 11.sp) }
                            TextButton(onClick = { onSendInvoice(inv, "whatsapp") }) { Text("💬WA", fontSize = 11.sp) }
                            TextButton(onClick = { onSendInvoice(inv, "email") }) { Text("📧Mail", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkReportsTab(reports: List<WorkReport>, viewModel: SecretaryViewModel, navController: NavHostController? = null) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var invoiceResult by remember { mutableStateOf<String?>(null) }
    if (reports.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noWorkReports, color = Color.Gray) }
    } else {
        LazyColumn {
            if (reports.size > 1) {
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedIds.size == reports.size, onCheckedChange = { if (it) selectedIds = reports.map { r -> r.id }.toSet() else selectedIds = emptySet() })
                            Text("Vybrat vše", fontSize = 13.sp)
                        }
                        if (selectedIds.isNotEmpty()) {
                            Button(onClick = {
                                viewModel.batchInvoiceFromWorkReports(selectedIds.toList()) { res ->
                                    val created = (res?.get("total_created") as? Number)?.toInt() ?: 0
                                    val errors = (res?.get("total_errors") as? Number)?.toInt() ?: 0
                                    invoiceResult = "Vytvořeno $created faktur" + if (errors > 0) ", $errors chyb" else ""
                                    selectedIds = emptySet()
                                    viewModel.refreshCrmData()
                                }
                            }, modifier = Modifier.height(36.dp)) { Text("Fakturovat ${selectedIds.size}×", fontSize = 13.sp) }
                        }
                    }
                }
            }
            if (invoiceResult != null) {
                item { Text(invoiceResult!!, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)); LaunchedEffect(invoiceResult) { kotlinx.coroutines.delay(3000); invoiceResult = null } }
            }
            items(reports) { wr ->
                Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(wr.client_name ?: "Klient #${wr.client_id}", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { navController?.navigate("client/${wr.client_id}") })
                                Text(wr.work_date ?: "", fontSize = 13.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("£${"%.2f".format(wr.total_price)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Text("${wr.total_hours}h", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                        if (wr.entries.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            wr.entries.forEach { e ->
                                val etype = e["type"]?.toString() ?: "work"
                                val ehrs = (e["hours"] as? Number)?.toDouble() ?: 0.0
                                val etotal = (e["total_price"] as? Number)?.toDouble() ?: 0.0
                                Text("  $etype: ${ehrs}h · £${"%.2f".format(etotal)}", fontSize = 12.sp)
                            }
                        }
                        if (wr.workers.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            val workerNames = wr.workers.mapNotNull { it["worker_name"]?.toString() }.joinToString(", ")
                            Text("👷 $workerNames", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (wr.waste.isNotEmpty() && wr.waste.any { (it["quantity"] as? Number)?.toDouble() ?: 0.0 > 0 }) {
                            val wq = (wr.waste[0]["quantity"] as? Number)?.toDouble() ?: 0.0
                            val wt = (wr.waste[0]["total_price"] as? Number)?.toDouble() ?: 0.0
                            Text("🗑 Waste: ${wq.toInt()} bags · £${"%.2f".format(wt)}", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (!wr.notes.isNullOrBlank()) {
                            Text("📝 ${wr.notes}", fontSize = 12.sp, color = Color.Gray, maxLines = 2)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when(wr.status) { "confirmed" -> Color(0xFF4CAF50); "draft" -> Color(0xFFFF9800); else -> Color.Gray }
                                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                                    Text(Strings.localizeStatus(wr.status), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = statusColor)
                                }
                                Checkbox(checked = selectedIds.contains(wr.id), onCheckedChange = { if (it) selectedIds = selectedIds + wr.id else selectedIds = selectedIds - wr.id }, modifier = Modifier.size(32.dp))
                            }
                            TextButton(onClick = {
                                viewModel.createInvoiceFromWorkReport(wr.id) { res ->
                                    if (res != null) {
                                        val profit = (res["profit"] as? Number)?.toDouble() ?: 0.0
                                        val margin = (res["profit_margin"] as? Number)?.toDouble() ?: 0.0
                                        invoiceResult = "✅ Faktura ${res["invoice_number"]}: £${"%.0f".format(res["grand_total"] as? Number ?: 0)}, zisk £${"%.0f".format(profit)} (${margin}%)"
                                    } else invoiceResult = "❌ Chyba — výkaz již fakturován?"
                                    viewModel.refreshCrmData()
                                }
                            }) { Text("📄 Faktura", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeadsListTab(leads: List<Lead>, viewModel: SecretaryViewModel, onEditLead: (Lead) -> Unit = {}) {
    var showConvertDialog by remember { mutableStateOf<Lead?>(null) }
    var selectedStatus by remember { mutableStateOf("vše") }
    val statuses = listOf("vše", "new", "kvalifikovany", "nabidka_odeslana", "preveden_na_klienta", "zamitnuto")
    val filtered = if (selectedStatus == "vše") leads else leads.filter { it.status == selectedStatus }
    
    Column(Modifier.fillMaxSize()) {
        // Status filter
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(statuses) { s ->
                FilterChip(selected = selectedStatus == s, onClick = { selectedStatus = s },
                    label = { Text(if (s == "vše") "Vše (${leads.size})" else "${Strings.localizeStatus(s)} (${leads.count { it.status == s }})") })
            }
        }
        
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text(Strings.noLeads, color = Color.Gray) }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered) { lead ->
                val sourceEmoji = when (lead.lead_source) {
                    "checkatrade" -> "🏠"; "web" -> "🌐"; "telefon" -> "📞"; "doporuceni" -> "👥"; else -> "📋"
                }
                val statusColor = when (lead.status) {
                    "new" -> Color(0xFF2196F3)
                    "kvalifikovany" -> Color(0xFF4CAF50)
                    "nabidka_odeslana" -> Color(0xFFF57C00)
                    "preveden_na_klienta", "preveden_na_zakazku" -> Color(0xFF9E9E9E)
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Card(Modifier.fillMaxWidth().padding(bottom = 4.dp).clickable { onEditLead(lead) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sourceEmoji, fontSize = 24.sp, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lead.contact_name ?: lead.lead_code ?: "Lead", fontWeight = FontWeight.SemiBold)
                            Text("${lead.lead_source ?: "?"} · ${lead.received_at?.take(10) ?: ""}", fontSize = 12.sp, color = Color.Gray)
                            if (lead.contact_phone != null) Text("📞 ${lead.contact_phone}", fontSize = 12.sp)
                            if (lead.contact_email != null) Text("📧 ${lead.contact_email}", fontSize = 12.sp, color = Color.Gray)
                            if (!lead.description.isNullOrBlank()) Text(lead.description, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                                Text(Strings.localizeStatus(lead.status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = statusColor)
                            }
                            if (lead.status == "new" || lead.status == "kvalifikovany") {
                                TextButton(onClick = { showConvertDialog = lead }) { Text("Převést →", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (showConvertDialog != null) {
        val lead = showConvertDialog!!
        ConvertLeadDialog(lead = lead, onDismiss = { showConvertDialog = null },
            onConvertToClient = { name, email, phone -> viewModel.convertLeadToClient(lead.id, name, email, phone); showConvertDialog = null },
            onConvertToJob = { title -> viewModel.convertLeadToJob(lead.id, title); showConvertDialog = null })
    }
}

@Composable
fun ConvertLeadDialog(lead: Lead, onDismiss: () -> Unit, onConvertToClient: (String, String, String) -> Unit, onConvertToJob: (String) -> Unit) {
    var mode by remember { mutableStateOf("client") }
    var name by remember { mutableStateOf(lead.contact_name ?: "") }
    var email by remember { mutableStateOf(lead.contact_email ?: "") }
    var phone by remember { mutableStateOf(lead.contact_phone ?: "") }
    var jobTitle by remember { mutableStateOf("Zakázka z ${lead.lead_code ?: "leadu"}") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.convertLead) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "client", onClick = { mode = "client" }, label = { Text(Strings.toClient) })
                    FilterChip(selected = mode == "job", onClick = { mode = "job" }, label = { Text(Strings.toJob) })
                }
                Spacer(Modifier.height(12.dp))
                if (mode == "client") {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("${Strings.clientName} *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    TextField(value = email, onValueChange = { email = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    TextField(value = phone, onValueChange = { phone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth())
                } else {
                    TextField(value = jobTitle, onValueChange = { jobTitle = it }, label = { Text("${Strings.jobTitle} *") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (mode == "client" && name.isNotBlank()) onConvertToClient(name, email, phone)
                else if (mode == "job" && jobTitle.isNotBlank()) onConvertToJob(jobTitle)
            }) { Text(Strings.convert) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLeadDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("telefon") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sourceExpanded by remember { mutableStateOf(false) }
    val sources = listOf("checkatrade", "web", "telefon", "doporuceni", "jiny")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.newLead) },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("${Strings.contactName} *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = it }) {
                    TextField(value = source, onValueChange = {}, readOnly = true, label = { Text(Strings.source) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceExpanded) })
                    ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        sources.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { source = s; sourceExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(value = phone, onValueChange = { phone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = email, onValueChange = { email = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = description, onValueChange = { description = it }, label = { Text(Strings.inquiryDesc) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name, source, email, phone, description) }, enabled = name.isNotBlank()) { Text(Strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@Composable
fun DashboardTab(state: UiState, viewModel: SecretaryViewModel, navController: NavHostController) {
    LazyColumn(Modifier.fillMaxSize()) {
        // Urgentni ukoly
        val urgent = state.tasks.filter { !it.isCompleted && (it.priority == "urgentni" || it.priority == "kriticka") }
        if (urgent.isNotEmpty()) {
            item { DashSection(Strings.urgentTasks, urgent.size) }
            items(urgent) { t -> TaskRow(t, viewModel) }
        }
        // Dnesni ukoly
        val today = state.tasks.filter { !it.isCompleted && it.status != "hotovo" && it.status != "zruseno" }.take(10)
        item { DashSection(Strings.activeTasks, today.size) }
        if (today.isEmpty()) { item { Text(Strings.noActiveTasks, color = Color.Gray, modifier = Modifier.padding(16.dp)) } }
        else { items(today) { t -> TaskRow(t, viewModel) } }
        // Ceka na klienta
        val waiting = state.tasks.filter { it.status == "ceka_na_klienta" }
        if (waiting.isNotEmpty()) {
            item { DashSection(Strings.waitingForClient, waiting.size) }
            items(waiting) { t -> TaskRow(t, viewModel) }
        }
        // Ceka na material
        val waitMat = state.tasks.filter { it.status == "ceka_na_material" }
        if (waitMat.isNotEmpty()) {
            item { DashSection(Strings.waitingForMaterial, waitMat.size) }
            items(waitMat) { t -> TaskRow(t, viewModel) }
        }
        // Ceka na platbu
        val waitPay = state.tasks.filter { it.status == "ceka_na_platbu" || it.waitingForPayment }
        if (waitPay.isNotEmpty()) {
            item { DashSection(Strings.waitingForPayment, waitPay.size) }
            items(waitPay) { t -> TaskRow(t, viewModel) }
        }
        // Aktivni zakazky
        val activeJobs = state.jobs.filter { it.job_status != "completed" && it.job_status != "cancelled" }
        if (activeJobs.isNotEmpty()) {
            item { DashSection(Strings.activeJobs, activeJobs.size) }
            items(activeJobs) { j -> ListItem(headlineContent = { Text(j.job_title) }, supportingContent = { Text(Strings.localizeStatus(j.job_status)) }, leadingContent = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { navController.navigate("job/${j.id}") }); HorizontalDivider() }
        }
        // Nove leady
        val newLeads = state.leads.filter { it.status == "new" || it.status == "kvalifikovany" }
        if (newLeads.isNotEmpty()) {
            item { DashSection(Strings.newLeads, newLeads.size) }
            items(newLeads) { l ->
                val emoji = when(l.lead_source) { "checkatrade"->"🏠"; "web"->"🌐"; "telefon"->"📞"; else->"📋" }
                ListItem(headlineContent = { Text(l.contact_name ?: l.lead_code ?: "Lead") }, supportingContent = { Text("${l.lead_source ?: "?"} · ${Strings.localizeStatus(l.status)}") }, leadingContent = { Text(emoji, fontSize = 20.sp) })
                HorizontalDivider()
            }
        }
        // Klienti
        item { DashSection(Strings.clientsInCrm, state.clients.size) }
    }
}

@Composable
fun DashSection(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text("$count", Modifier.padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider()
}

@Composable
fun <T> CrmDataList(items: List<T>, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (T) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Žádná data v CRM", color = Color.Gray) }
    } else {
        LazyColumn {
            items(items) { item ->
                val label = when(item) {
                    is Client -> item.display_name
                    is Property -> item.property_name
                    is Job -> item.job_title
                    is WasteLoad -> item.waste_type
                    is Invoice -> "Faktura ${item.invoice_number}"
                    else -> "Neznámé"
                }
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(icon, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable { onClick(item) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(clientId: Long, viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    LaunchedEffect(clientId) { viewModel.loadClientDetail(clientId) }
    val detail = state.selectedClientDetail
    val tabs = listOf(Strings.overview, Strings.jobs, Strings.tasks, Strings.communications, Strings.notes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.client?.display_name ?: "Načítám...") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, "Upravit") }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                2 -> FloatingActionButton(onClick = { showAddTaskDialog = true }) { Icon(Icons.Default.Add, "Úkol") }
                4 -> FloatingActionButton(onClick = { showNoteDialog = true }) { Icon(Icons.Default.Add, "Poznámka") }
                else -> {}
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (detail == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 12.sp) }) }
                }
                Box(Modifier.weight(1f).padding(12.dp)) {
                    when (selectedTab) {
                        0 -> ClientInfoTab(detail, viewModel)
                        1 -> ClientJobsTab(detail)
                        2 -> ClientTasksTab(state.tasks.filter { it.clientId == clientId }, viewModel)
                        3 -> ClientCommsTab(detail, viewModel)
                        4 -> ClientNotesTab(detail)
                    }
                }
            }
        }
    }

    if (showEditDialog && detail != null) {
        EditClientDialog(detail.client, onDismiss = { showEditDialog = false },
            onSave = { data -> viewModel.updateClient(clientId, data); showEditDialog = false })
    }
    if (showNoteDialog) {
        AddNoteDialog(onDismiss = { showNoteDialog = false },
            onSave = { note -> viewModel.addClientNote(clientId, note); showNoteDialog = false })
    }
    if (showAddTaskDialog) {
        AddTaskDialog(clients = state.clients, onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, type, prio, _, _, deadline ->
                viewModel.createTaskManual(title, type, prio, clientId, detail?.client?.display_name, deadline)
                showAddTaskDialog = false
            })
    }
}

@Composable
fun ClientInfoTab(detail: ClientDetail, viewModel: SecretaryViewModel) {
    val c = detail.client
    val ctx = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    if (showEditDialog) {
        ClientEditDialog(client = c, onDismiss = { showEditDialog = false },
            onSave = { data -> viewModel.updateClient(c.id, data); showEditDialog = false })
    }
    LazyColumn {
        // === ACTION BUTTONS ===
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!c.phone_primary.isNullOrBlank()) {
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone_primary}"))) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Zavolat", fontSize = 12.sp) }
                    Button(onClick = {
                        val phone = c.phone_primary!!.replace("+","").replace(" ","").replace("-","")
                        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))
                        ctx.startActivity(waIntent)
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) { Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("WhatsApp", fontSize = 12.sp) }
                }
                if (!c.email_primary.isNullOrBlank()) {
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email_primary}"))) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) { Icon(Icons.Default.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Email", fontSize = 12.sp) }
                }
                Button(onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Upravit", fontSize = 12.sp) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(Strings.contact, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    InfoRow(Strings.codeLabel, c.client_code)
                    InfoRow(Strings.clientType, c.client_type)
                    InfoRow(Strings.titleField, c.title)
                    InfoRow(Strings.firstName, c.first_name)
                    InfoRow(Strings.lastName, c.last_name)
                    InfoRow(Strings.email, c.email_primary)
                    InfoRow(Strings.emailSecondary, c.email_secondary)
                    InfoRow(Strings.phone, c.phone_primary)
                    InfoRow(Strings.phoneSecondary, c.phone_secondary)
                    InfoRow(Strings.website, c.website)
                    InfoRow(Strings.preferredContact, c.preferred_contact_method)
                    InfoRow(Strings.status, c.status)
                    InfoRow(Strings.commercial, if (c.is_commercial) Strings.yes else Strings.no)
                    if ((c.default_hourly_rate ?: 0.0) > 0) InfoRow("Hodinová sazba", "£${c.default_hourly_rate}/h")
                }
            }
        }
        if (c.company_name != null || c.is_commercial) {
            item {
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Strings.companyName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Strings.companyName, c.company_name)
                        InfoRow(Strings.companyRegNo, c.company_registration_no)
                        InfoRow(Strings.vatNo, c.vat_no)
                    }
                }
            }
        }
        if (c.billing_address_line1 != null || c.billing_city != null) {
            item {
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Strings.billingAddress, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Strings.billingAddress, c.billing_address_line1)
                        InfoRow(Strings.city, c.billing_city)
                        InfoRow(Strings.postcode, c.billing_postcode)
                        InfoRow(Strings.country, c.billing_country)
                    }
                }
            }
        }
        if (detail.properties.isNotEmpty()) {
            item { Text("${Strings.properties} (${detail.properties.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            items(detail.properties) { p ->
                Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.property_name, fontWeight = FontWeight.SemiBold)
                        Text("${p.address_line1}, ${p.city} ${p.postcode}", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value != null && value.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("$label: ", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.width(120.dp))
            Text(value, fontSize = 14.sp)
        }
    }
}

@Composable
fun ClientJobsTab(detail: ClientDetail) {
    if (detail.recent_jobs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noJobs, color = Color.Gray) }
    } else {
        LazyColumn {
            items(detail.recent_jobs) { job ->
                Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(job.job_title, fontWeight = FontWeight.SemiBold)
                            Text("${job.job_number ?: ""} · ${job.start_date_planned ?: Strings.noTermin}", fontSize = 12.sp, color = Color.Gray)
                            if (job.start_date_planned != null) Text("Plán: ${job.start_date_planned}", fontSize = 12.sp)
                        }
                        val statusColor = when (job.job_status) {
                            "dokončeno", "uzavřeno" -> Color(0xFF4CAF50)
                            "v_realizaci" -> Color(0xFF2196F3)
                            "pozastaveno" -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                            Text(Strings.localizeStatus(job.job_status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = statusColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClientTasksTab(tasks: List<Task>, viewModel: SecretaryViewModel) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noTasksForClient, color = Color.Gray) }
    } else {
        LazyColumn { items(tasks) { t -> TaskRow(t, viewModel); HorizontalDivider() } }
    }
}

@Composable
fun ClientCommsTab(detail: ClientDetail, viewModel: SecretaryViewModel) {
    var showLogDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (detail.communications.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text("Žádná komunikace", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(detail.communications) { c -> CommRow(c); HorizontalDivider() }
            }
        }
        Button(onClick = { showLogDialog = true }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("+ Zalogovat komunikaci") }
    }
    if (showLogDialog) {
        LogCommunicationDialog(clientId = detail.client.id, onDismiss = { showLogDialog = false },
            onSave = { type, subject, msg, dir -> viewModel.logCommunication(detail.client.id, null, type, subject, msg, dir); showLogDialog = false })
    }
}

@Composable
fun CommRow(c: Communication, navController: NavHostController? = null) {
    val typeEmoji = when (c.comm_type) { "telefon" -> "📞"; "email" -> "📧"; "sms" -> "💬"; "whatsapp" -> "📱"; "checkatrade" -> "🏠"; "osobne" -> "🤝"; else -> "📋" }
    val dirLabel = if (c.direction == "outbound") "→ Odchozí" else "← Příchozí"
    val dirColor = if (c.direction == "outbound") Color(0xFF2196F3) else Color(0xFF4CAF50)
    Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(typeEmoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.subject ?: "Bez předmětu", fontWeight = FontWeight.SemiBold)
                    if (c.client_name != null) Text("👤 ${c.client_name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { c.client_id?.let { navController?.navigate("client/$it") } })
                    if (c.job_title != null) Text("📋 ${c.job_title}", fontSize = 12.sp, color = Color.Gray)
                }
                Text(dirLabel, fontSize = 11.sp, color = dirColor)
            }
            if (c.message_summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(c.message_summary, fontSize = 13.sp, color = Color.Gray, maxLines = 2)
            }
            Text(c.sent_at?.take(16) ?: c.created_at?.take(16) ?: "", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCommunicationDialog(clientId: Long?, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var commType by remember { mutableStateOf("telefon") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("outbound") }
    var typeExpanded by remember { mutableStateOf(false) }
    val types = listOf("telefon", "email", "sms", "whatsapp", "checkatrade", "osobne")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.logCommunication) },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    TextField(value = commType, onValueChange = {}, readOnly = true, label = { Text(Strings.type) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) })
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { commType = t; typeExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "outbound", onClick = { direction = "outbound" }, label = { Text(Strings.outgoing) })
                    FilterChip(selected = direction == "inbound", onClick = { direction = "inbound" }, label = { Text(Strings.incoming) })
                }
                Spacer(Modifier.height(8.dp))
                TextField(value = subject, onValueChange = { subject = it }, label = { Text(Strings.subject) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = message, onValueChange = { message = it }, label = { Text(Strings.summary) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSave(commType, subject, message, direction) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@Composable
fun ClientNotesTab(detail: ClientDetail) {
    if (detail.notes.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noNotes, color = Color.Gray) }
    } else {
        LazyColumn {
            items(detail.notes) { n ->
                Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.note)
                        Text("${n.created_by ?: "Marek"} · ${n.created_at ?: ""}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun EditClientDialog(client: Client, onDismiss: () -> Unit, onSave: (Map<String, Any?>) -> Unit) {
    var displayName by remember { mutableStateOf(client.display_name) }
    var firstName by remember { mutableStateOf(client.first_name ?: "") }
    var lastName by remember { mutableStateOf(client.last_name ?: "") }
    var title by remember { mutableStateOf(client.title ?: "") }
    var companyName by remember { mutableStateOf(client.company_name ?: "") }
    var emailPrimary by remember { mutableStateOf(client.email_primary ?: "") }
    var emailSecondary by remember { mutableStateOf(client.email_secondary ?: "") }
    var phonePrimary by remember { mutableStateOf(client.phone_primary ?: "") }
    var phoneSecondary by remember { mutableStateOf(client.phone_secondary ?: "") }
    var website by remember { mutableStateOf(client.website ?: "") }
    var billingAddr by remember { mutableStateOf(client.billing_address_line1 ?: "") }
    var billingCity by remember { mutableStateOf(client.billing_city ?: "") }
    var billingPostcode by remember { mutableStateOf(client.billing_postcode ?: "") }
    var isCommercial by remember { mutableStateOf(client.is_commercial) }
    var hourlyRate by remember { mutableStateOf((client.default_hourly_rate ?: 0.0).let { if (it > 0) it.toString() else "" }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.editClient) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    Text(Strings.contact, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(Strings.clientName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text(Strings.firstName) }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text(Strings.lastName) }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(Strings.titleField) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.companyName, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text(Strings.companyName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCommercial, onCheckedChange = { isCommercial = it })
                        Text(Strings.commercial)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.contact, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(value = emailPrimary, onValueChange = { emailPrimary = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = emailSecondary, onValueChange = { emailSecondary = it }, label = { Text(Strings.emailSecondary) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = phonePrimary, onValueChange = { phonePrimary = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = phoneSecondary, onValueChange = { phoneSecondary = it }, label = { Text(Strings.phoneSecondary) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text(Strings.website) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("Sazby", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(value = hourlyRate, onValueChange = { hourlyRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Hodinová sazba (£/h)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text(Strings.billingAddress, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(value = billingAddr, onValueChange = { billingAddr = it }, label = { Text(Strings.billingAddress) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = billingCity, onValueChange = { billingCity = it }, label = { Text(Strings.city) }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = billingPostcode, onValueChange = { billingPostcode = it }, label = { Text(Strings.postcode) }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            onSave(mapOf(
                "display_name" to displayName, "first_name" to firstName.ifBlank { null },
                "last_name" to lastName.ifBlank { null }, "title" to title.ifBlank { null },
                "company_name" to companyName.ifBlank { null }, "is_commercial" to isCommercial,
                "email_primary" to emailPrimary.ifBlank { null }, "email_secondary" to emailSecondary.ifBlank { null },
                "phone_primary" to phonePrimary.ifBlank { null }, "phone_secondary" to phoneSecondary.ifBlank { null },
                "website" to website.ifBlank { null },
                "billing_address_line1" to billingAddr.ifBlank { null },
                "billing_city" to billingCity.ifBlank { null }, "billing_postcode" to billingPostcode.ifBlank { null },
                "default_hourly_rate" to (hourlyRate.toDoubleOrNull() ?: 0.0)
            ))
        }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.addNote) },
        text = { TextField(value = note, onValueChange = { note = it }, label = { Text(Strings.notes) }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
        confirmButton = { Button(onClick = { if (note.isNotBlank()) onSave(note) }, enabled = note.isNotBlank()) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddJobDialog(clients: List<Client>, onDismiss: () -> Unit, onConfirm: (String, Long?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.newJob) },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("${Strings.jobTitle} *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (clients.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        TextField(value = selectedClientName ?: Strings.clients, onValueChange = {}, readOnly = true, label = { Text(Strings.clients) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextField(value = startDate, onValueChange = { startDate = it }, label = { Text("${Strings.plannedStart} (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank()) onConfirm(title, selectedClientId, startDate.ifBlank { null }) }, enabled = title.isNotBlank()) { Text(Strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== JOB DETAIL SCREEN ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(jobId: Long, viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()
    var showStatusDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(jobId) { viewModel.loadJobDetail(jobId) }
    val detail = state.selectedJobDetail

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.job?.job_title ?: "Načítám...") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, "Upravit") }
                    IconButton(onClick = { showStatusDialog = true }) { Icon(Icons.Default.Settings, Strings.changeStatus) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNoteDialog = true }) { Icon(Icons.Default.Add, "Poznámka") }
        }
    ) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.padding(padding).padding(12.dp)) {
                // Info karta
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(Strings.job, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("${Strings.jobNumber}: ${detail.job.job_number ?: "-"}")
                            Text("${Strings.status}: ${Strings.localizeStatus(detail.job.job_status)}")
                            Text("${Strings.plan}: ${detail.job.start_date_planned ?: Strings.noTermin}")
                        }
                    }
                }
                // Ukoly zakazky
                val tasks = detail.tasks
                item { Text("${Strings.tasks} (${tasks.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                if (tasks.isEmpty()) {
                    item { Text(Strings.noTasks, color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                } else {
                    items(tasks) { m ->
                        val title = m["title"]?.toString() ?: ""
                        val status = m["status"]?.toString() ?: ""
                        val prio = m["priority"]?.toString() ?: ""
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = { Text("$status · $prio") },
                            leadingContent = { Text(when(m["task_type"]?.toString()) { "volat"->"📞"; "email"->"📧"; "schuzka"->"📅"; "realizace"->"🔨"; else->"📋" }) }
                        )
                        HorizontalDivider()
                    }
                }
                // Poznamky
                val notes = detail.notes
                if (notes.isNotEmpty()) {
                    item { Text("${Strings.notes} (${notes.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(notes) { n ->
                        Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(n.note)
                                Text("${n.created_by ?: "Marek"} · ${n.created_at ?: ""}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog pro editaci zakazky
    if (showEditDialog && detail != null) {
        EditJobDialog(job = detail.job, onDismiss = { showEditDialog = false },
            onSave = { data -> viewModel.updateJob(jobId, data); showEditDialog = false })
    }
    // Dialog pro poznamku
    if (showNoteDialog) {
        AddJobNoteDialog(onDismiss = { showNoteDialog = false },
            onSave = { note -> viewModel.addJobNote(jobId, note); showNoteDialog = false })
    }
    // Dialog pro zmenu stavu zakazky
    if (showStatusDialog && detail != null) {
        val statuses = listOf("nova","v_reseni","ceka_na_klienta","ceka_na_material","naplanovano","v_realizaci","dokonceno","vyfakturovano","uzavreno","pozastaveno","zruseno")
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text(Strings.changeStatus) },
            text = {
                LazyColumn {
                    items(statuses) { s ->
                        val isCurrent = s == detail.job.job_status
                        ListItem(
                            headlineContent = { Text(Strings.localizeStatus(s), fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                            modifier = Modifier.clickable { viewModel.updateJob(jobId, mapOf("job_status" to s)); showStatusDialog = false }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStatusDialog = false }) { Text(Strings.close) } }
        )
    }
}

// ========== TASK DETAIL SCREEN ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(taskId: String, viewModel: SecretaryViewModel, navController: NavHostController) {
    val task = viewModel.getTaskById(taskId)
    val context = androidx.compose.ui.platform.LocalContext.current
    var showStatusDialog by remember { mutableStateOf(false) }
    var showPrioDialog by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf(task?.result ?: "") }
    var showResultSave by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: "Úkol nenalezen") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (task == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("Úkol nenalezen", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.padding(padding).padding(12.dp)) {
                // Info
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(when(task.taskType) { "volat"->"📞"; "email"->"📧"; "schuzka"->"📅"; "realizace"->"🔨"; "kontrola"->"✅"; "objednat_material"->"🧱"; else->"📋" }, fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("${Strings.type}: ${task.taskType.replace("_"," ")}", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                // Stav a priorita - klikatelne
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStatusDialog = true }, modifier = Modifier.weight(1f)) {
                            Text("${Strings.status}: ${task.status.replace("_"," ")}")
                        }
                        OutlinedButton(onClick = { showPrioDialog = true }, modifier = Modifier.weight(1f)) {
                            val prioColor = when(task.priority) { "kriticka"->Color.Red; "urgentni"->Color(0xFFE65100); "vysoka"->Color(0xFFF57C00); else->Color.Unspecified }
                            Text("${Strings.priority}: ${task.priority}", color = prioColor)
                        }
                    }
                }
                // Detaily
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            if (task.description != null) { Text(Strings.description, fontWeight = FontWeight.SemiBold); Text(task.description!!); Spacer(Modifier.height(8.dp)) }
                            if (task.clientName != null) Text("${Strings.client}: ${task.clientName}")
                            if (task.assignedTo != null) Text("${Strings.assigned}: ${task.assignedTo}")
                            if (task.deadline != null) Text("${Strings.deadline}: ${task.deadline}")
                            if (task.plannedDate != null) Text("${Strings.plan}: ${task.plannedDate}")
                            if (task.createdBy != null) Text("${Strings.createdBy}: ${task.createdBy}")
                            if (task.createdAt != null) Text("${Strings.created}: ${task.createdAt}", fontSize = 12.sp, color = Color.Gray)
                            Text("${Strings.sourceLabel}: ${task.source ?: "manual"}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                // Vysledek
                item {
                    Text(Strings.taskResult, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    TextField(value = resultText, onValueChange = { resultText = it; showResultSave = true }, label = { Text(Strings.whatWasDone) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    if (showResultSave) {
                        Button(onClick = { viewModel.updateTask(taskId, mapOf("result" to resultText)); showResultSave = false }, modifier = Modifier.padding(top = 4.dp)) { Text(Strings.saveResult) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // Akce: Foto + Kalendar
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                            try { context.startActivity(intent) } catch (_: Exception) {}
                            viewModel.savePhotoMetadata(taskId, "photo_${System.currentTimeMillis()}.jpg", "camera")
                        }, modifier = Modifier.weight(1f)) {
                            Text("📷 Foto")
                        }
                        if (task.deadline != null || task.plannedDate != null) {
                            OutlinedButton(onClick = { viewModel.addTaskToCalendar(task) }, modifier = Modifier.weight(1f)) {
                                Text("📅 Do kalendáře")
                            }
                        }
                    }
                }
                // Dokoncit
                if (!task.isCompleted) {
                    item {
                        Button(onClick = { viewModel.completeTask(taskId); navController.popBackStack() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                            Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(Strings.completeTask)
                        }
                    }
                } else {
                    item { Text("✅ ${Strings.taskCompleted}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }

    // Status dialog
    if (showStatusDialog) {
        val statuses = listOf("novy","naplanovany","v_reseni","ceka_na_klienta","ceka_na_material","ceka_na_platbu","hotovo","zruseno","predano_dal")
        AlertDialog(onDismissRequest = { showStatusDialog = false }, title = { Text(Strings.changeStatus) },
            text = { LazyColumn { items(statuses) { s -> ListItem(headlineContent = { Text(s.replace("_"," ")) }, modifier = Modifier.clickable { viewModel.updateTask(taskId, mapOf("status" to s)); showStatusDialog = false }) } } },
            confirmButton = { TextButton(onClick = { showStatusDialog = false }) { Text(Strings.close) } })
    }
    // Priority dialog
    if (showPrioDialog) {
        val prios = listOf("nizka","bezna","vysoka","urgentni","kriticka")
        AlertDialog(onDismissRequest = { showPrioDialog = false }, title = { Text(Strings.changePriority) },
            text = { LazyColumn { items(prios) { p -> ListItem(headlineContent = { Text(p) }, modifier = Modifier.clickable { viewModel.updateTask(taskId, mapOf("priority" to p)); showPrioDialog = false }) } } },
            confirmButton = { TextButton(onClick = { showPrioDialog = false }) { Text(Strings.close) } })
    }
}

@Composable
fun TasksScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf("vse") }
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Load tasks on screen open
    LaunchedEffect(Unit) { viewModel.refreshCrmData() }
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, Strings.newTask)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("${Strings.tasks} (${state.tasks.size})", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("vse" to Strings.allTasks, "novy" to Strings.newTasks, "v_reseni" to Strings.inProgress, "ceka" to Strings.waiting, "hotovo" to Strings.done).forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            val filtered = if (filter == "vse") state.tasks
                else if (filter == "ceka") state.tasks.filter { it.status.startsWith("ceka") }
                else state.tasks.filter { it.status == filter }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Strings.noTasks, color = Color.Gray, fontSize = 18.sp)
                        Text(Strings.addVoiceOrButton, color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn { items(filtered) { t -> TaskRow(t, viewModel); HorizontalDivider() } }
            }
        }
    }
    
    if (showAddDialog) {
        AddTaskDialog(
            clients = state.clients,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, type, prio, clientId, clientName, deadline ->
                viewModel.createTaskManual(title, type, prio, clientId, clientName, deadline)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TaskRow(task: Task, viewModel: SecretaryViewModel, navController: NavHostController? = null, onEdit: ((Task) -> Unit)? = null) {
    val prioColor = when (task.priority) {
        "kriticka" -> Color.Red
        "urgentni" -> Color(0xFFE65100)
        "vysoka" -> Color(0xFFF57C00)
        else -> Color.Unspecified
    }
    val typeEmoji = when (task.taskType) {
        "volat" -> "📞"
        "email" -> "📧"
        "schuzka" -> "📅"
        "objednat_material" -> "🧱"
        "navsteva_klienta" -> "🏠"
        "realizace" -> "🔨"
        "kontrola" -> "✅"
        "fotodokumentace" -> "📷"
        "vytvorit_kalkulaci", "poslat_kalkulaci" -> "💰"
        "reklamace" -> "⚠️"
        "pripomenout_se" -> "🔔"
        else -> "📋"
    }
    val rowMod = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onEdit?.invoke(task) }
    Row(rowMod, verticalAlignment = Alignment.CenterVertically) {
        Text(typeEmoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontWeight = FontWeight.SemiBold, color = if (prioColor != Color.Unspecified) prioColor else Color.Unspecified)
            if (task.clientName != null) Text("Klient: ${task.clientName}", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.localizeStatus(task.status), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                if (task.deadline != null) Text("DL: ${task.deadline}", fontSize = 11.sp, color = Color.Gray)
                if (task.assignedTo != null) Text("→ ${task.assignedTo}", fontSize = 11.sp, color = Color.Gray)
            }
        }
        if (!task.isCompleted) {
            IconButton(onClick = { viewModel.completeTask(task.id) }) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditDialog(task: Task, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var status by remember { mutableStateOf(task.status) }
    var priority by remember { mutableStateOf(task.priority) }
    var notes by remember { mutableStateOf(task.result ?: "") }
    val statuses = listOf("novy", "naplanovany", "v_reseni", "ceka_na_klienta", "ceka_na_material", "hotovo", "zruseno")
    val priorities = listOf("kriticka", "urgentni", "vysoka", "bezna", "nizka")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Upravit úkol") },
        text = {
            Column {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (task.clientName != null) Text("Klient: ${task.clientName}", fontSize = 13.sp, color = Color.Gray)
                if (task.description != null) Text(task.description, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Text("Stav", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(statuses) { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(Strings.localizeStatus(s), fontSize = 10.sp) }) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Priorita", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(priorities) { p -> FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(Strings.localizeStatus(p), fontSize = 10.sp) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Poznámka / výsledek") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(mapOf("status" to status, "priority" to priority, "result" to notes)) }) { Text("Uložit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}

@Composable
fun TasksTab(state: UiState, viewModel: SecretaryViewModel) {
    var filter by remember { mutableStateOf("aktivni") }
    var editTask by remember { mutableStateOf<Task?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("aktivni" to "Aktivní", "ceka" to "Čeká", "hotovo" to "Hotové", "vse" to "Vše").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        val filtered = when (filter) {
            "aktivni" -> state.tasks.filter { !it.isCompleted && it.status != "hotovo" && it.status != "zruseno" }
            "ceka" -> state.tasks.filter { it.status.startsWith("ceka") }
            "hotovo" -> state.tasks.filter { it.isCompleted || it.status == "hotovo" }
            else -> state.tasks
        }
        if (filtered.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Žádné úkoly", color = Color.Gray) } }
        else { LazyColumn { items(filtered) { t -> TaskRow(t, viewModel, onEdit = { editTask = it }); HorizontalDivider() } } }
    }
    if (editTask != null) {
        TaskEditDialog(task = editTask!!, onDismiss = { editTask = null }, onSave = { data ->
            viewModel.updateTask(editTask!!.id, data); editTask = null
        })
    }
}

@Composable
fun CommunicationTab(state: UiState, viewModel: SecretaryViewModel, navController: NavHostController? = null) {
    val comms = remember { mutableStateOf<List<Communication>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf("vše") }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        try {
            val res = viewModel.loadAllCommunications()
            comms.value = res
        } catch (_: Exception) {}
        loading.value = false
    }
    
    val types = listOf("vše", "telefon", "email", "sms", "whatsapp", "checkatrade", "osobně")
    val filtered = if (selectedType == "vše") comms.value else comms.value.filter { it.comm_type == selectedType.replace("ě","e") }
    
    Column(Modifier.fillMaxSize()) {
        // Filter chips
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(types) { type ->
                val emoji = when(type) { "telefon"->"📞"; "email"->"📧"; "sms"->"💬"; "whatsapp"->"📱"; "checkatrade"->"🏠"; "osobně"->"🤝"; else->"📋" }
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(if (type == "vše") "Vše" else "$emoji ${type.replaceFirstChar { it.uppercase() }}") }
                )
            }
        }
        
        if (loading.value) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (selectedType == "vše") "Žádná komunikace" else "Žádná komunikace typu $selectedType", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered) { c -> CommRow(c, navController) }
            }
        }
    }
    
    if (showAddDialog) {
        LogCommunicationDialog(clientId = null, onDismiss = { showAddDialog = false }, onSave = { type, subject, msg, dir ->
            viewModel.logCommunication(null, null, type, subject, msg, dir)
            showAddDialog = false
            scope.launch {
                try { comms.value = viewModel.loadAllCommunications() } catch (_: Exception) {}
            }
        })
    }
}

class SecretaryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var voiceManager: VoiceManager? = null
    private var calendarManager: CalendarManager? = null
    private var contactManager: ContactManager? = null
    private var mailManager: MailManager? = null
    private var settingsManager: SettingsManager? = null
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val token = settingsManager?.accessToken
            val request = if (token != null) {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            } else original
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    internal val api by lazy {
        val url = settingsManager?.apiUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL
        Retrofit.Builder()
        .baseUrl(url)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SecretaryApi::class.java) }

    fun setManagers(vm: VoiceManager?, cm: CalendarManager, ctm: ContactManager, mm: MailManager, sm: SettingsManager) {
        voiceManager = vm; calendarManager = cm; contactManager = ctm; mailManager = mm; settingsManager = sm
        // Clear any stale voice session on startup
        sm.pendingVoiceSessionId = null
        endVoiceSession()
        // Auto-login then check onboarding
        autoLogin()
    }

    private var autoLoginStarted = false
    private fun autoLogin() {
        if (autoLoginStarted) return
        autoLoginStarted = true
        viewModelScope.launch {
            // If we have a saved token, verify it
            val token = settingsManager?.accessToken
            if (token != null) {
                try {
                    val res = api.authMe("Bearer $token")
                    if (res.isSuccessful) {
                        _uiState.value = _uiState.value.copy(loggedIn = true)
                        checkOnboardingStatus()
                        return@launch
                    }
                } catch (_: Exception) {}
                // Token invalid — try refresh
                if (tryRefreshToken()) {
                    _uiState.value = _uiState.value.copy(loggedIn = true)
                    checkOnboardingStatus()
                    return@launch
                }
            }
            // No valid token — show login screen
            _uiState.value = _uiState.value.copy(loggedIn = false)
        }
    }

    fun login(email: String, password: String, onError: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.authLogin(mapOf("email" to email, "password" to password))
                if (res.isSuccessful) {
                    val body = res.body()
                    settingsManager?.accessToken = body?.get("access_token")?.toString()
                    settingsManager?.refreshToken = body?.get("refresh_token")?.toString()
                    _uiState.value = _uiState.value.copy(loggedIn = true)
                    onError(null) // signal success to caller
                    checkOnboardingStatus()
                } else {
                    onError("Neplatné přihlašovací údaje")
                }
            } catch (e: Exception) {
                onError("Chyba připojení: ${e.message}")
            }
        }
    }

    fun logout() {
        settingsManager?.accessToken = null
        settingsManager?.refreshToken = null
        _uiState.value = _uiState.value.copy(loggedIn = false, onboardingComplete = null, tenantConfig = null)
    }

    fun tryRefreshToken(): Boolean {
        val rt = settingsManager?.refreshToken ?: return false
        return try {
            val url = (settingsManager?.apiUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL) + "auth/refresh"
            val body = """{"refresh_token":"$rt"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = okhttp3.Request.Builder().url(url).post(body).build()
            val response = okhttp3.OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val json = org.json.JSONObject(response.body?.string() ?: "{}")
                settingsManager?.accessToken = json.optString("access_token", null)
                Log.d("ViewModel", "Token refreshed")
                true
            } else false
        } catch (e: Exception) {
            Log.e("ViewModel", "Token refresh error", e)
            false
        }
    }
    
    fun getSettingsManager() = settingsManager
    fun getCalendarText(days: Int = 7): String = calendarManager?.getCalendarContext(days) ?: "Kalendář není dostupný"

    fun checkOnboardingStatus() {
        viewModelScope.launch {
            try {
                val res = api.getOnboardingStatus(1)
                if (res.isSuccessful) {
                    val body = res.body()
                    val complete = body?.get("is_complete") as? Boolean ?: false
                    _uiState.value = _uiState.value.copy(onboardingComplete = complete)
                } else {
                    _uiState.value = _uiState.value.copy(onboardingComplete = false)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Onboarding check error", e)
                _uiState.value = _uiState.value.copy(onboardingComplete = true) // fallback: skip onboarding if server unreachable
            }
        }
    }

    fun loadTenantConfig() {
        viewModelScope.launch {
            try {
                val res = api.getTenantConfig(1)
                if (res.isSuccessful) {
                    val config = res.body()
                    _uiState.value = _uiState.value.copy(tenantConfig = config)
                    // Sync default internal language to voice recognition if not manually overridden
                    val defaultLang = config?.get("default_internal_lang")?.toString()
                    if (defaultLang != null && settingsManager != null) {
                        val langMap = mapOf("cs" to "cs-CZ", "en" to "en-GB", "pl" to "pl-PL", "de" to "de-DE", "fr" to "fr-FR", "es" to "es-ES", "sk" to "sk-SK")
                        langMap[defaultLang]?.let { locale ->
                            settingsManager!!.recognitionLanguage = locale
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Tenant config load error", e)
            }
        }
    }

    fun submitOnboarding(
        companyName: String, legalType: String,
        industryGroupId: Long?, industrySubtypeId: Long?,
        internalLangMode: String, customerLangMode: String,
        defaultInternalLang: String, defaultCustomerLang: String,
        workspaceMode: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val languages = mutableListOf<Map<String, Any?>>()
                languages.add(mapOf("code" to defaultInternalLang, "scope" to "internal", "is_default" to true))
                languages.add(mapOf("code" to defaultCustomerLang, "scope" to "customer", "is_default" to true))
                languages.add(mapOf("code" to defaultInternalLang, "scope" to "voice_input", "is_default" to true))
                languages.add(mapOf("code" to defaultCustomerLang, "scope" to "voice_output", "is_default" to true))
                if (internalLangMode == "multi") {
                    for (code in listOf("en","cs","pl")) {
                        if (code != defaultInternalLang) languages.add(mapOf("code" to code, "scope" to "internal", "is_default" to false))
                    }
                }
                if (customerLangMode == "multi") {
                    for (code in listOf("en","cs","pl")) {
                        if (code != defaultCustomerLang) languages.add(mapOf("code" to code, "scope" to "customer", "is_default" to false))
                    }
                }
                val data = mapOf<String, Any?>(
                    "tenant_id" to 1, "company_name" to companyName, "legal_type" to legalType,
                    "industry_group_id" to industryGroupId, "industry_subtype_id" to industrySubtypeId,
                    "internal_language_mode" to internalLangMode, "customer_language_mode" to customerLangMode,
                    "default_internal_language_code" to defaultInternalLang,
                    "default_customer_language_code" to defaultCustomerLang,
                    "workspace_mode" to workspaceMode, "languages" to languages
                )
                val res = api.companySetup(data)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(onboardingComplete = true)
                    onSuccess()
                } else { onError("Server: ${res.code()} ${res.message()}") }
            } catch (e: Exception) { onError(e.message ?: "Chyba") }
        }
    }

    suspend fun loadAllCommunications(): List<Communication> {
        return try {
            val res = api.getCommunications()
            if (res.isSuccessful) res.body() ?: emptyList() else emptyList()
        } catch (e: Exception) { Log.e("ViewModel", "Load comms error", e); emptyList() }
    }

    fun takePhotoForTask(taskId: String, taskTitle: String) {
        // Store pending photo metadata, open camera via intent
        _uiState.value = _uiState.value.copy(pendingPhotoTaskId = taskId, pendingPhotoTaskTitle = taskTitle)
    }

    fun savePhotoMetadata(taskId: String, filename: String, filePath: String) {
        viewModelScope.launch {
            try {
                api.addPhoto(mapOf("entity_type" to "task", "entity_id" to taskId, "filename" to filename, "file_path" to filePath, "description" to "Fotodokumentace"))
            } catch (e: Exception) { Log.e("ViewModel", "Save photo error", e) }
        }
    }

    fun addTaskToCalendar(task: Task) {
        val dateStr = task.deadline ?: task.plannedDate ?: return
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return
            val startMs = date.time + 9 * 3600000L // 9:00 rano
            val endMs = startMs + 3600000L // 1 hodina
            calendarManager?.addEvent(task.title, startMs, endMs)
            setStatus("Přidáno do kalendáře: ${task.title}")
        } catch (e: Exception) { Log.e("ViewModel", "Calendar add error", e); setStatus("Chyba při přidávání do kalendáře") }
    }

    fun resumeVoiceSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("session_id" to sessionId, "tenant_id" to 1)
                val res = api.voiceSessionResume(data)
                if (res.isSuccessful) {
                    val body = res.body() ?: return@launch
                    val step = body["step"]?.toString() ?: "client"
                    val prompt = body["prompt"]?.toString() ?: ""
                    if (step != "done" && step != "error") {
                        _uiState.value = _uiState.value.copy(
                            voiceSessionId = sessionId, voiceSessionStep = step,
                            voiceSessionPrompt = prompt, isVoiceSessionActive = true
                        )
                        voiceManager?.speak(prompt, expectReply = true)
                    }
                }
            } catch (e: Exception) { Log.e("ViewModel", "Resume session error", e) }
        }
    }

    fun completeTask(taskId: String) {
        val tasks = _uiState.value.tasks.map { if (it.id == taskId) it.copy(isCompleted = true, status = "hotovo") else it }
        _uiState.value = _uiState.value.copy(tasks = tasks)
        viewModelScope.launch {
            try { api.updateTask(taskId, mapOf("is_completed" to true, "status" to "hotovo")) }
            catch (e: Exception) { Log.e("Task", "Complete sync error", e) }
        }
    }

    fun createTaskManual(title: String, taskType: String, priority: String, clientId: Long?, clientName: String?, deadline: String?) {
        val taskId = UUID.randomUUID().toString()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val newTask = Task(
            id = taskId, title = title, taskType = taskType, priority = priority,
            status = "novy", createdAt = now, deadline = deadline,
            clientId = clientId, clientName = clientName, createdBy = "Marek"
        )
        _uiState.value = _uiState.value.copy(tasks = _uiState.value.tasks + newTask)
        viewModelScope.launch {
            try {
                api.createTask(mapOf<String, Any?>(
                    "id" to taskId, "title" to title, "task_type" to taskType,
                    "priority" to priority, "deadline" to deadline,
                    "client_id" to clientId, "client_name" to clientName,
                    "created_by" to "Marek", "source" to "manualne"
                ))
            } catch (e: Exception) { Log.e("Task", "Create sync error", e) }
        }
    }

    fun setStatus(status: String) { _uiState.value = _uiState.value.copy(status = status) }
    fun setListening(isL: Boolean) { _uiState.value = _uiState.value.copy(isListening = isL) }
    fun startListening() { voiceManager?.startListening() }

    fun updateContext(id: Long?, type: String?) {
        _uiState.value = _uiState.value.copy(contextEntityId = id, contextType = type)
    }

    fun refreshCrmData() {
        viewModelScope.launch {
            try {
                val cl = api.getClients(); if (cl.isSuccessful) _uiState.value = _uiState.value.copy(clients = cl.body() ?: emptyList())
                val pr = api.getProperties(); if (pr.isSuccessful) _uiState.value = _uiState.value.copy(properties = pr.body() ?: emptyList())
                val jb = api.getJobs(); if (jb.isSuccessful) _uiState.value = _uiState.value.copy(jobs = jb.body() ?: emptyList())
                val ld = api.getLeads(); if (ld.isSuccessful) _uiState.value = _uiState.value.copy(leads = ld.body() ?: emptyList())
                val qt = api.getQuotes(); if (qt.isSuccessful) _uiState.value = _uiState.value.copy(quotes = qt.body() ?: emptyList())
                val iv = api.getInvoices(); if (iv.isSuccessful) _uiState.value = _uiState.value.copy(invoices = iv.body() ?: emptyList())
                loadTasksFromServer()
                loadWorkReportsFromServer()
            } catch (e: Exception) { Log.e("ViewModel", "Refresh Error", e) }
        }
    }

    private fun refreshCrmDataKeepTasks() {
        viewModelScope.launch {
            try {
                val cl = api.getClients(); if (cl.isSuccessful) _uiState.value = _uiState.value.copy(clients = cl.body() ?: emptyList())
                val pr = api.getProperties(); if (pr.isSuccessful) _uiState.value = _uiState.value.copy(properties = pr.body() ?: emptyList())
                val jb = api.getJobs(); if (jb.isSuccessful) _uiState.value = _uiState.value.copy(jobs = jb.body() ?: emptyList())
                val ld = api.getLeads(); if (ld.isSuccessful) _uiState.value = _uiState.value.copy(leads = ld.body() ?: emptyList())
                val qt = api.getQuotes(); if (qt.isSuccessful) _uiState.value = _uiState.value.copy(quotes = qt.body() ?: emptyList())
                val iv = api.getInvoices(); if (iv.isSuccessful) _uiState.value = _uiState.value.copy(invoices = iv.body() ?: emptyList())
                loadTasksFromServer()
                loadWorkReportsFromServer()
            } catch (e: Exception) { Log.e("ViewModel", "Refresh Error", e) }
        }
    }

    private suspend fun loadWorkReportsFromServer() {
        try {
            val res = api.getWorkReports()
            if (res.isSuccessful) {
                val raw = res.body() ?: emptyList()
                val reports = raw.map { m ->
                    WorkReport(
                        id = (m["id"] as? Number)?.toLong() ?: 0,
                        client_id = (m["client_id"] as? Number)?.toLong(),
                        client_name = m["client_name"]?.toString(),
                        work_date = m["work_date"]?.toString(),
                        total_hours = (m["total_hours"] as? Number)?.toDouble() ?: 0.0,
                        total_price = (m["total_price"] as? Number)?.toDouble() ?: 0.0,
                        currency = m["currency"]?.toString() ?: "GBP",
                        notes = m["notes"]?.toString(),
                        status = m["status"]?.toString() ?: "draft",
                        input_type = m["input_type"]?.toString(),
                        created_at = m["created_at"]?.toString(),
                        workers = (m["workers"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList(),
                        entries = (m["entries"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList(),
                        waste = (m["waste"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList(),
                        materials = (m["materials"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
                    )
                }
                _uiState.value = _uiState.value.copy(workReports = reports)
            }
        } catch (e: Exception) { Log.e("ViewModel", "Work reports load error", e) }
    }
    private suspend fun loadTasksFromServer() {
        try {
            val tr = api.getTasks()
            if (tr.isSuccessful) {
                val serverTasks = (tr.body() ?: emptyList()).map { m ->
                    Task(
                        id = m["id"]?.toString() ?: "",
                        title = m["title"]?.toString() ?: "",
                        description = m["description"]?.toString(),
                        taskType = m["task_type"]?.toString() ?: "interni_poznamka",
                        status = m["status"]?.toString() ?: "novy",
                        priority = m["priority"]?.toString() ?: "bezna",
                        createdAt = m["created_at"]?.toString(),
                        deadline = m["deadline"]?.toString(),
                        plannedDate = m["planned_date"]?.toString(),
                        assignedTo = m["assigned_to"]?.toString(),
                        clientName = m["client_name"]?.toString(),
                        clientId = (m["client_id"] as? Number)?.toLong(),
                        createdBy = m["created_by"]?.toString(),
                        result = m["result"]?.toString(),
                        isCompleted = m["is_completed"] as? Boolean ?: false
                    )
                }
                // Merge: server tasks + local-only tasks (not yet synced)
                val serverIds = serverTasks.map { it.id }.toSet()
                val localOnly = _uiState.value.tasks.filter { it.id !in serverIds }
                _uiState.value = _uiState.value.copy(tasks = serverTasks + localOnly)
            }
        } catch (e: Exception) { Log.e("ViewModel", "Tasks load error", e) }
    }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                val res = api.getSettings()
                if (res.isSuccessful) _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTED, systemSettings = res.body() ?: emptyMap(), status = "Pripojeno")
                else _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED, status = "Server neodpovida")
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED, status = "Odpojeno") }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.TESTING, status = "Testuji...")
            try {
                val res = api.getSettings()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        systemSettings = res.body() ?: emptyMap(),
                        status = "Pripojeno"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        status = "Server neodpovida"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    status = "Odpojeno"
                )
            }
        }
    }

    fun loadClientDetail(clientId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedClientDetail = null)
            try {
                val res = api.getClientDetail(clientId)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(selectedClientDetail = res.body())
                }
            } catch (e: Exception) { Log.e("ViewModel", "Detail Error", e) }
        }
    }

    fun createClientManual(name: String, email: String, phone: String) {
        viewModelScope.launch {
            try {
                val data = mapOf("name" to name, "email" to email, "phone" to phone)
                val res = api.createClient(data)
                if (res.isSuccessful) refreshCrmData()
            } catch (e: Exception) { }
        }
    }

    fun updateClient(clientId: Long, data: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                val res = api.updateClient(clientId, data)
                if (res.isSuccessful) { loadClientDetail(clientId); refreshCrmData() }
            } catch (e: Exception) { Log.e("ViewModel", "Update client error", e) }
        }
    }

    fun addClientNote(clientId: Long, note: String) {
        viewModelScope.launch {
            try {
                val res = api.addClientNote(clientId, mapOf("note" to note))
                if (res.isSuccessful) loadClientDetail(clientId)
            } catch (e: Exception) { Log.e("ViewModel", "Add note error", e) }
        }
    }

    fun syncPhoneContacts(context: Context, filterUk: Boolean = true) {
        viewModelScope.launch {
            try {
                val cm = ContactManager(context)
                val contacts = cm.getContactsWithEmail()
                Log.d("ViewModel", "Syncing ${contacts.size} phone contacts to server (filterUk=$filterUk)")
                val res = api.syncContacts(mapOf("contacts" to contacts, "filter_uk" to filterUk))
                if (res.isSuccessful) {
                    val body = res.body()
                    val created = body?.get("created") ?: 0
                    val skipped = body?.get("skipped") ?: 0
                    Log.d("ViewModel", "Sync done: created=$created, skipped=$skipped")
                    refreshCrmData()
                }
            } catch (e: Exception) { Log.e("ViewModel", "Sync contacts error", e) }
        }
    }

    fun updateLead(leadId: Long, data: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                api.updateLead(leadId, data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Update lead error", e) }
        }
    }

    fun createInvoiceManual(clientId: Long?, amount: Double, dueDate: String?) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("client_id" to clientId, "grand_total" to amount, "due_date" to dueDate)
                api.createInvoice(data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Create invoice error", e) }
        }
    }

    fun updateInvoiceStatus(invoiceId: Long, status: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("status" to status)
                api.updateInvoice(invoiceId, data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Update invoice error", e) }
        }
    }

    fun createWorkReportManual(clientId: Long?, workDate: String, totalHours: Double, totalPrice: Double, notes: String?) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("tenant_id" to 1, "client_id" to clientId, "work_date" to workDate,
                    "total_hours" to totalHours, "total_price" to totalPrice, "notes" to notes, "input_type" to "manual", "status" to "draft")
                api.createWorkReport(data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Create work report error", e) }
        }
    }

    fun addJobNote(jobId: Long, note: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("note" to note, "created_by" to "Marek")
                api.addJobNote(jobId, data)
                loadJobDetail(jobId)
            } catch (e: Exception) { Log.e("ViewModel", "Add job note error", e) }
        }
    }

    fun createQuote(clientId: Long?, title: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("client_id" to clientId, "quote_title" to title)
                api.createQuote(data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Create quote error", e) }
        }
    }

    fun approveQuote(quoteId: Long, createJob: Boolean = true) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("create_job" to createJob)
                api.approveQuote(quoteId, data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Approve quote error", e) }
        }
    }

    fun addQuoteItem(quoteId: Long, description: String, qty: Double, price: Double) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("description" to description, "quantity" to qty, "unit_price" to price)
                api.addQuoteItem(quoteId, data)
                refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Add quote item error", e) }
        }
    }

    fun createJobManual(title: String, clientId: Long?, startDate: String?) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("title" to title, "client_id" to clientId, "start_date" to startDate)
                val res = api.createJob(data)
                if (res.isSuccessful) refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Create job error", e) }
        }
    }

    fun loadJobDetail(jobId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedJobDetail = null)
            try {
                val res = api.getJobDetail(jobId)
                if (res.isSuccessful) _uiState.value = _uiState.value.copy(selectedJobDetail = res.body())
            } catch (e: Exception) { Log.e("ViewModel", "Job detail error", e) }
        }
    }

    fun updateJob(jobId: Long, data: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                val res = api.updateJob(jobId, data)
                if (res.isSuccessful) { loadJobDetail(jobId); refreshCrmData() }
            } catch (e: Exception) { Log.e("ViewModel", "Update job error", e) }
        }
    }

    fun updateTask(taskId: String, data: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                val res = api.updateTask(taskId, data)
                if (res.isSuccessful) {
                    // Update local state
                    val tasks = _uiState.value.tasks.map { t ->
                        if (t.id == taskId) {
                            t.copy(
                                status = data["status"]?.toString() ?: t.status,
                                priority = data["priority"]?.toString() ?: t.priority,
                                result = data["result"]?.toString() ?: t.result,
                                assignedTo = data["assigned_to"]?.toString() ?: t.assignedTo,
                                isCompleted = data["is_completed"] as? Boolean ?: t.isCompleted
                            )
                        } else t
                    }
                    _uiState.value = _uiState.value.copy(tasks = tasks)
                }
            } catch (e: Exception) { Log.e("ViewModel", "Update task error", e) }
        }
    }

    fun getTaskById(taskId: String): Task? = _uiState.value.tasks.find { it.id == taskId }

    fun createLeadManual(name: String, source: String, email: String, phone: String, description: String) {
        viewModelScope.launch {
            try {
                val data = mapOf("name" to name, "source" to source, "email" to email, "phone" to phone, "description" to description)
                val res = api.createLead(data)
                if (res.isSuccessful) refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Create lead error", e) }
        }
    }

    fun convertLeadToClient(leadId: Long, name: String, email: String, phone: String) {
        viewModelScope.launch {
            try {
                val data = mapOf("name" to name, "email" to email, "phone" to phone)
                val res = api.convertLeadToClient(leadId, data)
                if (res.isSuccessful) refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Convert lead error", e) }
        }
    }

    fun convertLeadToJob(leadId: Long, title: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("title" to title)
                val res = api.convertLeadToJob(leadId, data)
                if (res.isSuccessful) refreshCrmData()
            } catch (e: Exception) { Log.e("ViewModel", "Convert lead to job error", e) }
        }
    }

    fun logCommunication(clientId: Long?, jobId: Long?, commType: String, subject: String, message: String, direction: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("client_id" to clientId, "job_id" to jobId, "comm_type" to commType, "subject" to subject, "message" to message, "direction" to direction)
                api.logCommunication(data)
                if (clientId != null) loadClientDetail(clientId)
            } catch (e: Exception) { Log.e("ViewModel", "Log comm error", e) }
        }
    }

    fun addPhotoMetadata(entityType: String, entityId: String, filename: String, description: String, filePath: String) {
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("entity_type" to entityType, "entity_id" to entityId, "filename" to filename, "description" to description, "file_path" to filePath)
                api.addPhoto(data)
            } catch (e: Exception) { Log.e("ViewModel", "Add photo error", e) }
        }
    }

    // === VOICE WORK REPORT SESSION ===
    fun startWorkReportSession() {
        viewModelScope.launch {
            try {
                val lang = settingsManager?.appLanguage ?: "en"
                val data = mapOf<String, Any?>("tenant_id" to 1, "language" to lang, "work_date" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))
                val res = api.voiceSessionStart(data)
                if (res.isSuccessful) {
                    val body = res.body() ?: return@launch
                    val sid = body["session_id"]?.toString() ?: return@launch
                    val prompt = body["prompt"]?.toString() ?: ""
                    val step = body["step"]?.toString() ?: "client"
                    settingsManager?.pendingVoiceSessionId = sid
                    _uiState.value = _uiState.value.copy(
                        voiceSessionId = sid, voiceSessionStep = step,
                        voiceSessionPrompt = prompt, isVoiceSessionActive = true,
                        voiceSessionSummary = null, voiceSessionWhatsApp = null
                    )
                    voiceManager?.speak(prompt, expectReply = true)
                }
            } catch (e: Exception) { Log.e("ViewModel", "Start voice session error", e) }
        }
    }

    fun processVoiceSessionInput(text: String) {
        val sid = _uiState.value.voiceSessionId ?: return
        viewModelScope.launch {
            try {
                val data = mapOf<String, Any?>("session_id" to sid, "text" to text, "tenant_id" to 1)
                val res = api.voiceSessionInput(data)
                if (res.isSuccessful) {
                    val body = res.body() ?: return@launch
                    val step = body["step"]?.toString() ?: ""
                    val prompt = body["prompt"]?.toString() ?: ""
                    if (step == "done") {
                        settingsManager?.pendingVoiceSessionId = null
                        val summary = body["summary"]?.toString() ?: ""
                        val whatsapp = body["whatsapp_message"]?.toString() ?: ""
                        _uiState.value = _uiState.value.copy(
                            voiceSessionStep = "done", voiceSessionPrompt = prompt,
                            voiceSessionSummary = summary, voiceSessionWhatsApp = whatsapp,
                            isVoiceSessionActive = false
                        )
                        voiceManager?.speak(prompt, expectReply = false)
                    } else {
                        _uiState.value = _uiState.value.copy(voiceSessionStep = step, voiceSessionPrompt = prompt)
                        voiceManager?.speak(prompt, expectReply = true)
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Voice session input error", e)
                voiceManager?.speak("Chyba spojení se serverem.", expectReply = true)
            }
        }
    }

    fun endVoiceSession() {
        settingsManager?.pendingVoiceSessionId = null
        _uiState.value = _uiState.value.copy(
            voiceSessionId = null, voiceSessionStep = null, voiceSessionPrompt = null,
            voiceSessionSummary = null, voiceSessionWhatsApp = null, isVoiceSessionActive = false
        )
    }

    fun shareWhatsApp(context: android.content.Context, message: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(android.content.Intent.EXTRA_TEXT, message)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, message)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
        }
    }

    fun onVoiceInput(text: String) {
        // Client-side commands — handle before sending to GPT
        val lower = text.lowercase().trim()
        if (lower.contains("odhlásit") || lower.contains("odhlasit") || lower == "logout") {
            val msg = ChatMessage("assistant", "Odhlašuji vás. Na shledanou!")
            _uiState.value = _uiState.value.copy(
                history = (_uiState.value.history + ChatMessage("user", text) + msg).takeLast(30),
                lastAiReply = msg.content
            )
            voiceManager?.speak("Odhlašuji vás. Na shledanou!")
            viewModelScope.launch {
                kotlinx.coroutines.delay(2500) // wait for TTS
                logout()
            }
            return
        }
        // If voice work report session is active, redirect to session
        if (_uiState.value.isVoiceSessionActive && _uiState.value.voiceSessionId != null) {
            processVoiceSessionInput(text)
            return
        }
        val currentState = _uiState.value
        val newUserMessage = ChatMessage("user", text)
        val updatedHistory = (currentState.history + newUserMessage).takeLast(30)
        
        _uiState.value = currentState.copy(isListening = false, status = Strings.processing, history = updatedHistory)
        viewModelScope.launch {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val nowStr = sdf.format(Date())
                val res = api.processMessage(MessageRequest(
                    text = text, 
                    history = updatedHistory,
                    context_entity_id = currentState.contextEntityId,
                    context_type = currentState.contextType,
                    internal_language = settingsManager?.recognitionLanguage ?: "cs-CZ",
                    calendar_context = calendarManager?.getCalendarContext(),
                    current_datetime = nowStr
                ))
                if (res.isSuccessful) {
                    res.body()?.let { response ->
                        val newAssistantMessage = ChatMessage("assistant", response.reply_cs)
                        _uiState.value = _uiState.value.copy(
                            lastAiReply = response.reply_cs,
                            status = if (response.is_question) "${Strings.listening}..." else Strings.waitingForCommand,
                            history = _uiState.value.history + newAssistantMessage
                        )
                        handleAction(response)
                        if (response.action_type != "SEARCH_CONTACTS" && response.action_type != "LIST_CALENDAR_EVENTS" && response.action_type != "START_WORK_REPORT") {
                            voiceManager?.speak(response.reply_cs, expectReply = response.is_question)
                        }
                        // Refresh CRM data ale NEZNICIT lokalni tasky
                        refreshCrmDataKeepTasks()
                    }
                } else {
                    Log.e("ViewModel", "API Error: ${res.code()}")
                    _uiState.value = _uiState.value.copy(status = "Chyba serveru ${res.code()}")
                }
            } catch (e: Exception) { 
                Log.e("ViewModel", "Network Error", e)
                _uiState.value = _uiState.value.copy(status = "Chyba sítě")
                voiceManager?.speak("Marku, nemůžu se spojit se serverem.")
            }
        }
    }

    private fun handleAction(response: AssistantResponse) {
        when (response.action_type) {
            "REFRESH" -> { refreshCrmData() }
            "SEARCH_CONTACTS" -> {
                val query = response.action_data?.get("query") as? String ?: return
                val results = contactManager?.searchContact(query) ?: emptyList()
                if (results.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(contactResults = results)
                    val names = results.joinToString(", ") { it["name"] ?: "" }
                    onVoiceInput("SYSTÉM: Našla jsem tyto kontakty: $names. Přečti je uživateli a nabídni volání.")
                } else {
                    voiceManager?.speak("V kontaktech jsem nikoho pro '$query' nenašla.")
                }
            }
            "ADD_CALENDAR_EVENT" -> {
                val data = response.action_data ?: return
                val title = data["title"] as? String ?: "Schůzka"
                val startTimeStr = data["start_time"] as? String ?: return
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = sdf.parse(startTimeStr) ?: return
                    val duration = (data["duration"] as? Double)?.toLong() ?: 60L
                    calendarManager?.addEvent(title, date.time, date.time + (duration * 60000))
                } catch (e: Exception) { Log.e("Action", "Calendar error", e) }
            }
            "DELETE_CALENDAR_EVENT" -> {
                val data = response.action_data ?: return
                val eventId = (data["event_id"] as? Double)?.toLong()
                if (eventId != null) {
                    calendarManager?.deleteEvent(eventId)
                } else {
                    val title = data["title"] as? String
                    if (title != null) calendarManager?.deleteEventByName(title)
                }
            }
            "UPDATE_CALENDAR_EVENT" -> {
                val data = response.action_data ?: return
                val eventId = (data["event_id"] as? Double)?.toLong() ?: return
                val title = data["title"] as? String
                val startTimeStr = data["start_time"] as? String
                
                var start: Long? = null
                var end: Long? = null
                
                if (startTimeStr != null) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val date = sdf.parse(startTimeStr)
                        start = date?.time
                        val duration = (data["duration"] as? Double)?.toLong() ?: 60L
                        if (start != null) end = start + (duration * 60000)
                    } catch (e: Exception) { }
                }
                calendarManager?.updateEvent(eventId, title, start, end)
            }
            "SEND_EMAIL" -> {
                val to = response.action_data?.get("to") as? String ?: return
                val sub = response.action_data?.get("subject") as? String ?: ""
                val body = response.action_data?.get("body") as? String ?: ""
                mailManager?.sendEmail(to, sub, body)
            }
            "LIST_CALENDAR_EVENTS" -> {
                val days = (response.action_data?.get("days") as? Double)?.toInt() ?: 7
                val context = calendarManager?.getCalendarContext(days) ?: "Kalendar neni dostupny."
                _uiState.value = _uiState.value.copy(lastAiReply = context)
                voiceManager?.speak(context, expectReply = true)
            }
            "MODIFY_CALENDAR_EVENT" -> {
                val data = response.action_data ?: return
                val eventTitle = data["event_title"] as? String ?: return
                val newTitle = data["new_title"] as? String
                val newTime = data["new_start_time"] as? String
                calendarManager?.deleteEventByName(eventTitle)
                if (newTitle != null || newTime != null) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val time = if (newTime != null) sdf.parse(newTime)?.time ?: System.currentTimeMillis() else System.currentTimeMillis()
                        calendarManager?.addEvent(newTitle ?: eventTitle, time, time + 3600000)
                    } catch (e: Exception) { Log.e("Action", "Modify calendar", e) }
                }
            }
            "CREATE_TASK" -> {
                // Server uz task ulozil do DB a vratil ho v action_data
                val data = response.action_data ?: emptyMap()
                val newTask = Task(
                    id = data["id"]?.toString() ?: UUID.randomUUID().toString(),
                    title = data["title"]?.toString() ?: "Nový úkol",
                    description = data["description"]?.toString(),
                    taskType = data["task_type"]?.toString() ?: "interni_poznamka",
                    status = data["status"]?.toString() ?: "novy",
                    priority = data["priority"]?.toString() ?: "bezna",
                    createdAt = data["created_at"]?.toString(),
                    deadline = data["deadline"]?.toString(),
                    assignedTo = data["assigned_to"]?.toString(),
                    clientName = data["client_name"]?.toString(),
                    clientId = (data["client_id"] as? Number)?.toLong(),
                    createdBy = data["created_by"]?.toString() ?: "Marek"
                )
                _uiState.value = _uiState.value.copy(tasks = _uiState.value.tasks + newTask)
            }
            "CALL_CONTACT" -> {
                val phone = response.action_data?.get("phone") as? String ?: return
                _uiState.value = _uiState.value.copy(pendingCall = phone)
            }
            "LIST_TASKS" -> {
                refreshCrmData()
            }
            "START_WORK_REPORT" -> {
                startWorkReportSession()
            }
            "IMPORT_DATABASE" -> {
                val source = response.action_data?.get("source") as? String ?: ""
                val table = response.action_data?.get("table") as? String ?: "clients"
                _uiState.value = _uiState.value.copy(pendingImport = mapOf("source" to source, "table" to table))
            }
        }
    }

    fun clearHistory() { _uiState.value = _uiState.value.copy(history = emptyList(), lastAiReply = Strings.waitingForCommand, contactResults = emptyList()) }

    fun updateDefaultRates(rates: Map<String, Any?>) {
        viewModelScope.launch {
            try { api.updateDefaultRates(rates) } catch (_: Exception) {}
        }
    }

    fun createInvoiceFromWorkReport(workReportId: Long, onResult: (Map<String, Any?>?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.createInvoiceFromWorkReport(mapOf("work_report_id" to workReportId))
                if (res.isSuccessful) onResult(res.body()) else onResult(null)
            } catch (_: Exception) { onResult(null) }
        }
    }

    fun batchInvoiceFromWorkReports(ids: List<Long>, onResult: (Map<String, Any?>?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.batchInvoiceFromWorkReports(mapOf("work_report_ids" to ids))
                if (res.isSuccessful) onResult(res.body()) else onResult(null)
            } catch (_: Exception) { onResult(null) }
        }
    }

    fun changeLanguage(langCode: String) {
        settingsManager?.appLanguage = langCode
        Strings.setLanguage(langCode)
        // Update recognition language to match
        settingsManager?.recognitionLanguage = Strings.getRecognitionLocale()
        _uiState.value = _uiState.value.copy(lastAiReply = Strings.waitingForCommand)
    }
    fun resetSettings() { settingsManager?.resetAll(); setStatus("Nastaveni obnovena") }
    fun exportCrmData() { viewModelScope.launch { setStatus("Export neni v teto verzi") } }
    fun triggerManualImport() {
        val path = settingsManager?.importFilePath ?: ""
        val table = settingsManager?.importTargetTable ?: "clients"
        if (path.isBlank()) { setStatus("Cesta neni nastavena"); return }
        _uiState.value = _uiState.value.copy(pendingImport = mapOf("source" to path, "table" to table))
    }
    fun cancelImport() { _uiState.value = _uiState.value.copy(pendingImport = null) }
    fun confirmImport() { _uiState.value = _uiState.value.copy(pendingImport = null); setStatus("Import spusten") }

    fun toggleBackground() {
        val current = _uiState.value.isBackgroundActive
        _uiState.value = _uiState.value.copy(isBackgroundActive = !current)
        if (current) {
            voiceManager?.stop()
            setStatus("Pozadi vypnuto")
        } else {
            voiceManager?.startHotwordLoop()
            setStatus("Posloucham na pozadi")
        }
    }

    fun restartVoice() {
        settingsManager?.pendingVoiceSessionId = null
        endVoiceSession()
        voiceManager?.stop()
        setStatus("Restartuji...")
        voiceManager?.startHotwordLoop()
        setStatus("Pripravena")
    }

    private var onShutdown: (() -> Unit)? = null
    fun setOnShutdown(callback: () -> Unit) { onShutdown = callback }
    fun shutdownApp() {
        settingsManager?.pendingVoiceSessionId = null
        endVoiceSession()
        voiceManager?.stop()
        onShutdown?.invoke()
    }
}

data class UiState(
    val isListening: Boolean = false, 
    val status: String = "Připravena", 
    val lastAiReply: String = "Čekám na váš povel...",
    val history: List<ChatMessage> = emptyList(),
    val contactResults: List<Map<String, String>> = emptyList(),
    val systemSettings: Map<String, Any> = emptyMap(),
    val tasks: List<Task> = emptyList(), 
    val clients: List<Client> = emptyList(), 
    val properties: List<Property> = emptyList(),
    val jobs: List<Job> = emptyList(), 
    val waste: List<WasteLoad> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val leads: List<Lead> = emptyList(),
    val workReports: List<WorkReport> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val selectedClientDetail: ClientDetail? = null,
    val selectedJobDetail: JobDetail? = null,
    val contextEntityId: Long? = null,
    val contextType: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val pendingImport: Map<String, String>? = null,
    val pendingCall: String? = null,
    val pendingPhotoTaskId: String? = null,
    val pendingPhotoTaskTitle: String? = null,
    val isBackgroundActive: Boolean = true,
    val voiceSessionId: String? = null,
    val voiceSessionStep: String? = null,
    val voiceSessionPrompt: String? = null,
    val voiceSessionSummary: String? = null,
    val voiceSessionWhatsApp: String? = null,
    val isVoiceSessionActive: Boolean = false,
    val onboardingComplete: Boolean? = null,
    val tenantConfig: Map<String, Any?>? = null,
    val loggedIn: Boolean? = null
)
