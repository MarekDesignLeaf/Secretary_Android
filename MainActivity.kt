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
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
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
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startAndBindVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarManager = CalendarManager(this)
        contactManager = ContactManager(this)
        mailManager = MailManager(this)
        settingsManager = SettingsManager(this)
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

        setContent {
            val vm: SecretaryViewModel = viewModel()
            val navController = rememberNavController()
            
            LaunchedEffect(Unit) {
                vm.setManagers(null, calendarManager, contactManager, mailManager, settingsManager)
            }

            MainAppScaffold(vm, navController)
        }
    }

    private fun startAndBindVoiceService() {
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
        },
        floatingActionButton = {
            if (currentRoute == Screen.Crm.route) {
                FloatingActionButton(onClick = { showAddClientDialog = true }) {
                    Icon(Icons.Default.Add, null)
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
            composable(Screen.Inbox.route) { 
                LaunchedEffect(Unit) { viewModel.updateContext(null, null) }
                InboxScreen(viewModel) 
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
        }
    }
}

@Composable
fun AddClientDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nový klient") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Jméno") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, email, phone) }) { Text("Uložit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}

@Composable
fun HomeScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.status.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (state.isListening) Color.Red else Color.Gray, modifier = Modifier.height(48.dp))
        Card(Modifier.fillMaxWidth().height(120.dp), colors = CardDefaults.cardColors(containerColor = if (state.isListening) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant)) {
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
                                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact["phone"]}"))
                                }) { Icon(Icons.Default.Call, null) } 
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { viewModel.startListening() }, Modifier.size(120.dp), shape = CircleShape) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(48.dp))
        }
        
        Spacer(Modifier.height(24.dp))
        Text("HISTORIE", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
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
    val tabs = listOf("Klienti", "Nemovitosti", "Zakázky", "Odpady", "Finance")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 12.sp) }) }
        }
        Box(Modifier.padding(16.dp).weight(1f)) {
            when (selectedTab) {
                0 -> CrmDataList(state.clients, Icons.Default.Person) { navController.navigate("client/${it.id}") }
                1 -> CrmDataList(state.properties, Icons.Default.Home) { }
                2 -> CrmDataList(state.jobs, Icons.Default.Build) { }
                3 -> CrmDataList(state.waste, Icons.Default.Delete) { }
                4 -> CrmDataList(state.invoices, Icons.Default.ShoppingCart) { }
            }
        }
    }
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
    LaunchedEffect(clientId) { viewModel.loadClientDetail(clientId) }
    val detail = state.selectedClientDetail

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(detail?.client?.display_name ?: "Načítám...") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }
        )
        
        if (detail == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.padding(16.dp)) {
                item {
                    Text("Kontaktní údaje", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Email: ${detail.client.email_primary ?: "Neuveden"}")
                    Text("Telefon: ${detail.client.phone_primary ?: "Neuveden"}")
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Nemovitosti (${detail.properties.size})", fontWeight = FontWeight.Bold)
                }
                items(detail.properties) { prop ->
                    ListItem(headlineContent = { Text(prop.property_name) }, supportingContent = { Text(prop.address_line1) })
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Poslední komunikace", fontWeight = FontWeight.Bold)
                }
                items(detail.communications) { comm ->
                    ListItem(headlineContent = { Text(comm.subject ?: "Bez předmětu") }, supportingContent = { Text(comm.message_summary) })
                }
            }
        }
    }
}

@Composable
fun TasksScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Úkoly", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        LazyColumn { items(state.tasks) { t -> ListItem(headlineContent = { Text(t.title) }, supportingContent = { Text(t.priority) }); HorizontalDivider() } }
    }
}

@Composable
fun InboxScreen(viewModel: SecretaryViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inbox", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Žádné nové zprávy", color = Color.Gray) }
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SecretaryApi::class.java)

    fun setManagers(vm: VoiceManager?, cm: CalendarManager, ctm: ContactManager, mm: MailManager, sm: SettingsManager) {
        voiceManager = vm; calendarManager = cm; contactManager = ctm; mailManager = mm; settingsManager = sm
    }
    
    fun getSettingsManager() = settingsManager

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
            } catch (e: Exception) { Log.e("ViewModel", "Refresh Error", e) }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                val res = api.getSettings()
                if (res.isSuccessful) _uiState.value = _uiState.value.copy(systemSettings = res.body() ?: emptyMap(), connectionStatus = ConnectionStatus.CONNECTED)
                else _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.TESTING, status = "Testuji...")
            try {
                val res = api.getSettings()
                if (res.isSuccessful) _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTED, systemSettings = res.body() ?: emptyMap(), status = "Pripojeno")
                else _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED, status = "Server neodpovida")
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED, status = "Odpojeno") }
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

    fun onVoiceInput(text: String) {
        val currentState = _uiState.value
        val newUserMessage = ChatMessage("user", text)
        val updatedHistory = (currentState.history + newUserMessage).takeLast(10)
        
        _uiState.value = currentState.copy(isListening = false, status = "Zpracovávám...", history = updatedHistory)
        viewModelScope.launch {
            try {
                val res = api.processMessage(MessageRequest(
                    text = text, 
                    history = updatedHistory,
                    context_entity_id = currentState.contextEntityId,
                    context_type = currentState.contextType,
                    calendar_context = calendarManager?.getCalendarContext()
                ))
                if (res.isSuccessful) {
                    res.body()?.let { response ->
                        val newAssistantMessage = ChatMessage("assistant", response.reply_cs)
                        _uiState.value = _uiState.value.copy(
                            lastAiReply = response.reply_cs,
                            status = if (response.is_question) "Naslouchám..." else "Připravena",
                            history = _uiState.value.history + newAssistantMessage
                        )
                        handleAction(response)
                        if (response.action_type != "SEARCH_CONTACTS") {
                            voiceManager?.speak(response.reply_cs, expectReply = response.is_question)
                        }
                        refreshCrmData()
                    }
                }
            } catch (e: Exception) { 
                _uiState.value = _uiState.value.copy(status = "Chyba sítě")
                voiceManager?.speak("Marku, nemůžu se spojit se serverem.")
            }
        }
    }

    private fun handleAction(response: AssistantResponse) {
        when (response.action_type) {
            "SEARCH_CONTACTS" -> {
                val query = response.action_data?.get("query") as? String ?: return
                val results = contactManager?.searchContact(query) ?: emptyList()
                if (results.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(contactResults = results)
                    val names = results.joinToString(", ") { it["name"] ?: "" }
                    val msg = "V telefonu jsem našla: $names. Co s tím uděláme?"
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
            "SEND_EMAIL" -> {
                val to = response.action_data?.get("to") as? String ?: return
                val sub = response.action_data?.get("subject") as? String ?: ""
                val body = response.action_data?.get("body") as? String ?: ""
                mailManager?.sendEmail(to, sub, body)
            }
        }
    }

    fun clearHistory() { _uiState.value = _uiState.value.copy(history = emptyList(), lastAiReply = "Cekam na povel...", contactResults = emptyList()) }
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
    val selectedClientDetail: ClientDetail? = null,
    val contextEntityId: Long? = null,
    val contextType: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val pendingImport: Map<String, String>? = null
)
