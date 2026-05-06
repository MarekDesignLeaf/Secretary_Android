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
import android.os.SystemClock
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CameraAlt
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
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private fun readSmsMessagesForImport(context: Context, limit: Int = 5000): List<Map<String, Any?>> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val messages = mutableListOf<Map<String, Any?>>()
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.UK)
    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE
    )
    try {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (cursor.moveToNext() && messages.size < limit) {
                val id = cursor.getLong(idIdx)
                val phone = cursor.getString(addressIdx) ?: continue
                val body = cursor.getString(bodyIdx) ?: ""
                val dateMillis = cursor.getLong(dateIdx)
                val type = cursor.getInt(typeIdx)
                val outbound = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                    type == Telephony.Sms.MESSAGE_TYPE_QUEUED
                messages.add(
                    mapOf(
                        "source" to "sms",
                        "comm_type" to "sms",
                        "external_message_id" to "android-sms-$id",
                        "phone" to phone,
                        "source_phone" to if (outbound) null else phone,
                        "target_phone" to if (outbound) phone else null,
                        "direction" to if (outbound) "outbound" else "inbound",
                        "message" to body,
                        "sent_at" to formatter.format(Date(dateMillis))
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e("SmsImport", "Unable to read SMS history", e)
    }
    return messages
}

data class CommunicationImportStats(
    val scanned: Int = 0,
    val imported: Int = 0,
    val updated: Int = 0,
    val matched: Int = 0,
    val unmatched: Int = 0,
    val message: String? = null
)

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
        Log.d("MainActivity", "Permissions result: RECORD_AUDIO=${permissions[Manifest.permission.RECORD_AUDIO]}")
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            ensureVoiceServiceRunning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarManager = CalendarManager(this)
        contactManager = ContactManager(this)
        mailManager = MailManager(this)
        settingsManager = SettingsManager(this)
        val appLang = settingsManager.getCurrentAppLanguage()
        Strings.setLanguage(appLang)
        
        // Reset recognition language if it was stuck on English while app is in Czech
        if (appLang == "cs" && settingsManager.recognitionLanguage.startsWith("en", ignoreCase = true)) {
            Log.i("MainActivity", "Resetting stale English recognition language for Czech app")
            settingsManager.recognitionLanguage = ""
        }

        viewModel = androidx.lifecycle.ViewModelProvider(this)[SecretaryViewModel::class.java]

        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
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

                LaunchedEffect(state.pendingNavigationAddress) {
                    val address = state.pendingNavigationAddress?.trim()?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
                    val opened = openNavigation(this@MainActivity, address)
                    vm.onNavigationLaunchHandled(opened, address)
                }

                LaunchedEffect(state.pendingCall) {
                    val phone = state.pendingCall?.trim()?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
                    val opened = openDialer(this@MainActivity, phone)
                    vm.onCallLaunchHandled(opened, phone)
                }

                LaunchedEffect(state.pendingWhatsAppPhone, state.pendingWhatsAppMessage) {
                    val phone = state.pendingWhatsAppPhone?.trim()?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
                    if (phone == "__INBOX__") {
                        val opened = openWhatsAppInbox(this@MainActivity)
                        vm.onWhatsAppLaunchHandled(opened, phone)
                    } else {
                        val opened = openWhatsApp(this@MainActivity, phone, state.pendingWhatsAppMessage.orEmpty())
                        vm.onWhatsAppLaunchHandled(opened, phone)
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
    private fun ensureVoiceServiceRunning() {
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!micGranted) return
        if (viewModel.uiState.value.loggedIn != true) return
        if (!viewModel.uiState.value.isBackgroundActive) return
        if (!voiceServiceStarted || !serviceBound || voiceService?.voiceManager == null) {
            startAndBindVoiceService()
        } else {
            voiceService?.voiceManager?.startHotwordLoop()
        }
    }

    private fun startAndBindVoiceService() {
        // Reset flag if service died and was not properly unbound
        if (voiceServiceStarted && !serviceBound) {
            voiceServiceStarted = false
        }
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
            onHotwordDetected = { viewModel.enterDialogMode() },
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

    override fun onResume() {
        super.onResume()
        ensureVoiceServiceRunning()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: SecretaryViewModel, navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val state by viewModel.uiState.collectAsState()
    var showAddClientDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.pendingPlantCaptureRequestId, currentRoute) {
        if (state.pendingPlantCaptureRequestId != null && currentRoute != Screen.Tools.route) {
            navController.navigate(Screen.Tools.route) {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
        }
    }

    // Notify voice resolver whenever the user switches screens
    LaunchedEffect(currentRoute) {
        val screenCode = when (currentRoute) {
            Screen.Home.route     -> "home"
            Screen.Crm.route      -> "crm"
            Screen.Tasks.route    -> "tasks"
            Screen.Calendar.route -> "calendar"
            Screen.Tools.route    -> "tools"
            Screen.Settings.route -> "settings"
            else -> currentRoute?.substringBefore("/") ?: return@LaunchedEffect
        }
        viewModel.updateVoiceContext(screenCode)
    }

    LaunchedEffect(state.pendingAppNavigation) {
        state.pendingAppNavigation?.let { target ->
            val route = when (target) {
                "home" -> Screen.Home.route
                "crm", "clients", "jobs", "leads", "quotes", "invoices", "reports", "contacts" -> Screen.Crm.route
                "tasks" -> Screen.Tasks.route
                "calendar" -> Screen.Calendar.route
                "tools" -> Screen.Tools.route
                "settings" -> Screen.Settings.route
                else -> null
            }
            if (route != null) {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
            viewModel.onAppNavigationHandled()
        }
    }

    LaunchedEffect(showAddClientDialog) {
        if (showAddClientDialog) viewModel.ensureBackendUsersLoaded()
    }

    if (showAddClientDialog) {
        AddClientDialog(
            backendUsers = state.backendUsers,
            onDismiss = { showAddClientDialog = false },
            onConfirm = viewModel::createClientManual
        )
    }

    // Voice resolve overlay: disambiguation or risk-confirmation dialog
    state.pendingVoiceResolve?.let { resolve ->
        VoiceResolveDialog(
            result = resolve,
            onSelectCandidate = viewModel::selectVoiceDisambiguationCandidate,
            onConfirmAction  = viewModel::confirmVoiceResolvedAction,
            onDismiss        = viewModel::dismissVoiceResolve
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
            composable(Screen.Tools.route) {
                LaunchedEffect(Unit) { viewModel.updateContext(null, null) }
                ToolsScreen(viewModel)
            }
            composable(Screen.Settings.route) { 
                LaunchedEffect(Unit) { 
                    viewModel.updateContext(null, null)
                    viewModel.loadSettings()
                    viewModel.loadBackendRoles()
                    viewModel.loadBackendUsers()
                    viewModel.loadAssistantMemory()
                }
                SettingsScreen(viewModel) 
            }
            composable(
                route = Screen.ClientDetail.route,
                arguments = listOf(navArgument("clientId") { type = NavType.LongType })
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getLong("clientId") ?: 0L
                LaunchedEffect(clientId) {
                    viewModel.updateContext(clientId, "client")
                    viewModel.updateVoiceContext("client_detail", "client", clientId.toString())
                }
                ClientDetailScreen(clientId, viewModel, navController)
            }
            composable(
                route = Screen.JobDetail.route,
                arguments = listOf(navArgument("jobId") { type = NavType.LongType })
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getLong("jobId") ?: 0L
                LaunchedEffect(jobId) {
                    viewModel.updateVoiceContext("job_detail", "job", jobId.toString())
                }
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
fun ToolsScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedToolMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.pendingPlantCaptureRequestId) {
        if (state.pendingPlantCaptureRequestId != null) {
            selectedToolMode = state.plantCaptureMode
        }
    }

    if (selectedToolMode == null) {
        ToolsHubScreen(viewModel = viewModel) { mode ->
            if (mode != "import" && mode != "packages") viewModel.setPlantCaptureMode(mode)
            selectedToolMode = mode
        }
    } else if (selectedToolMode == "import") {
        ImportScreen(viewModel = viewModel, onBack = { selectedToolMode = null })
    } else if (selectedToolMode == "packages") {
        ToolPackagesScreen(viewModel = viewModel, onBack = { selectedToolMode = null })
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedToolMode = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back)
                    Spacer(Modifier.width(4.dp))
                    Text(Strings.back)
                }
                Text(
                    toolModeTitle(state.plantCaptureMode),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold
                )
            }
            PlantRecognitionTab(
                state = state,
                viewModel = viewModel,
                showModeSwitcher = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToolsHubScreen(viewModel: SecretaryViewModel, onOpenMode: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    // Fetch dynamic tiles from DB on first display
    LaunchedEffect(Unit) { viewModel.loadToolHubTiles() }

    // Built-in system tiles (always shown at bottom)
    val builtInTiles = listOf(
        Triple(
            "import",
            Strings.t("Data import", "Import dat", "Import danych"),
            Strings.t(
                "Import contacts, clients or other data from CSV, Excel or JSON",
                "Importuj kontakty, klienty nebo jiná data z CSV, Excel nebo JSON",
                "Importuj kontakty, klientów lub inne dane z CSV, Excel lub JSON"
            )
        ),
        Triple(
            "packages",
            Strings.t("Tool packages", "Balíčky nástrojů", "Pakiety narzędzi"),
            Strings.t(
                "Install, configure and manage tool extension packages",
                "Instaluj, konfiguruj a spravuj rozšiřující balíčky nástrojů",
                "Instaluj, konfiguruj i zarządzaj pakietami rozszerzeń narzędzi"
            )
        )
    )

    // DB-sourced tiles from installed plugins, localised per app language
    val lang = Strings.getLangCode()
    val pluginTiles = state.toolHubTiles.sortedBy { it.sort_order }.map { tile ->
        val title = when (lang) {
            "cs" -> tile.tile_title_cs?.takeIf { it.isNotBlank() } ?: tile.tile_title_en
            "pl" -> tile.tile_title_pl?.takeIf { it.isNotBlank() } ?: tile.tile_title_en
            else -> tile.tile_title_en
        }
        val hint = when (lang) {
            "cs" -> tile.tile_hint_cs?.takeIf { it.isNotBlank() } ?: tile.tile_hint_en ?: ""
            "pl" -> tile.tile_hint_pl?.takeIf { it.isNotBlank() } ?: tile.tile_hint_en ?: ""
            else -> tile.tile_hint_en ?: ""
        }
        Triple(tile.tile_key, title, hint)
    }

    val allTiles = pluginTiles + builtInTiles

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(Strings.tools, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.t(
                    "Choose a tool section to open.",
                    "Vyber sekci nástrojů, kterou chceš otevřít.",
                    "Wybierz sekcję narzędzi, którą chcesz otworzyć."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.toolHubTilesLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        items(allTiles) { (mode, title, description) ->
            Button(
                onClick = { onOpenMode(mode) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (description.isNotBlank())
                        Text(description, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun toolModeTitle(mode: String): String = when (mode) {
    "health" -> Strings.plantHealthTitle
    "mushroom" -> Strings.mushroomRecognitionTitle
    else -> Strings.plantRecognitionTitle
}

@Composable
fun CalendarScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val calendarText = remember { mutableStateOf(Strings.loadingCalendar) }
    val today = remember { Calendar.getInstance() }
    var visibleMonth by remember { mutableStateOf(calendarMonthStart(today)) }
    var selectedDate by remember { mutableStateOf(formatCalendarDayKey(today)) }
    LaunchedEffect(Unit) {
        viewModel.loadCalendarFeed()
        val ctx = viewModel.getCalendarText(7)
        calendarText.value = ctx
    }
    val itemsByDate = remember(state.calendarFeed) {
        state.calendarFeed.groupBy { calendarEntryDayKey(it) }
    }
    val monthDays = remember(visibleMonth, itemsByDate, selectedDate) {
        buildCalendarMonthCells(visibleMonth, itemsByDate, selectedDate)
    }
    val selectedEntries = remember(selectedDate, itemsByDate) {
        itemsByDate[selectedDate].orEmpty()
    }
    val selectedWeek = remember(selectedDate, itemsByDate) {
        buildCalendarWeekCells(selectedDate, itemsByDate, selectedDate)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
        Text(Strings.calendar, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.sharedPlanningLabel, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { viewModel.syncPlanningCalendar() }) { Text(Strings.syncCalendar) }
        }
        }
        item {
            CalendarMonthHeader(
                visibleMonth = visibleMonth,
                onPrevious = { visibleMonth = shiftCalendarMonth(visibleMonth, -1) },
                onNext = { visibleMonth = shiftCalendarMonth(visibleMonth, 1) },
                onToday = {
                    val now = Calendar.getInstance()
                    visibleMonth = calendarMonthStart(now)
                    selectedDate = formatCalendarDayKey(now)
                }
            )
        }
        item {
            Text(Strings.calendarWeekLabel, fontWeight = FontWeight.SemiBold)
        }
        item {
            CalendarWeekRow(weekDays = selectedWeek) { selectedDate = it.dateKey }
        }
        item {
            Text(Strings.calendarMonthLabel, fontWeight = FontWeight.SemiBold)
        }
        item {
            CalendarMonthGrid(days = monthDays) { day ->
                selectedDate = day.dateKey
                visibleMonth = calendarMonthStart(day.calendar)
            }
        }
        item {
            Text(
                Strings.calendarSelectedDayLabel(calendarDisplayDate(selectedDate)),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (selectedEntries.isEmpty()) {
            item {
                Text(Strings.noCalendarEntriesForDay, color = Color.Gray)
            }
        } else {
            items(selectedEntries) { entry ->
                val label = when (entry.display_mode) {
                    "reminder" -> Strings.reminderEntry
                    "info" -> Strings.infoEntry
                    else -> Strings.sharedEntry
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(calendarEntryDateLabel(entry), fontSize = 12.sp, color = Color.Gray)
                        entry.client_name?.takeIf { it.isNotBlank() }?.let {
                            Text("${Strings.client}: $it", fontSize = 12.sp, color = Color.Gray)
                        }
                        entry.assigned_to?.takeIf { it.isNotBlank() }?.let {
                            Text("${Strings.assigned}: $it", fontSize = 12.sp, color = Color.Gray)
                        }
                        entry.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        item {
            Text(Strings.calendarEventsLabel, fontWeight = FontWeight.SemiBold)
            Text(calendarText.value)
        }
    }
}

private data class CalendarDayCell(
    val dateKey: String,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val entryCount: Int,
    val calendar: Calendar
)

@Composable
private fun CalendarMonthHeader(
    visibleMonth: Calendar,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back) }
                Text(calendarMonthTitle(visibleMonth), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = Strings.next) }
            }
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.End)) {
                Text(Strings.calendarTodayAction)
            }
        }
    }
}

@Composable
private fun CalendarWeekRow(
    weekDays: List<CalendarDayCell>,
    onSelect: (CalendarDayCell) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekDays.forEach { day ->
            CalendarCompactDayChip(
                day = day,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(day) }
            )
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    days: List<CalendarDayCell>,
    onSelect: (CalendarDayCell) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                calendarWeekdayLabels().forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { day ->
                        CalendarMonthDayCell(
                            day = day,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(day) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCompactDayChip(
    day: CalendarDayCell,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = when {
        day.isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val content = when {
        day.isSelected -> MaterialTheme.colorScheme.onPrimary
        day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> Color.Gray
    }
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(calendarWeekdayShort(day.calendar), fontSize = 11.sp, color = content)
            Text(day.dayNumber.toString(), fontWeight = FontWeight.Bold, color = content)
            Text(day.entryCount.toString(), fontSize = 11.sp, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun CalendarMonthDayCell(
    day: CalendarDayCell,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = when {
        day.isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        day.isSelected -> MaterialTheme.colorScheme.onPrimary
        day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> Color.Gray
    }
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                day.dayNumber.toString(),
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Medium,
                color = content
            )
            if (day.entryCount > 0) {
                Text(
                    Strings.calendarItemCount(day.entryCount),
                    fontSize = 11.sp,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun calendarMonthStart(base: Calendar): Calendar =
    (base.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun shiftCalendarMonth(base: Calendar, delta: Int): Calendar =
    (base.clone() as Calendar).apply { add(Calendar.MONTH, delta) }.let(::calendarMonthStart)

private fun formatCalendarDayKey(calendar: Calendar): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(calendar.time)

private fun parseCalendarDayKey(dateKey: String?): Calendar {
    val fallback = Calendar.getInstance()
    if (dateKey.isNullOrBlank()) return fallback
    return try {
        Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(dateKey) ?: fallback.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    } catch (_: Exception) {
        fallback
    }
}

private fun calendarEntryDayKey(entry: CalendarFeedEntry): String {
    val candidates = listOf(entry.planned_start_at, entry.planned_date, entry.planned_end_at)
    for (candidate in candidates) {
        val parsed = parseFlexibleCalendarDate(candidate)
        if (parsed != null) return parsed
    }
    return formatCalendarDayKey(Calendar.getInstance())
}

private fun parseFlexibleCalendarDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val direct = Regex("""\d{4}-\d{2}-\d{2}""").find(value)?.value
    if (direct != null) return direct
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "dd.MM.yyyy",
        "dd/MM/yyyy"
    )
    for (format in formats) {
        try {
            val date = SimpleDateFormat(format, Locale.UK).parse(value) ?: continue
            return SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(date)
        } catch (_: Exception) {
        }
    }
    return null
}

private fun calendarEntryDateLabel(entry: CalendarFeedEntry): String =
    (entry.planned_start_at ?: entry.planned_date ?: entry.planned_end_at).orEmpty()

private fun buildCalendarMonthCells(
    month: Calendar,
    itemsByDate: Map<String, List<CalendarFeedEntry>>,
    selectedDate: String
): List<CalendarDayCell> {
    val start = (month.clone() as Calendar).apply {
        val weekday = get(Calendar.DAY_OF_WEEK)
        val offset = (weekday + 5) % 7
        add(Calendar.DAY_OF_MONTH, -offset)
    }
    val todayKey = formatCalendarDayKey(Calendar.getInstance())
    return buildList {
        repeat(42) {
            val cellCal = start.clone() as Calendar
            val key = formatCalendarDayKey(cellCal)
            add(
                CalendarDayCell(
                    dateKey = key,
                    dayNumber = cellCal.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = cellCal.get(Calendar.MONTH) == month.get(Calendar.MONTH) && cellCal.get(Calendar.YEAR) == month.get(Calendar.YEAR),
                    isToday = key == todayKey,
                    isSelected = key == selectedDate,
                    entryCount = itemsByDate[key]?.size ?: 0,
                    calendar = cellCal
                )
            )
            start.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

private fun buildCalendarWeekCells(
    selectedDate: String,
    itemsByDate: Map<String, List<CalendarFeedEntry>>,
    activeDate: String
): List<CalendarDayCell> {
    val selected = parseCalendarDayKey(selectedDate)
    val offset = (selected.get(Calendar.DAY_OF_WEEK) + 5) % 7
    selected.add(Calendar.DAY_OF_MONTH, -offset)
    val todayKey = formatCalendarDayKey(Calendar.getInstance())
    return buildList {
        repeat(7) {
            val cell = selected.clone() as Calendar
            val key = formatCalendarDayKey(cell)
            add(
                CalendarDayCell(
                    dateKey = key,
                    dayNumber = cell.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = true,
                    isToday = key == todayKey,
                    isSelected = key == activeDate,
                    entryCount = itemsByDate[key]?.size ?: 0,
                    calendar = cell
                )
            )
            selected.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

private fun calendarMonthTitle(month: Calendar): String =
    SimpleDateFormat("MMMM yyyy", Strings.currentLocale()).format(month.time)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Strings.currentLocale()) else it.toString() }

private fun calendarDisplayDate(dateKey: String): String =
    try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(dateKey)
        SimpleDateFormat("dd.MM.yyyy", Locale.UK).format(date ?: Date())
    } catch (_: Exception) {
        dateKey
    }

private val dateInputIsoDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

private fun dateInputDatePart(value: String): String? =
    dateInputIsoDateRegex.find(value)?.value

private fun dateInputTimePart(value: String): String? =
    Regex("""[T ](\d{2}:\d{2}(?::\d{2})?)""").find(value)?.groupValues?.getOrNull(1)

private fun dateInputParseMillis(value: String): Long? {
    val datePart = dateInputDatePart(value) ?: return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(datePart)?.time
    } catch (_: Exception) {
        null
    }
}

private fun dateInputFormatMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

private fun dateInputTodayMillis(): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun dateInputMergeSelection(
    currentValue: String,
    selectedDate: String,
    includeTime: Boolean,
    defaultTime: String?
): String {
    if (!includeTime) return selectedDate
    val time = dateInputTimePart(currentValue) ?: defaultTime ?: "09:00:00"
    return "${selectedDate}T$time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    includeTime: Boolean = false,
    defaultTime: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }, enabled = enabled) {
                Icon(Icons.Default.DateRange, contentDescription = Strings.calendar)
            }
        }
    )
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateInputParseMillis(value) ?: dateInputTodayMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onValueChange(
                            dateInputMergeSelection(
                                currentValue = value,
                                selectedDate = dateInputFormatMillis(millis),
                                includeTime = includeTime,
                                defaultTime = defaultTime
                            )
                        )
                    }
                    showPicker = false
                }) {
                    Text(Strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(Strings.cancel)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun calendarWeekdayLabels(): List<String> {
    val base = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
    return (0 until 7).map {
        val label = SimpleDateFormat("EE", Strings.currentLocale()).format(base.time)
        base.add(Calendar.DAY_OF_MONTH, 1)
        label.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Strings.currentLocale()) else ch.toString() }
    }
}

private fun calendarWeekdayShort(calendar: Calendar): String =
    SimpleDateFormat("EE", Strings.currentLocale()).format(calendar.time)


private fun activeHierarchyUsers(users: List<BackendUser>): List<BackendUser> =
    users.filter { it.status.equals("active", ignoreCase = true) }

private fun cleanUserDisplayName(value: String?): String =
    value.orEmpty()
        .replace(Regex("\\*+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun hierarchyUserLabel(user: BackendUser): String =
    cleanUserDisplayName(user.display_name).ifBlank { user.email }.ifBlank { "User ${user.id}" }

private fun findHierarchyUser(users: List<BackendUser>, userId: Long?): BackendUser? =
    users.firstOrNull { it.id == userId }

private fun taskHasPlanning(task: Task): Boolean =
    !task.plannedStartAt.isNullOrBlank() || !task.deadline.isNullOrBlank()

private fun canManageHierarchy(state: UiState): Boolean =
    state.currentUserPermissions["manage_users"] == true ||
        state.currentUserRole.equals("admin", ignoreCase = true) ||
        state.currentUserRole.equals("manager", ignoreCase = true)

private fun summarizeHierarchyIssues(issues: List<String>): String =
    issues.distinct().joinToString("\n") { "\u2022 ${Strings.localizeHierarchyIssue(it)}" }

private fun clientHierarchyIssues(detail: ClientDetail, state: UiState): List<String> {
    val issues = mutableListOf<String>()
    val client = detail.client
    val owner = state.backendUsers.firstOrNull { it.id == client.owner_user_id }
    val nextActionId = client.next_action_task_id
    val nextActionTask = state.tasks.firstOrNull { it.id == nextActionId }
    if (client.owner_user_id == null || owner == null || !owner.status.equals("active", ignoreCase = true)) {
        issues += "missing_or_inactive_owner"
    }
    if (nextActionId.isNullOrBlank()) {
        issues += "missing_next_action"
    } else if (nextActionTask == null) {
        issues += "invalid_next_action"
    } else {
        if (nextActionTask.jobId != null || nextActionTask.clientId != client.id) issues += "next_action_wrong_client"
        if (!taskHasPlanning(nextActionTask)) issues += "next_action_missing_planning"
        if (nextActionTask.assignedUserId == null) issues += "next_action_missing_assignee"
        if (nextActionTask.isCompleted || nextActionTask.status in listOf("hotovo", "zruseno")) issues += "next_action_not_open"
    }
    if ((client.hierarchy_status ?: "valid") != "valid" && issues.isEmpty()) {
        issues += "invalid_next_action"
    }
    return issues.distinct()
}

private fun jobHierarchyIssues(detail: JobDetail, state: UiState): List<String> {
    val issues = mutableListOf<String>()
    val job = detail.job
    val owner = state.backendUsers.firstOrNull { it.id == job.assigned_user_id }
    val nextActionId = job.next_action_task_id
    val nextActionTask = state.tasks.firstOrNull { it.id == nextActionId }
    if (job.assigned_user_id == null || owner == null || !owner.status.equals("active", ignoreCase = true)) {
        issues += "missing_or_inactive_owner"
    }
    if (nextActionId.isNullOrBlank()) {
        issues += "missing_next_action"
    } else if (nextActionTask == null) {
        issues += "invalid_next_action"
    } else {
        if (nextActionTask.jobId != job.id) issues += "next_action_wrong_job"
        if (!taskHasPlanning(nextActionTask)) issues += "next_action_missing_planning"
        if (nextActionTask.assignedUserId == null) issues += "next_action_missing_assignee"
        if (nextActionTask.isCompleted || nextActionTask.status in listOf("hotovo", "zruseno")) issues += "next_action_not_open"
    }
    if ((job.hierarchy_status ?: "valid") != "valid" && issues.isEmpty()) {
        issues += "invalid_next_action"
    }
    return issues.distinct()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackendUserDropdown(
    label: String,
    users: List<BackendUser>,
    selectedUserId: Long?,
    onSelect: (BackendUser) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = findHierarchyUser(users, selectedUserId)?.let(::hierarchyUserLabel).orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(hierarchyUserLabel(user)) },
                    onClick = {
                        onSelect(user)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkflowSummaryRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.width(148.dp))
        Text(value?.ifBlank { "-" } ?: "-", fontSize = 13.sp)
    }
}

@Composable
private fun WorkflowStepHeader(step: Int, labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = step == index,
                onClick = {},
                enabled = false,
                label = { Text(label, fontSize = 10.sp) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClientDialog(
    backendUsers: List<BackendUser>,
    onDismiss: () -> Unit,
    onConfirm: (ClientCreationDraft, (Boolean, String?) -> Unit) -> Unit
) {
    val activeUsers = remember(backendUsers) { activeHierarchyUsers(backendUsers) }
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var ownerUserId by remember { mutableStateOf<Long?>(activeUsers.firstOrNull()?.id) }
    var actionTitle by remember { mutableStateOf("") }
    var actionAssigneeId by remember { mutableStateOf<Long?>(activeUsers.firstOrNull()?.id) }
    var actionPlannedStart by remember { mutableStateOf("") }
    var actionDeadline by remember { mutableStateOf("") }
    var actionPriority by remember { mutableStateOf("bezna") }
    var actionNote by remember { mutableStateOf("") }
    var priorityExpanded by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val prios = listOf("nizka" to Strings.low, "bezna" to Strings.normal, "vysoka" to Strings.high, "urgentni" to Strings.urgent, "kriticka" to Strings.critical)
    val stepLabels = listOf(Strings.workflowBasicsStep, Strings.workflowOwnerStep, Strings.workflowActionStep, Strings.workflowSummaryStep)
    val stepValid = when (step) {
        0 -> name.isNotBlank()
        1 -> ownerUserId != null
        2 -> actionTitle.isNotBlank() && actionAssigneeId != null && (actionPlannedStart.isNotBlank() || actionDeadline.isNotBlank())
        else -> name.isNotBlank() && ownerUserId != null && actionTitle.isNotBlank() && actionAssigneeId != null && (actionPlannedStart.isNotBlank() || actionDeadline.isNotBlank())
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.newClientWizardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkflowStepHeader(step, stepLabels)
                Text(Strings.openClientWizardHint, fontSize = 12.sp, color = Color.Gray)
                when (step) {
                    0 -> {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("${Strings.clientName} *") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth())
                    }
                    1 -> {
                        BackendUserDropdown(label = "${Strings.clientOwner} *", users = activeUsers, selectedUserId = ownerUserId) {
                            ownerUserId = it.id
                        }
                    }
                    2 -> {
                        OutlinedTextField(value = actionTitle, onValueChange = { actionTitle = it }, label = { Text("${Strings.actionTitle} *") }, modifier = Modifier.fillMaxWidth())
                        BackendUserDropdown(label = "${Strings.actionAssignee} *", users = activeUsers, selectedUserId = actionAssigneeId) {
                            actionAssigneeId = it.id
                        }
                        DateInputField(value = actionPlannedStart, onValueChange = { actionPlannedStart = it }, label = "${Strings.plannedStart} (YYYY-MM-DDTHH:MM:SS)", includeTime = true, defaultTime = "09:00:00")
                        DateInputField(value = actionDeadline, onValueChange = { actionDeadline = it }, label = "${Strings.deadline} (YYYY-MM-DD)")
                        ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = it }) {
                            OutlinedTextField(
                                value = prios.firstOrNull { it.first == actionPriority }?.second ?: Strings.normal,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(Strings.priority) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) }
                            )
                            ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                                prios.forEach { (key, value) ->
                                    DropdownMenuItem(text = { Text(value) }, onClick = {
                                        actionPriority = key
                                        priorityExpanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(value = actionNote, onValueChange = { actionNote = it }, label = { Text(Strings.planningNote) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                    else -> {
                        WorkflowSummaryRow(Strings.clientName, name)
                        WorkflowSummaryRow(Strings.email, email.ifBlank { null })
                        WorkflowSummaryRow(Strings.phone, phone.ifBlank { null })
                        WorkflowSummaryRow(Strings.clientOwner, findHierarchyUser(activeUsers, ownerUserId)?.let(::hierarchyUserLabel))
                        HorizontalDivider()
                        WorkflowSummaryRow(Strings.actionTitle, actionTitle)
                        WorkflowSummaryRow(Strings.actionAssignee, findHierarchyUser(activeUsers, actionAssigneeId)?.let(::hierarchyUserLabel))
                        WorkflowSummaryRow(Strings.plannedStart, actionPlannedStart.ifBlank { null })
                        WorkflowSummaryRow(Strings.deadline, actionDeadline.ifBlank { null })
                        WorkflowSummaryRow(Strings.priority, prios.firstOrNull { it.first == actionPriority }?.second)
                        WorkflowSummaryRow(Strings.planningNote, actionNote.ifBlank { null })
                    }
                }
                submitError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitError = when {
                        step == 1 && ownerUserId == null -> Strings.ownerRequired
                        step == 2 && actionAssigneeId == null -> Strings.assigneeRequired
                        step == 2 && actionTitle.isBlank() -> Strings.firstActionRequired
                        step == 2 && actionPlannedStart.isBlank() && actionDeadline.isBlank() -> Strings.planningRequired
                        !stepValid -> Strings.firstActionRequired
                        step < 3 -> {
                            step += 1
                            null
                        }
                        else -> {
                            submitting = true
                            val selectedAssignee = findHierarchyUser(activeUsers, actionAssigneeId)
                            val draft = ClientCreationDraft(
                                name = name.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                ownerUserId = ownerUserId,
                                firstAction = WorkflowActionDraft(
                                    title = actionTitle.trim(),
                                    assignedUserId = actionAssigneeId,
                                    assignedTo = selectedAssignee?.let(::hierarchyUserLabel),
                                    plannedStartAt = actionPlannedStart.trim().ifBlank { null },
                                    deadline = actionDeadline.trim().ifBlank { null },
                                    priority = actionPriority,
                                    planningNote = actionNote.trim().ifBlank { null }
                                )
                            )
                            onConfirm(draft) { success, error ->
                                submitting = false
                                submitError = error
                                if (success) onDismiss()
                            }
                            null
                        }
                    }
                },
                enabled = !submitting && stepValid
            ) {
                Text(if (step < 3) Strings.next else Strings.create)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                submitError = null
                if (step > 0) step -= 1 else onDismiss()
            }) {
                Text(if (step > 0) Strings.back else Strings.cancel)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    clients: List<Client>,
    backendUsers: List<BackendUser>,
    initialClientId: Long? = null,
    initialClientName: String? = null,
    initialJobId: Long? = null,
    allowSetAsNextAction: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (TaskCreationDraft, (Boolean, String?) -> Unit) -> Unit
) {
    val activeUsers = remember(backendUsers) { activeHierarchyUsers(backendUsers) }
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("interni_poznamka") }
    var selectedPrio by remember { mutableStateOf("bezna") }
    var deadline by remember { mutableStateOf("") }
    var plannedStartAt by remember { mutableStateOf("") }
    var planningNote by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf<Long?>(initialClientId) }
    var selectedClientName by remember { mutableStateOf<String?>(initialClientName) }
    var selectedAssigneeId by remember { mutableStateOf<Long?>(activeUsers.firstOrNull()?.id) }
    var setAsNextAction by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var prioExpanded by remember { mutableStateOf(false) }
    var clientExpanded by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    val types = listOf("volat" to "📞 ${Strings.call}", "email" to "📧 Email", "schuzka" to "📅 ${Strings.meeting}",
        "objednat_material" to "🧱 ${Strings.orderMaterial}", "navsteva_klienta" to "🏠 ${Strings.visit}",
        "realizace" to "🔨 ${Strings.workExecution}", "kontrola" to "✅ ${Strings.inspection}", "vytvorit_kalkulaci" to "💰 ${Strings.calculation}",
        "pripomenout_se" to "🔔 ${Strings.remind}", "interni_poznamka" to "📋 ${Strings.noteLabel}")
    val prios = listOf("nizka" to Strings.low, "bezna" to Strings.normal, "vysoka" to Strings.high, "urgentni" to Strings.urgent, "kriticka" to Strings.critical)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.newTask) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.taskPlanningHint, fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("${Strings.taskTitle} *") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = types.first { it.first == selectedType }.second, onValueChange = {}, readOnly = true, label = { Text(Strings.taskType) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) })
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedType = k; typeExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = prioExpanded, onExpandedChange = { prioExpanded = it }) {
                    OutlinedTextField(value = prios.first { it.first == selectedPrio }.second, onValueChange = {}, readOnly = true, label = { Text(Strings.priority) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prioExpanded) })
                    ExposedDropdownMenu(expanded = prioExpanded, onDismissRequest = { prioExpanded = false }) {
                        prios.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { selectedPrio = k; prioExpanded = false }) }
                    }
                }
                BackendUserDropdown(label = "${Strings.actionAssignee} *", users = activeUsers, selectedUserId = selectedAssigneeId) {
                    selectedAssigneeId = it.id
                }
                if (clients.isNotEmpty() && initialClientId == null) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        OutlinedTextField(value = selectedClientName ?: Strings.noClient, onValueChange = {}, readOnly = true, label = { Text(Strings.client) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            DropdownMenuItem(text = { Text(Strings.noClient) }, onClick = { selectedClientId = null; selectedClientName = null; clientExpanded = false })
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                }
                DateInputField(value = plannedStartAt, onValueChange = { plannedStartAt = it }, label = "${Strings.plannedStart} (YYYY-MM-DDTHH:MM:SS)", includeTime = true, defaultTime = "09:00:00")
                DateInputField(value = deadline, onValueChange = { deadline = it }, label = "${Strings.deadline} (YYYY-MM-DD)")
                OutlinedTextField(value = planningNote, onValueChange = { planningNote = it }, label = { Text(Strings.planningNote) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (allowSetAsNextAction && (selectedClientId != null || initialJobId != null)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setAsNextAction, onCheckedChange = { setAsNextAction = it })
                        Text(Strings.setAsNextAction)
                    }
                }
                submitError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitError = when {
                        title.isBlank() -> Strings.firstActionRequired
                        selectedAssigneeId == null -> Strings.assigneeRequired
                        plannedStartAt.isBlank() && deadline.isBlank() -> Strings.planningRequired
                        else -> {
                            submitting = true
                            val assignee = findHierarchyUser(activeUsers, selectedAssigneeId)
                            val draft = TaskCreationDraft(
                                title = title.trim(),
                                taskType = selectedType,
                                priority = selectedPrio,
                                clientId = selectedClientId,
                                clientName = selectedClientName,
                                jobId = initialJobId,
                                assignedUserId = selectedAssigneeId,
                                assignedTo = assignee?.let(::hierarchyUserLabel),
                                plannedStartAt = plannedStartAt.trim().ifBlank { null },
                                deadline = deadline.trim().ifBlank { null },
                                planningNote = planningNote.trim().ifBlank { null },
                                setAsNextAction = setAsNextAction
                            )
                            onConfirm(draft) { success, error ->
                                submitting = false
                                submitError = error
                                if (success) onDismiss()
                            }
                            null
                        }
                    }
                },
                enabled = !submitting && title.isNotBlank()
            ) { Text(Strings.create) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ==================== VOICE RESOLVE DIALOG ====================

/**
 * Modal dialog shown whenever the voice resolver returns a result requiring
 * user interaction — either disambiguation between multiple contacts, or
 * confirmation of a medium/high-risk action.
 */
@Composable
fun VoiceResolveDialog(
    result: VoiceResolveResult,
    onSelectCandidate: (VoiceDisambiguationCandidate) -> Unit,
    onConfirmAction: () -> Unit,
    onDismiss: () -> Unit
) {
    if (result.requiresClarification && result.candidates.isNotEmpty()) {
        // ---- DISAMBIGUATION: multiple contacts matched ----
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = result.clarificationQuestion ?: "Koho myslíš?",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "\"${result.originalText}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    Spacer(Modifier.height(4.dp))
                    result.candidates.forEach { candidate ->
                        OutlinedCard(
                            onClick = { onSelectCandidate(candidate) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (candidate.aliasType == "company") Icons.Default.Business else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!candidate.companyName.isNullOrBlank() &&
                                        candidate.companyName != candidate.displayName) {
                                        Text(
                                            text = candidate.companyName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (candidate.disambiguationHint.isNotBlank()) {
                                        Text(
                                            text = candidate.disambiguationHint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                val pct = (candidate.matchConfidence * 100).toInt()
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(Strings.cancel) }
            }
        )
    } else if (result.resolved && result.riskLevel != "low") {
        // ---- CONFIRMATION: medium/high-risk action ----
        val riskColor = if (result.riskLevel == "high")
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.tertiary

        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = riskColor
                )
            },
            title = {
                Text(
                    text = if (result.riskLevel == "high") "Potvrdit akci (vysoké riziko)"
                           else "Potvrdit akci",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "\"${result.originalText}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = riskColor.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = riskColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = result.actionCode ?: result.controlCode ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                result.resolutionMethod?.let { method ->
                                    Text(
                                        text = "rozpoznáno přes: $method",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmAction,
                    colors = ButtonDefaults.buttonColors(containerColor = riskColor)
                ) {
                    Text("Potvrdit")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(Strings.cancel) }
            }
        )
    }
    // If neither condition is met (e.g. stale state), auto-dismiss
    else {
        LaunchedEffect(Unit) { onDismiss() }
    }
}

// ==================== VOICE STATUS BADGE ====================

/**
 * Small inline card showing current voice context + last resolve result.
 * Shown in HomeScreen below the main reply card.
 */
@Composable
fun VoiceContextBadge(state: UiState) {
    val screenCode = state.currentScreenCode ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Kontext: $screenCode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===================================================================

@Composable
fun HomeScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showNavigationDialog by remember { mutableStateOf(false) }
    if (showNavigationDialog) {
        NavigationAddressDialog(
            onDismiss = { showNavigationDialog = false },
            onNavigate = { address ->
                if (openNavigation(context, address)) {
                    showNavigationDialog = false
                } else {
                    viewModel.setStatus(Strings.navigationUnavailable(address))
                }
            }
        )
    }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.isDialogMode) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("●", fontSize = 10.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(Strings.dialogModeStatus.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(state.status.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (state.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(48.dp))
        Card(Modifier.fillMaxWidth().height(120.dp), colors = CardDefaults.cardColors(containerColor = if (state.isDialogMode) MaterialTheme.colorScheme.primaryContainer else if (state.isListening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(state.lastAiReply, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
        VoiceContextBadge(state)

        if (state.contactResults.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(Strings.foundContacts, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    state.contactResults.forEach { contact ->
                        val address = contact["address"]
                        ListItem(
                            headlineContent = { Text(contact["name"] ?: "") },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(contact["phone"] ?: "")
                                    if (!address.isNullOrBlank()) {
                                        Text(address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        AddressActionsRow(address = address, viewModel = viewModel)
                                    }
                                }
                            },
                            trailingContent = { 
                                IconButton(onClick = { 
                                    val phone = contact["phone"] ?: return@IconButton
                                    openDialer(context, phone)
                                }) { Icon(imageVector = Icons.Default.Call, contentDescription = null) } 
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
            Icon(imageVector = if (state.isListening) Icons.Default.Refresh else Icons.Default.Call, contentDescription = null, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showNavigationDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(Strings.startNavigation)
        }
        
        // Ovladaci tlacitka - radek 1
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { viewModel.toggleBackground() }) {
                Icon(imageVector = Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.isBackgroundActive) Strings.backgroundEnabledShort else Strings.backgroundDisabledShort, fontSize = 11.sp)
            }
            OutlinedButton(onClick = { viewModel.restartVoice() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(Strings.restartShort, fontSize = 11.sp)
            }
        }
        // Ovladaci tlacitka - radek 2
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(
                onClick = { viewModel.shutdownApp() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(Strings.closeApp, fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(Strings.logout, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(Strings.history, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(state.history.reversed()) { item -> 
                Text("${Strings.historySpeaker(item.role)}: ${item.content}", Modifier.padding(6.dp))
                HorizontalDivider() 
            }
        }
    }
}

@Composable
fun NavigationAddressDialog(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.startNavigation) },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(Strings.enterAddress) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        },
        confirmButton = {
            Button(
                onClick = { onNavigate(address.trim()) },
                enabled = address.isNotBlank()
            ) { Text(Strings.navigate) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel) }
        }
    )
}

@Composable
fun CrmHubScreen(viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddClient by remember { mutableStateOf(false) }
    var showAddTask by remember { mutableStateOf(false) }
    var showAddJob by remember { mutableStateOf(false) }
    var showAddLead by remember { mutableStateOf(false) }
    var showAddInvoice by remember { mutableStateOf(false) }
    var showAddWorkReport by remember { mutableStateOf(false) }
    var showAddQuote by remember { mutableStateOf(false) }
    var showLogComm by remember { mutableStateOf(false) }
    var showAddSharedContact by remember { mutableStateOf(false) }
    var showEditLead by remember { mutableStateOf<Lead?>(null) }
    var showInvoiceStatus by remember { mutableStateOf<Invoice?>(null) }
    val tabs = listOf(Strings.today, Strings.clients, Strings.jobs, Strings.tasks, Strings.leads, Strings.quotes, Strings.invoices, Strings.workReports, Strings.contactsDirectory, Strings.communications)

    LaunchedEffect(Unit) {
        viewModel.refreshCrmData()
        viewModel.ensureBackendUsersLoaded()
    }

    LaunchedEffect(state.pendingAppNavigation) {
        state.pendingAppNavigation?.let { target ->
            val index = when (target) {
                "today", "home" -> 0
                "clients" -> 1
                "jobs", "crm" -> 2
                "tasks" -> 3
                "leads" -> 4
                "quotes" -> 5
                "invoices" -> 6
                "reports" -> 7
                "contacts" -> 8
                "communications" -> 9
                else -> -1
            }
            if (index >= 0) {
                selectedTab = index
            }
        }
    }

    LaunchedEffect(state.pendingAppAction) {
        state.pendingAppAction?.let { action ->
            when (action) {
                "add_client" -> { selectedTab = 1; showAddClient = true }
                "add_job" -> { selectedTab = 2; showAddJob = true }
                "add_task" -> { selectedTab = 3; showAddTask = true }
                "add_lead" -> { selectedTab = 4; showAddLead = true }
                "add_quote" -> { selectedTab = 5; showAddQuote = true }
                "add_invoice" -> { selectedTab = 6; showAddInvoice = true }
                "add_work_report" -> { selectedTab = 7; showAddWorkReport = true }
                "add_contact" -> { selectedTab = 8; showAddSharedContact = true }
            }
            viewModel.onAppActionHandled()
        }
    }

    Scaffold(
        floatingActionButton = {
            when (selectedTab) {
                1 -> FloatingActionButton(onClick = { showAddClient = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Klient") }
                2 -> FloatingActionButton(onClick = { showAddJob = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Zakázka") }
                3 -> FloatingActionButton(onClick = { showAddTask = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Úkol") }
                4 -> FloatingActionButton(onClick = { showAddLead = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Lead") }
                5 -> FloatingActionButton(onClick = { showAddQuote = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Nabídka") }
                6 -> FloatingActionButton(onClick = { showAddInvoice = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Faktura") }
                7 -> FloatingActionButton(onClick = { showAddWorkReport = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Výkaz") }
                8 -> FloatingActionButton(onClick = { showAddSharedContact = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Kontakt") }
                9 -> FloatingActionButton(onClick = { showLogComm = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Komunikace") }
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
                            val companyName = state.tenantProfile?.get("company_name")?.toString()?.takeIf { it.isNotBlank() } ?: VersionInfo.COMPANY
                            val text = "Faktura ${inv.invoice_number}\nČástka: £${inv.grand_total}\nSplatnost: ${inv.due_date ?: "N/A"}\n\n$companyName"
                            val intent = when(method) {
                                "sms" -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("sms:")).apply { putExtra("sms_body", text) }
                                "whatsapp" -> android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; `package` = "com.whatsapp"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
                                else -> android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_SUBJECT, "Faktura ${inv.invoice_number} — $companyName"); putExtra(android.content.Intent.EXTRA_TEXT, text) }
                            }
                            try { ctx.startActivity(intent) } catch (_: Exception) { try { ctx.startActivity(android.content.Intent.createChooser(intent, "Odeslat fakturu")) } catch (_: Exception) {} }
                        })
                    }
                    7 -> WorkReportsTab(state.workReports, viewModel, navController)
                    8 -> ContactsDirectoryTab(state, viewModel)
                    9 -> CommunicationTab(state, viewModel, navController)
                }
            }
        }
    }

    if (showAddClient) {
        AddClientDialog(
            backendUsers = state.backendUsers,
            onDismiss = { showAddClient = false },
            onConfirm = viewModel::createClientManual
        )
    }
    if (showAddTask) {
        AddTaskDialog(
            clients = state.clients,
            backendUsers = state.backendUsers,
            onDismiss = { showAddTask = false },
            onConfirm = viewModel::createTaskManual
        )
    }
    if (showAddJob) {
        AddJobDialog(
            clients = state.clients,
            backendUsers = state.backendUsers,
            onDismiss = { showAddJob = false },
            onConfirm = viewModel::createJobManual
        )
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
    if (showAddSharedContact) {
        SharedContactDialog(
            sections = state.contactSections,
            onDismiss = { showAddSharedContact = false },
            onSave = { payload, _, onDone ->
                viewModel.saveSharedContact(payload) { ok, msg ->
                    if (ok) showAddSharedContact = false
                    onDone(ok, msg)
                }
            }
        )
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
    var clientSetupMode by remember { mutableStateOf(false) }
    var contactsLoading by remember { mutableStateOf(false) }
    var contactsSaving by remember { mutableStateOf(false) }
    var contactsError by remember { mutableStateOf<String?>(null) }
    var phoneContacts by remember { mutableStateOf<List<SyncedContactCandidate>>(emptyList()) }
    val ctx = LocalContext.current
    val filtered = if (searchQuery.isBlank()) clients
        else clients.filter {
            it.display_name.contains(searchQuery, ignoreCase = true) ||
            (it.client_code ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.email_primary ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.phone_primary ?: "").contains(searchQuery, ignoreCase = true)
        }
    val filteredPhoneContacts = if (searchQuery.isBlank()) phoneContacts else phoneContacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
            (it.phone ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.email ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.address ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.city ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.postcode ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.linked_client_name ?: "").contains(searchQuery, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text(Strings.searchClients, fontSize = 14.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(imageVector = Icons.Default.Clear, contentDescription = null) } },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    contactsError = null
                    if (clientSetupMode) {
                        clientSetupMode = false
                    } else {
                        contactsLoading = true
                        viewModel.loadClientSelectionContacts(ctx) { contacts, error ->
                            contactsLoading = false
                            contactsError = error
                            phoneContacts = contacts
                            clientSetupMode = error == null
                        }
                    }
                }
            ) {
                Text(if (clientSetupMode) Strings.closeClientSetup else Strings.setClients)
            }
        }
        if (clientSetupMode) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    Strings.clientSetupHint,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                contactsError?.let {
                    Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                if (contactsLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.clientSetupLoading, color = Color.Gray) }
                } else if (filteredPhoneContacts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noPhoneContacts, color = Color.Gray) }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                contactsSaving = true
                                contactsError = null
                                viewModel.saveClientSelectionContacts(phoneContacts) { ok, message ->
                                    contactsSaving = false
                                    contactsError = message
                                    if (ok) clientSetupMode = false
                                }
                            },
                            enabled = !contactsSaving
                        ) {
                            Text(if (contactsSaving) Strings.processing else Strings.saveClientSelection)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filteredPhoneContacts, key = { it.contact_key }) { contact ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = contact.selected_as_client,
                                        onCheckedChange = { checked ->
                                            phoneContacts = phoneContacts.map {
                                                if (it.contact_key == contact.contact_key) it.copy(selected_as_client = checked) else it
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                contact.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            VoiceAliasButton(contact.name, viewModel, compact = true)
                                        }
                                        contact.phone?.takeIf { it.isNotBlank() }?.let { Text("\u260E $it", fontSize = 13.sp) }
                                        contact.email?.takeIf { it.isNotBlank() }?.let { Text("\u2709 $it", fontSize = 13.sp, color = Color.Gray) }
                                        contact.address?.takeIf { it.isNotBlank() }?.let { Text("${Strings.address}: $it", fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                        if (contact.linked_client_name != null) {
                                            Text("${Strings.syncedToClient}: ${contact.linked_client_name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (filtered.isEmpty()) {
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
                                imageVector = Icons.Default.Person,
                                contentDescription = null, modifier = Modifier.size(40.dp).padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        client.display_name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    VoiceAliasButton(client.display_name, viewModel, compact = true)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    client.client_code?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                                    if (client.is_commercial) Text(Strings.commercial, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                                }
                                client.phone_primary?.let { Text("\u260E $it", fontSize = 13.sp, maxLines = 1) }
                                client.email_primary?.let { Text("\u2709 $it", fontSize = 13.sp, color = Color.Gray, maxLines = 1) }
                            }
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
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
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.job_title, fontWeight = FontWeight.SemiBold)
                            if (!job.client_name.isNullOrBlank()) {
                                Text("👤 ${job.client_name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { job.client_id?.let { navController.navigate("client/$it") } })
                            }
                            Text("${job.job_number ?: ""} · ${job.planned_start_at ?: job.start_date_planned ?: "bez termínu"}", fontSize = 12.sp, color = Color.Gray)
                            if (!job.assigned_to.isNullOrBlank()) {
                                Text("${Strings.assigned}: ${job.assigned_to}", fontSize = 12.sp, color = Color.Gray)
                            }
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
                        IconButton(onClick = { editJob = job }) { Icon(imageVector = Icons.Default.Edit, contentDescription = "Upravit", tint = Color.Gray) }
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
                                Text(inv.invoice_number ?: "-", fontWeight = FontWeight.Bold)
                                if (!inv.client_name.isNullOrBlank()) {
                                    Text("👤 ${inv.client_name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { inv.client_id?.let { navController?.navigate("client/$it") } })
                                }
                                Text(Strings.localizeStatus(inv.status), fontSize = 12.sp, color = Color.Gray)
                                Text(Strings.invoiceDueDate(inv.due_date), fontSize = 12.sp, color = if (inv.status == "po_splatnosti") Color.Red else Color.Gray)
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
                            Text(Strings.selectAll, fontSize = 13.sp)
                        }
                        if (selectedIds.isNotEmpty()) {
                            Button(onClick = {
                                viewModel.batchInvoiceFromWorkReports(selectedIds.toList()) { res ->
                                    val created = (res?.get("total_created") as? Number)?.toInt() ?: 0
                                    val errors = (res?.get("total_errors") as? Number)?.toInt() ?: 0
                                    invoiceResult = Strings.invoicesCreatedSummary(created, errors)
                                    selectedIds = emptySet()
                                    viewModel.refreshCrmData()
                                }
                            }, modifier = Modifier.height(36.dp)) { Text(Strings.batchInvoiceLabel(selectedIds.size), fontSize = 13.sp) }
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
                                Text(wr.client_name ?: Strings.workReportUnknownClient(wr.client_id), fontWeight = FontWeight.Bold, fontSize = 16.sp,
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
                            Text("🗑 ${Strings.wasteSummary(wq.toInt(), "%.2f".format(wt))}", fontSize = 12.sp, color = Color.Gray)
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
                                        invoiceResult = "✅ ${Strings.invoiceCreatedSuccess(res["invoice_number"], res["grand_total"], profit, margin)}"
                                    } else invoiceResult = "❌ ${Strings.invoiceCreateError()}"
                                    viewModel.refreshCrmData()
                                }
                            }) { Text("📄 ${Strings.newInvoice}", fontSize = 12.sp) }
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
                    label = { Text(if (s == "vše") "${Strings.allTasks} (${leads.size})" else "${Strings.localizeStatus(s)} (${leads.count { it.status == s }})") })
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
                            Text(lead.contact_name ?: lead.lead_code ?: Strings.leadFallback, fontWeight = FontWeight.SemiBold)
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
                                TextButton(onClick = { showConvertDialog = lead }) { Text("${Strings.convert} →", fontSize = 11.sp) }
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
    val summary = state.hierarchyIntegrityReport?.summary
    val orphanTaskIssues = state.hierarchyIntegrityReport?.orphan_tasks.orEmpty()
    val validTaskIds = state.tasks.map { it.id }.toSet()
    val orphanClients = summary?.orphan_clients
        ?: state.clients.count { it.owner_user_id == null || it.next_action_task_id.isNullOrBlank() || !validTaskIds.contains(it.next_action_task_id) }
    val orphanJobs = summary?.orphan_jobs
        ?: state.jobs.count { it.assigned_user_id == null || it.next_action_task_id.isNullOrBlank() || !validTaskIds.contains(it.next_action_task_id) }
    val orphanTasksAssignee = if (orphanTaskIssues.isNotEmpty()) {
        orphanTaskIssues.count { it.issues.any { issue -> issue == "missing_or_inactive_assignee" } }
    } else {
        state.tasks.count { it.assignedUserId == null }
    }
    val orphanTasksPlanning = if (orphanTaskIssues.isNotEmpty()) {
        orphanTaskIssues.count { it.issues.any { issue -> issue == "missing_planning" } }
    } else {
        state.tasks.count { !taskHasPlanning(it) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.hierarchyIntegrityError != null && canManageHierarchy(state)) {
            item {
                Text(
                    state.hierarchyIntegrityError ?: Strings.hierarchyReportLoadFailed,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        item { DashSection(Strings.clientsWithoutNextAction, orphanClients) }
        item { DashSection(Strings.jobsWithoutNextAction, orphanJobs) }
        item { DashSection(Strings.tasksWithoutAssignee, orphanTasksAssignee) }
        item { DashSection(Strings.tasksWithoutSchedule, orphanTasksPlanning) }
        item { FieldModeCard(state, viewModel) }
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
            items(activeJobs) { j -> ListItem(headlineContent = { Text(j.job_title) }, supportingContent = { Text(Strings.localizeStatus(j.job_status)) }, leadingContent = { Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { navController.navigate("job/${j.id}") }); HorizontalDivider() }
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
fun FieldModeCard(state: UiState, viewModel: SecretaryViewModel) {
    val context = LocalContext.current
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date()) }
    val fieldTasks = remember(state.tasks, state.clients, todayKey) {
        val openTasks = state.tasks.filter { it.isOpenForField() && it.navigationAddress(state) != null }
        val plannedToday = openTasks.filter {
            it.plannedDate == todayKey || it.plannedStartAt?.startsWith(todayKey) == true || it.deadline?.startsWith(todayKey) == true
        }
        (if (plannedToday.isNotEmpty()) plannedToday else openTasks).take(5)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Strings.fieldModeTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(Strings.fieldModeHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.startWorkReportSession() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Strings.startVoiceWorkReport, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.requestNavigationAddress() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Strings.startNavigation, fontSize = 12.sp)
                }
            }
            Text(Strings.todayFieldTasks, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (fieldTasks.isEmpty()) {
                Text(Strings.noFieldTasks, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                fieldTasks.forEach { task ->
                    val address = task.navigationAddress(state).orEmpty()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(task.clientName, address).joinToString(" - "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (!openNavigation(context, address)) {
                                    viewModel.setStatus(Strings.navigationUnavailable(address))
                                }
                            }
                        ) {
                            Text(Strings.navigate, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
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
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noCrmData, color = Color.Gray) }
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
                    leadingContent = { Icon(imageVector = icon, contentDescription = null) },
                    trailingContent = { Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
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
    LaunchedEffect(clientId) {
        viewModel.loadClientDetail(clientId)
        viewModel.ensureBackendUsersLoaded()
    }
    val detail = state.selectedClientDetail
    val tabs = listOf(Strings.overview, Strings.jobs, Strings.tasks, Strings.communications, Strings.notes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.client?.display_name ?: "Načítám...") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) { Icon(imageVector = Icons.Default.Edit, contentDescription = "Upravit") }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                2 -> FloatingActionButton(onClick = { showAddTaskDialog = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Úkol") }
                4 -> FloatingActionButton(onClick = { showNoteDialog = true }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Poznámka") }
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
        AddTaskDialog(
            clients = state.clients,
            backendUsers = state.backendUsers,
            initialClientId = clientId,
            initialClientName = detail?.client?.display_name,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = viewModel::createTaskManual
        )
    }
}

private fun addressFromParts(vararg parts: String?): String =
    parts.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .joinToString(", ")

private fun Client.navigationAddress(): String? =
    addressFromParts(billing_address_line1, billing_city, billing_postcode, billing_country)
        .takeIf { it.isNotBlank() }

private fun SharedContact.navigationAddress(): String? =
    address?.trim()?.takeIf(String::isNotBlank)
        ?: addressFromParts(address_line1, city, postcode, country).takeIf { it.isNotBlank() }

private fun Task.navigationAddress(state: UiState): String? =
    propertyAddress?.trim()?.takeIf(String::isNotBlank)
        ?: state.clients.firstOrNull { it.id == clientId }?.navigationAddress()

private fun Task.isOpenForField(): Boolean =
    !isCompleted && status !in setOf("hotovo", "zruseno", "completed", "cancelled")

private fun openDialer(context: Context, phone: String): Boolean {
    val cleanPhone = phone.trim()
    if (cleanPhone.isBlank()) return false
    val telUri = Uri.fromParts("tel", cleanPhone, null)
    val intents = mutableListOf<Intent>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        intents += Intent(Intent.ACTION_CALL, telUri)
    }
    intents += Intent(Intent.ACTION_DIAL, telUri)
    for (intent in intents) {
        intent.apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w("Dialer", "Failed to open phone intent ${intent.action} for phone: $cleanPhone", e)
        }
    }
    return false
}

private fun openWhatsAppInbox(context: Context): Boolean {
    val intents = listOf(
        android.content.Intent("android.intent.action.MAIN").apply {
            addCategory("android.intent.category.LAUNCHER")
            setPackage("com.whatsapp")
        },
        android.content.Intent("android.intent.action.MAIN").apply {
            addCategory("android.intent.category.LAUNCHER")
            setPackage("com.whatsapp.w4b")
        },
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("whatsapp://")).apply {
            setPackage("com.whatsapp")
        }
    )
    for (intent in intents) {
        try { context.startActivity(intent); return true } catch (_: Exception) {}
    }
    return false
}

private fun normalizePhoneForWhatsApp(phone: String): String {
    val raw = phone.trim().filter { it.isDigit() || it == '+' }
    val international = when {
        raw.startsWith("+") -> raw.drop(1)
        raw.startsWith("00") -> raw.drop(2)
        raw.startsWith("0") && raw.length in 10..11 -> "44${raw.drop(1)}"
        else -> raw
    }
    return international.filter(Char::isDigit)
}

private fun openWhatsApp(context: Context, phone: String, message: String): Boolean {
    val cleanPhone = normalizePhoneForWhatsApp(phone)
    if (cleanPhone.length < 8) return false
    val uri = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") },
        Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp.w4b") },
        Intent(Intent.ACTION_VIEW, uri)
    ).map { intent ->
        intent.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            // Try the next installed WhatsApp variant or browser fallback.
        }
    }
    Log.w("WhatsApp", "Failed to open WhatsApp for phone: $phone")
    return false
}

private fun openNavigation(context: Context, address: String): Boolean {
    android.util.Log.d("VoiceNav", "openNavigation called address=[$address]")
    val query = address.trim()
    if (query.isBlank()) return false
    val encoded = Uri.encode(query)
    val mapsDirectionsUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=driving")
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addNavigationLaunchFlags(context)
    }
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded&mode=d")).apply {
            setPackage("com.google.android.apps.maps")
            addNavigationLaunchFlags(context)
        },
        Intent(Intent.ACTION_VIEW, mapsDirectionsUri).apply {
            setPackage("com.google.android.apps.maps")
            addCategory(Intent.CATEGORY_BROWSABLE)
            addNavigationLaunchFlags(context)
        },
        mapIntent,
        Intent(Intent.ACTION_VIEW, mapsDirectionsUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addNavigationLaunchFlags(context)
        },
        Intent.createChooser(mapIntent, Strings.navigate).apply {
            addNavigationLaunchFlags(context)
        }
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w("Navigation", "Failed to open navigation intent: ${intent.data}", e)
        }
    }
    return false
}

private fun Intent.addNavigationLaunchFlags(context: Context) {
    if (context !is android.app.Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
fun AddressActionsRow(address: String?, viewModel: SecretaryViewModel, modifier: Modifier = Modifier) {
    val cleanAddress = address?.trim()?.takeIf(String::isNotBlank) ?: return
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { viewModel.speakAddress(cleanAddress) },
            modifier = Modifier.weight(1f)
        ) {
            Text(Strings.readAddress, fontSize = 12.sp)
        }
        Button(
            onClick = {
                if (!openNavigation(context, cleanAddress)) {
                    viewModel.setStatus(Strings.navigationUnavailable(cleanAddress))
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Text(Strings.navigate, fontSize = 12.sp)
        }
    }
}

@Composable
fun VoiceAliasButton(
    targetName: String,
    viewModel: SecretaryViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val cleanName = targetName.trim()
    if (cleanName.isBlank()) return
    if (compact) {
        IconButton(
            onClick = { viewModel.startVoiceAliasTraining(cleanName) },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = Strings.voiceAliasButton,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        OutlinedButton(
            onClick = { viewModel.startVoiceAliasTraining(cleanName) },
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(Strings.voiceAliasButton, fontSize = 12.sp)
        }
    }
}

@Composable
fun ClientInfoTab(detail: ClientDetail, viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val c = detail.client
    val ctx = LocalContext.current
    val hierarchyIssues = remember(detail, state.tasks, state.backendUsers, state.hierarchyIntegrityReport) {
        state.hierarchyIntegrityReport?.orphan_clients?.firstOrNull { it.id == c.id }?.issues
            ?: clientHierarchyIssues(detail, state)
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showServiceRatesDialog by remember { mutableStateOf(false) }
    if (showEditDialog) {
        ClientEditDialog(client = c, onDismiss = { showEditDialog = false },
            onSave = { data -> viewModel.updateClient(c.id, data); showEditDialog = false })
    }
    if (showServiceRatesDialog) {
        ClientServiceRatesDialog(
            client = c,
            detail = detail,
            viewModel = viewModel,
            rateTypes = state.tenantRateTypes,
            onDismiss = { showServiceRatesDialog = false }
        )
    }
    LazyColumn {
        // === ACTION BUTTONS ===
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!c.phone_primary.isNullOrBlank()) {
                    Button(onClick = { openDialer(ctx, c.phone_primary.orEmpty()) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Strings.call, fontSize = 12.sp) }
                    Button(onClick = {
                        val phone = c.phone_primary!!.replace("+","").replace(" ","").replace("-","")
                        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))
                        ctx.startActivity(waIntent)
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) { Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("WhatsApp", fontSize = 12.sp) }
                }
                if (!c.email_primary.isNullOrBlank()) {
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email_primary}"))) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) { Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Strings.email, fontSize = 12.sp) }
                }
                Button(onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) { Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Strings.edit, fontSize = 12.sp) }
            }
        }
        item {
            VoiceAliasButton(
                targetName = c.display_name,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
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
                }
            }
        }
        item {
            val ownerLabel = state.backendUsers.firstOrNull { it.id == c.owner_user_id }?.let(::hierarchyUserLabel)
            val nextActionTitle = state.tasks.firstOrNull { it.id == c.next_action_task_id }?.title
                ?: detail.tasks.firstOrNull { it["id"]?.toString() == c.next_action_task_id }?.get("title")?.toString()
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Strings.nextAction, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    InfoRow(Strings.clientOwner, ownerLabel)
                    InfoRow(Strings.nextAction, nextActionTitle)
                    InfoRow(Strings.hierarchyStatus, if ((c.hierarchy_status ?: "valid") == "valid") Strings.hierarchyValid else Strings.hierarchyNeedsAttention)
                    if (hierarchyIssues.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(Strings.hierarchyIssues, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text(
                            summarizeHierarchyIssues(hierarchyIssues),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Strings.individualServiceRates, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = { showServiceRatesDialog = true },
                            label = { Text(Strings.edit, fontSize = 12.sp) }
                        )
                    }
                    Text(
                        if (detail.has_individual_service_rates) Strings.individualServiceRatesActive else Strings.individualServiceRatesNotSet,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(Strings.individualServiceRatesHint, fontSize = 12.sp, color = Color.Gray)
                    ClientServiceRatesSummary(detail, rateTypes = state.tenantRateTypes)
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
                val billingAddress = addressFromParts(
                    c.billing_address_line1,
                    c.billing_city,
                    c.billing_postcode,
                    c.billing_country
                )
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Strings.billingAddress, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Strings.billingAddress, c.billing_address_line1)
                        InfoRow(Strings.city, c.billing_city)
                        InfoRow(Strings.postcode, c.billing_postcode)
                        InfoRow(Strings.country, c.billing_country)
                        Spacer(Modifier.height(8.dp))
                        AddressActionsRow(address = billingAddress, viewModel = viewModel)
                    }
                }
            }
        }
        if (detail.properties.isNotEmpty()) {
            item { Text("${Strings.properties} (${detail.properties.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            items(detail.properties) { p ->
                val propertyAddress = addressFromParts(p.address_line1, p.city, p.postcode, p.country)
                Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.property_name, fontWeight = FontWeight.SemiBold)
                        Text(propertyAddress, fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        AddressActionsRow(address = propertyAddress, viewModel = viewModel)
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
                            if (job.start_date_planned != null) Text("${Strings.plan}: ${job.start_date_planned}", fontSize = 12.sp)
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

private fun communicationSourceKey(c: Communication): String =
    (c.source ?: c.comm_type ?: "manual").lowercase(Locale.UK)

private fun communicationSourceLabel(source: String): String = when (source) {
    "whatsapp" -> "WhatsApp"
    "sms" -> "SMS"
    "email" -> "Email"
    "telefon" -> "Telefon"
    "checkatrade" -> "Checkatrade"
    else -> source.replaceFirstChar { it.uppercase() }
}

private fun communicationSourceColor(source: String): Color = when (source) {
    "whatsapp" -> Color(0xFF1FA855)
    "sms" -> Color(0xFF00897B)
    "email" -> Color(0xFF1976D2)
    "telefon" -> Color(0xFFEF6C00)
    "checkatrade" -> Color(0xFF6D4C41)
    else -> Color(0xFF607D8B)
}

private fun communicationImportStatus(stats: CommunicationImportStats): String =
    stats.message ?: Strings.communicationImportDone(stats.imported, stats.updated, stats.matched, stats.scanned)

@Composable
fun ClientCommsTab(detail: ClientDetail, viewModel: SecretaryViewModel) {
    var showLogDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importRunning by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    fun runSmsImport() {
        importRunning = true
        importStatus = null
        scope.launch {
            try {
                val stats = viewModel.importSmsMessages(readSmsMessagesForImport(context))
                importStatus = communicationImportStatus(stats)
                viewModel.loadClientDetail(detail.client.id)
            } catch (e: Exception) {
                importStatus = Strings.communicationImportFailed(e.message ?: "unknown error")
            } finally {
                importRunning = false
            }
        }
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runSmsImport() else importStatus = Strings.communicationImportFailed("READ_SMS permission denied")
    }
    Column(Modifier.fillMaxSize()) {
        if (detail.communications.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) { Text(Strings.noCommunications, color = Color.Gray) }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                val sorted = detail.communications.sortedByDescending { it.sent_at ?: it.created_at ?: "" }
                items(sorted) { c -> CommRow(c); HorizontalDivider() }
            }
        }
        importStatus?.let {
            Text(it, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        runSmsImport()
                    } else {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                },
                enabled = !importRunning,
                modifier = Modifier.weight(1f)
            ) {
                if (importRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(Strings.importSmsHistory)
            }
            OutlinedButton(
                onClick = {
                    importRunning = true
                    importStatus = null
                    scope.launch {
                        try {
                            val stats = viewModel.importServerCommunicationHistory()
                            importStatus = communicationImportStatus(stats)
                            viewModel.loadClientDetail(detail.client.id)
                        } catch (e: Exception) {
                            importStatus = Strings.communicationImportFailed(e.message ?: "unknown error")
                        } finally {
                            importRunning = false
                        }
                    }
                },
                enabled = !importRunning,
                modifier = Modifier.weight(1f)
            ) { Text(Strings.importServerMessageHistory) }
        }
        Button(onClick = { showLogDialog = true }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("+ ${Strings.logCommunicationAction}") }
    }
    if (showLogDialog) {
        LogCommunicationDialog(clientId = detail.client.id, onDismiss = { showLogDialog = false },
            onSave = { type, subject, msg, dir -> viewModel.logCommunication(detail.client.id, null, type, subject, msg, dir); showLogDialog = false })
    }
}

@Composable
fun CommRow(c: Communication, navController: NavHostController? = null) {
    val source = communicationSourceKey(c)
    val sourceColor = communicationSourceColor(source)
    val typeEmoji = when (source) { "telefon" -> "📞"; "email" -> "📧"; "sms" -> "💬"; "whatsapp" -> "📱"; "checkatrade" -> "🏠"; "osobne" -> "🤝"; else -> "📋" }
    val dirLabel = if (c.direction == "outbound") "→ ${Strings.outgoing}" else "← ${Strings.incoming}"
    val dirColor = if (c.direction == "outbound") Color(0xFF2196F3) else Color(0xFF4CAF50)
    Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(sourceColor))
            Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(typeEmoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.subject ?: Strings.noSubject, fontWeight = FontWeight.SemiBold)
                    if (c.client_name != null) Text("👤 ${c.client_name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { c.client_id?.let { navController?.navigate("client/$it") } })
                    if (c.job_title != null) Text("📋 ${c.job_title}", fontSize = 12.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(shape = RoundedCornerShape(50), color = sourceColor.copy(alpha = 0.16f)) {
                        Text(communicationSourceLabel(source), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = sourceColor)
                    }
                    Text(dirLabel, fontSize = 11.sp, color = dirColor)
                }
            }
            if (c.message_summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(c.message_summary, fontSize = 13.sp, color = Color.Gray, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            val phoneLine = listOfNotNull(c.source_phone?.takeIf { it.isNotBlank() }, c.target_phone?.takeIf { it.isNotBlank() }).distinct().joinToString(" → ")
            if (phoneLine.isNotBlank()) Text(phoneLine, fontSize = 11.sp, color = Color.Gray)
            Text(c.sent_at?.take(16) ?: c.created_at?.take(16) ?: "", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
            }
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
                        Text("${n.created_by ?: Strings.you} · ${n.created_at ?: ""}", fontSize = 11.sp, color = Color.Gray)
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
                "billing_city" to billingCity.ifBlank { null }, "billing_postcode" to billingPostcode.ifBlank { null }
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
fun AddJobDialog(
    clients: List<Client>,
    backendUsers: List<BackendUser>,
    onDismiss: () -> Unit,
    onConfirm: (JobCreationDraft, (Boolean, String?) -> Unit) -> Unit
) {
    val activeUsers = remember(backendUsers) { activeHierarchyUsers(backendUsers) }
    var step by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var assignedUserId by remember { mutableStateOf<Long?>(activeUsers.firstOrNull()?.id) }
    var firstActionTitle by remember { mutableStateOf("") }
    var firstActionAssigneeId by remember { mutableStateOf<Long?>(activeUsers.firstOrNull()?.id) }
    var firstActionPlannedStart by remember { mutableStateOf("") }
    var firstActionDeadline by remember { mutableStateOf("") }
    var firstActionPriority by remember { mutableStateOf("bezna") }
    var firstActionNote by remember { mutableStateOf("") }
    var clientExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val prios = listOf("nizka" to Strings.low, "bezna" to Strings.normal, "vysoka" to Strings.high, "urgentni" to Strings.urgent, "kriticka" to Strings.critical)
    val stepLabels = listOf(Strings.workflowBasicsStep, Strings.workflowOwnerStep, Strings.workflowActionStep, Strings.workflowSummaryStep)
    val stepValid = when (step) {
        0 -> title.isNotBlank()
        1 -> assignedUserId != null
        2 -> firstActionTitle.isNotBlank() && firstActionAssigneeId != null && (firstActionPlannedStart.isNotBlank() || firstActionDeadline.isNotBlank())
        else -> title.isNotBlank() && assignedUserId != null && firstActionTitle.isNotBlank() && firstActionAssigneeId != null && (firstActionPlannedStart.isNotBlank() || firstActionDeadline.isNotBlank())
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.newJobWizardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkflowStepHeader(step, stepLabels)
                Text(Strings.openJobWizardHint, fontSize = 12.sp, color = Color.Gray)
                when (step) {
                    0 -> {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("${Strings.jobTitle} *") }, modifier = Modifier.fillMaxWidth())
                        if (clients.isNotEmpty()) {
                            ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                                OutlinedTextField(value = selectedClientName ?: Strings.noClient, onValueChange = {}, readOnly = true, label = { Text(Strings.client) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                                ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                                    DropdownMenuItem(text = { Text(Strings.noClient) }, onClick = {
                                        selectedClientId = null
                                        selectedClientName = null
                                        clientExpanded = false
                                    })
                                    clients.forEach { c ->
                                        DropdownMenuItem(text = { Text(c.display_name) }, onClick = {
                                            selectedClientId = c.id
                                            selectedClientName = c.display_name
                                            clientExpanded = false
                                        })
                                    }
                                }
                            }
                        }
                        DateInputField(value = startDate, onValueChange = { startDate = it }, label = "${Strings.plannedStart} (YYYY-MM-DD)")
                    }
                    1 -> {
                        BackendUserDropdown(label = "${Strings.jobOwner} *", users = activeUsers, selectedUserId = assignedUserId) {
                            assignedUserId = it.id
                        }
                    }
                    2 -> {
                        OutlinedTextField(value = firstActionTitle, onValueChange = { firstActionTitle = it }, label = { Text("${Strings.actionTitle} *") }, modifier = Modifier.fillMaxWidth())
                        BackendUserDropdown(label = "${Strings.actionAssignee} *", users = activeUsers, selectedUserId = firstActionAssigneeId) {
                            firstActionAssigneeId = it.id
                        }
                        DateInputField(value = firstActionPlannedStart, onValueChange = { firstActionPlannedStart = it }, label = "${Strings.plannedStart} (YYYY-MM-DDTHH:MM:SS)", includeTime = true, defaultTime = "09:00:00")
                        DateInputField(value = firstActionDeadline, onValueChange = { firstActionDeadline = it }, label = "${Strings.deadline} (YYYY-MM-DD)")
                        ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = it }) {
                            OutlinedTextField(
                                value = prios.firstOrNull { it.first == firstActionPriority }?.second ?: Strings.normal,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(Strings.priority) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) }
                            )
                            ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                                prios.forEach { (key, value) ->
                                    DropdownMenuItem(text = { Text(value) }, onClick = {
                                        firstActionPriority = key
                                        priorityExpanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(value = firstActionNote, onValueChange = { firstActionNote = it }, label = { Text(Strings.planningNote) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                    else -> {
                        WorkflowSummaryRow(Strings.jobTitle, title)
                        WorkflowSummaryRow(Strings.client, selectedClientName)
                        WorkflowSummaryRow(Strings.jobOwner, findHierarchyUser(activeUsers, assignedUserId)?.let(::hierarchyUserLabel))
                        WorkflowSummaryRow(Strings.plannedStart, startDate.ifBlank { null })
                        HorizontalDivider()
                        WorkflowSummaryRow(Strings.actionTitle, firstActionTitle)
                        WorkflowSummaryRow(Strings.actionAssignee, findHierarchyUser(activeUsers, firstActionAssigneeId)?.let(::hierarchyUserLabel))
                        WorkflowSummaryRow(Strings.plannedStart, firstActionPlannedStart.ifBlank { null })
                        WorkflowSummaryRow(Strings.deadline, firstActionDeadline.ifBlank { null })
                        WorkflowSummaryRow(Strings.priority, prios.firstOrNull { it.first == firstActionPriority }?.second)
                        WorkflowSummaryRow(Strings.planningNote, firstActionNote.ifBlank { null })
                    }
                }
                submitError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitError = when {
                        step == 1 && assignedUserId == null -> Strings.ownerRequired
                        step == 2 && firstActionAssigneeId == null -> Strings.assigneeRequired
                        step == 2 && firstActionTitle.isBlank() -> Strings.firstActionRequired
                        step == 2 && firstActionPlannedStart.isBlank() && firstActionDeadline.isBlank() -> Strings.planningRequired
                        !stepValid -> Strings.firstActionRequired
                        step < 3 -> {
                            step += 1
                            null
                        }
                        else -> {
                            submitting = true
                            val owner = findHierarchyUser(activeUsers, assignedUserId)
                            val assignee = findHierarchyUser(activeUsers, firstActionAssigneeId)
                            val draft = JobCreationDraft(
                                title = title.trim(),
                                clientId = selectedClientId,
                                clientName = selectedClientName,
                                assignedUserId = assignedUserId,
                                assignedTo = owner?.let(::hierarchyUserLabel),
                                startDate = startDate.trim().ifBlank { null },
                                firstAction = WorkflowActionDraft(
                                    title = firstActionTitle.trim(),
                                    assignedUserId = firstActionAssigneeId,
                                    assignedTo = assignee?.let(::hierarchyUserLabel),
                                    plannedStartAt = firstActionPlannedStart.trim().ifBlank { null },
                                    deadline = firstActionDeadline.trim().ifBlank { null },
                                    priority = firstActionPriority,
                                    planningNote = firstActionNote.trim().ifBlank { null }
                                )
                            )
                            onConfirm(draft) { success, error ->
                                submitting = false
                                submitError = error
                                if (success) onDismiss()
                            }
                            null
                        }
                    }
                },
                enabled = !submitting && stepValid
            ) { Text(if (step < 3) Strings.next else Strings.create) }
        },
        dismissButton = {
            TextButton(onClick = {
                submitError = null
                if (step > 0) step -= 1 else onDismiss()
            }) { Text(if (step > 0) Strings.back else Strings.cancel) }
        }
    )
}

// ========== JOB DETAIL SCREEN ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(jobId: Long, viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(Strings.startLabel, Strings.progressLabel, Strings.endLabel, Strings.complicationsLabel, Strings.auditLog)

    var showStatusDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteTypeForDialog by remember { mutableStateOf("general") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val photoFile = remember { mutableStateOf<java.io.File?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoFile.value?.let { file ->
                val type = when(selectedTab) {
                    0 -> "start"
                    1 -> "process"
                    2 -> "end"
                    3 -> "complication"
                    else -> "general"
                }
                viewModel.uploadJobPhoto(jobId, type, file)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = java.io.File(context.cacheDir, "gallery_photo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val type = when(selectedTab) {
                0 -> "start"
                1 -> "process"
                2 -> "end"
                3 -> "complication"
                else -> "general"
            }
            viewModel.uploadJobPhoto(jobId, type, file)
        }
    }

    fun launchCamera() {
        val file = java.io.File(context.cacheDir, "job_photo_${System.currentTimeMillis()}.jpg")
        photoFile.value = file
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(jobId) {
        viewModel.loadJobDetail(jobId)
        viewModel.ensureBackendUsersLoaded()
    }
    val detail = state.selectedJobDetail
    val hierarchyIssues = remember(detail, state.tasks, state.backendUsers, state.hierarchyIntegrityReport) {
        val current = detail ?: return@remember emptyList<String>()
        state.hierarchyIntegrityReport?.orphan_jobs?.firstOrNull { it.id == current.job.id }?.issues
            ?: jobHierarchyIssues(current, state)
    }
    val hierarchyValid = hierarchyIssues.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.job?.job_title ?: "Načítám...") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) { Icon(imageVector = Icons.Default.Edit, contentDescription = "Upravit") }
                    IconButton(onClick = { if (hierarchyValid) showStatusDialog = true }, enabled = hierarchyValid) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = Strings.changeStatus)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) { Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null) }
                
                FloatingActionButton(onClick = { 
                    noteTypeForDialog = if (selectedTab == 3) "complication" else "general"
                    showNoteDialog = true 
                }) { Icon(imageVector = Icons.Default.Add, contentDescription = "Poznámka") }
            }
        }
    ) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.padding(padding)) {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }

                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    item {
                        val ownerLabel = state.backendUsers.firstOrNull { it.id == detail.job.assigned_user_id }?.let(::hierarchyUserLabel)
                        val nextActionTitle = state.tasks.firstOrNull { it.id == detail.job.next_action_task_id }?.title
                            ?: detail.tasks.firstOrNull { it["id"]?.toString() == detail.job.next_action_task_id }?.get("title")?.toString()
                        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(Strings.nextAction, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                InfoRow(Strings.jobOwner, ownerLabel)
                                InfoRow(Strings.nextAction, nextActionTitle)
                                InfoRow(Strings.hierarchyStatus, if ((detail.job.hierarchy_status ?: "valid") == "valid") Strings.hierarchyValid else Strings.hierarchyNeedsAttention)
                                if (hierarchyIssues.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(Strings.hierarchyIssues, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                    Text(
                                        summarizeHierarchyIssues(hierarchyIssues),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    when (selectedTab) {
                        0 -> jobStageContent(detail, "start", viewModel)
                        1 -> jobStageContent(detail, "process", viewModel)
                        2 -> jobStageContent(detail, "end", viewModel)
                        3 -> jobStageContent(detail, "complication", viewModel)
                        4 -> auditLogContent(detail)
                    }
                }
            }
        }
    }

    if (showEditDialog && detail != null) {
        EditJobDialog(job = detail.job, onDismiss = { showEditDialog = false },
            onSave = { data -> viewModel.updateJob(jobId, data); showEditDialog = false })
    }
    if (showNoteDialog && detail != null) {
        AddJobNoteDialog(onDismiss = { showNoteDialog = false },
            onSave = { note -> viewModel.addJobNote(jobId, note, noteTypeForDialog); showNoteDialog = false })
    }
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
                            modifier = Modifier.clickable { 
                                if (!hierarchyValid) return@clickable
                                viewModel.updateJob(jobId, mapOf("job_status" to s))
                                viewModel.addJobAuditEntry(jobId, "status_change", "Status changed to $s")
                                showStatusDialog = false 
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStatusDialog = false }) { Text(Strings.close) } },
            dismissButton = {
                if (!hierarchyValid) {
                    Text(
                        Strings.hierarchyStatusBlocked,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        )
    }
}

private fun LazyListScope.jobStageContent(detail: JobDetail, stage: String, viewModel: SecretaryViewModel) {
    item {
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                val stageTitle = when(stage) {
                    "start" -> Strings.startLabel
                    "process" -> Strings.progressLabel
                    "end" -> Strings.endLabel
                    "complication" -> Strings.complicationsLabel
                    else -> stage
                }
                Text(stageTitle, fontWeight = FontWeight.Bold)
                if (stage == "start") {
                    Text("${Strings.jobNumber}: ${detail.job.job_number ?: "-"}")
                    Text("${Strings.plan}: ${detail.job.start_date_planned ?: Strings.noTermin}")
                }
                detail.job.assigned_to?.let { Text("${Strings.assigned}: $it") }
            }
        }
    }

    val stageNotes = detail.notes.filter { 
        if (stage == "complication") it.note_type == "complication" 
        else it.note_type == stage || (stage == "process" && it.note_type == "general")
    }
    if (stageNotes.isNotEmpty()) {
        item { Text(Strings.notes, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
        items(stageNotes) { n ->
            Card(Modifier.fillMaxWidth().padding(bottom = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text(n.note)
                    Text("${n.created_by ?: ""} · ${n.created_at ?: ""}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }

    val stagePhotos = detail.photos.filter { it.photo_type == stage }
    if (stagePhotos.isNotEmpty()) {
        item { Text(Strings.photoAction, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
        items(stagePhotos.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { photo ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp)) {
                        CoilImage(
                            imageModel = { photo.url },
                            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.auditLogContent(detail: JobDetail) {
    item {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Strings.timestampLabel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
            Text(Strings.userLabel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(Strings.actionLabel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
        }
        HorizontalDivider()
    }
    items(detail.audit_log.reversed()) { entry ->
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.created_at.split("T").lastOrNull()?.take(5) ?: "", fontSize = 12.sp, modifier = Modifier.weight(1.2f))
            Text(entry.user_name ?: "-", fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(entry.description, fontSize = 12.sp, modifier = Modifier.weight(2f))
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCompletionDialog(
    task: Task,
    backendUsers: List<BackendUser>,
    replacementCandidates: List<Task>,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String?, WorkflowActionDraft?) -> Unit
) {
    val activeUsers = remember(backendUsers) { activeHierarchyUsers(backendUsers) }
    var useExisting by remember { mutableStateOf(replacementCandidates.isNotEmpty()) }
    var selectedExistingTaskId by remember { mutableStateOf<String?>(replacementCandidates.firstOrNull()?.id) }
    var title by remember { mutableStateOf("") }
    var assignedUserId by remember { mutableStateOf<Long?>(activeUsers.firstOrNull()?.id) }
    var plannedStartAt by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("bezna") }
    var note by remember { mutableStateOf("") }
    var priorityExpanded by remember { mutableStateOf(false) }
    var existingExpanded by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val prios = listOf("nizka" to Strings.low, "bezna" to Strings.normal, "vysoka" to Strings.high, "urgentni" to Strings.urgent, "kriticka" to Strings.critical)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.currentNextActionDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.currentNextActionDialogBody, fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useExisting,
                        onClick = { useExisting = true },
                        enabled = replacementCandidates.isNotEmpty(),
                        label = { Text(Strings.replacementExistingTask, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = !useExisting,
                        onClick = { useExisting = false },
                        label = { Text(Strings.replacementCreateTask, fontSize = 11.sp) }
                    )
                }
                if (useExisting) {
                    if (replacementCandidates.isEmpty()) {
                        Text(Strings.noReplacementCandidates, color = Color.Gray, fontSize = 12.sp)
                    } else {
                        ExposedDropdownMenuBox(expanded = existingExpanded, onExpandedChange = { existingExpanded = it }) {
                            val selectedLabel = replacementCandidates.firstOrNull { it.id == selectedExistingTaskId }?.title.orEmpty()
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(Strings.replacementExistingTask) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = existingExpanded) }
                            )
                            ExposedDropdownMenu(expanded = existingExpanded, onDismissRequest = { existingExpanded = false }) {
                                replacementCandidates.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(candidate.title) },
                                        onClick = {
                                            selectedExistingTaskId = candidate.id
                                            existingExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("${Strings.createReplacementTaskLabel} *") }, modifier = Modifier.fillMaxWidth())
                    BackendUserDropdown(label = "${Strings.actionAssignee} *", users = activeUsers, selectedUserId = assignedUserId) {
                        assignedUserId = it.id
                    }
                    DateInputField(value = plannedStartAt, onValueChange = { plannedStartAt = it }, label = "${Strings.plannedStart} (YYYY-MM-DDTHH:MM:SS)", includeTime = true, defaultTime = "09:00:00")
                    DateInputField(value = deadline, onValueChange = { deadline = it }, label = "${Strings.deadline} (YYYY-MM-DD)")
                    ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = it }) {
                        OutlinedTextField(
                            value = prios.firstOrNull { it.first == priority }?.second ?: Strings.normal,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(Strings.priority) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) }
                        )
                        ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                            prios.forEach { (key, value) ->
                                DropdownMenuItem(text = { Text(value) }, onClick = {
                                    priority = key
                                    priorityExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(Strings.planningNote) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
                (submitError ?: errorMessage)?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                submitError = when {
                    useExisting && selectedExistingTaskId.isNullOrBlank() -> Strings.replacementTaskRequired
                    !useExisting && title.isBlank() -> Strings.replacementTaskRequired
                    !useExisting && assignedUserId == null -> Strings.assigneeRequired
                    !useExisting && plannedStartAt.isBlank() && deadline.isBlank() -> Strings.planningRequired
                    useExisting -> {
                        onConfirm(selectedExistingTaskId, null)
                        null
                    }
                    else -> {
                        val assignee = findHierarchyUser(activeUsers, assignedUserId)
                        onConfirm(
                            null,
                            WorkflowActionDraft(
                                title = title.trim(),
                                assignedUserId = assignedUserId,
                                assignedTo = assignee?.let(::hierarchyUserLabel),
                                plannedStartAt = plannedStartAt.trim().ifBlank { null },
                                deadline = deadline.trim().ifBlank { null },
                                priority = priority,
                                planningNote = note.trim().ifBlank { null }
                            )
                        )
                        null
                    }
                }
            }) { Text(Strings.completeWithReplacement) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== TASK DETAIL SCREEN ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(taskId: String, viewModel: SecretaryViewModel, navController: NavHostController) {
    val state by viewModel.uiState.collectAsState()
    val task = viewModel.getTaskById(taskId)
    val context = androidx.compose.ui.platform.LocalContext.current
    var showStatusDialog by remember { mutableStateOf(false) }
    var showPrioDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf(task?.result ?: "") }
    var showResultSave by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: Strings.noTaskFound) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (task == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text(Strings.noTaskFound, color = Color.Gray) }
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
                            Text("${Strings.status}: ${Strings.localizeStatus(task.status)}")
                        }
                        OutlinedButton(onClick = { showPrioDialog = true }, modifier = Modifier.weight(1f)) {
                            val prioColor = when(task.priority) { "kriticka"->Color.Red; "urgentni"->Color(0xFFE65100); "vysoka"->Color(0xFFF57C00); else->Color.Unspecified }
                            Text("${Strings.priority}: ${Strings.localizeStatus(task.priority)}", color = prioColor)
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
                            if (task.plannedStartAt != null) Text("${Strings.plannedStart}: ${task.plannedStartAt}")
                            if (task.plannedEndAt != null) Text("${Strings.plannedEnd}: ${task.plannedEndAt}")
                            if (task.planningNote != null) Text("${Strings.planningNote}: ${task.planningNote}")
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
                            Text("📷 ${Strings.photoAction}")
                        }
                        if (task.deadline != null || task.plannedDate != null) {
                            OutlinedButton(onClick = { viewModel.addTaskToCalendar(task) }, modifier = Modifier.weight(1f)) {
                                Text("📅 ${Strings.addToCalendarAction}")
                            }
                        }
                    }
                }
                // Dokoncit
                if (!task.isCompleted) {
                    item {
                        Button(onClick = {
                            if (viewModel.isCurrentNextActionTask(taskId)) {
                                completionError = null
                                showCompletionDialog = true
                            } else {
                                viewModel.completeTask(taskId) { success, error ->
                                    if (success) navController.popBackStack() else completionError = error
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                            Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(Strings.completeTask)
                        }
                        completionError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
            text = { LazyColumn { items(statuses) { s -> ListItem(headlineContent = { Text(Strings.localizeStatus(s)) }, modifier = Modifier.clickable {
                if ((s == "hotovo" || s == "zruseno") && viewModel.isCurrentNextActionTask(taskId)) {
                    completionError = null
                    showCompletionDialog = true
                } else {
                    viewModel.updateTask(taskId, mapOf("status" to s))
                }
                showStatusDialog = false
            }) } } },
            confirmButton = { TextButton(onClick = { showStatusDialog = false }) { Text(Strings.close) } })
    }
    // Priority dialog
    if (showPrioDialog) {
        val prios = listOf("nizka","bezna","vysoka","urgentni","kriticka")
        AlertDialog(onDismissRequest = { showPrioDialog = false }, title = { Text(Strings.changePriority) },
            text = { LazyColumn { items(prios) { p -> ListItem(headlineContent = { Text(Strings.localizeStatus(p)) }, modifier = Modifier.clickable { viewModel.updateTask(taskId, mapOf("priority" to p)); showPrioDialog = false }) } } },
            confirmButton = { TextButton(onClick = { showPrioDialog = false }) { Text(Strings.close) } })
    }
    if (showCompletionDialog && task != null) {
        TaskCompletionDialog(
            task = task,
            backendUsers = state.backendUsers,
            replacementCandidates = viewModel.getReplacementCandidates(task),
            errorMessage = completionError,
            onDismiss = { showCompletionDialog = false },
            onConfirm = { replacementTaskId, replacementDraft ->
                completionError = null
                viewModel.completeTask(task.id, replacementTaskId, replacementDraft) { success, error ->
                    if (success) {
                        showCompletionDialog = false
                        navController.popBackStack()
                    } else {
                        completionError = error
                    }
                }
            }
        )
    }
}

@Composable
fun TasksScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf("vse") }
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Load tasks on screen open
    LaunchedEffect(Unit) {
        viewModel.refreshCrmData()
        viewModel.ensureBackendUsersLoaded()
    }
    
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
            backendUsers = state.backendUsers,
            onDismiss = { showAddDialog = false },
            onConfirm = viewModel::createTaskManual
        )
    }
}

@Composable
fun TaskRow(task: Task, viewModel: SecretaryViewModel, navController: NavHostController? = null, onEdit: ((Task) -> Unit)? = null) {
    val state by viewModel.uiState.collectAsState()
    var showCompletionDialog by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf<String?>(null) }
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
            if (task.clientName != null) Text("${Strings.client}: ${task.clientName}", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.localizeStatus(task.status), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                if (task.deadline != null) Text("${Strings.deadline}: ${task.deadline}", fontSize = 11.sp, color = Color.Gray)
                if (task.assignedTo != null) Text("→ ${task.assignedTo}", fontSize = 11.sp, color = Color.Gray)
            }
        }
        if (!task.isCompleted) {
            IconButton(onClick = {
                if (viewModel.isCurrentNextActionTask(task.id)) {
                    completionError = null
                    showCompletionDialog = true
                } else {
                    viewModel.completeTask(task.id) { _, error -> completionError = error }
                }
            }) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showCompletionDialog) {
        TaskCompletionDialog(
            task = task,
            backendUsers = state.backendUsers,
            replacementCandidates = viewModel.getReplacementCandidates(task),
            errorMessage = completionError,
            onDismiss = { showCompletionDialog = false },
            onConfirm = { replacementTaskId, replacementDraft ->
                completionError = null
                viewModel.completeTask(task.id, replacementTaskId, replacementDraft) { success, error ->
                    if (success) {
                        showCompletionDialog = false
                    } else {
                        completionError = error
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditDialog(
    task: Task,
    backendUsers: List<BackendUser>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>) -> Unit
) {
    val activeUsers = remember(backendUsers) { activeHierarchyUsers(backendUsers) }
    var status by remember { mutableStateOf(task.status) }
    var priority by remember { mutableStateOf(task.priority) }
    var notes by remember { mutableStateOf(task.result ?: "") }
    var assignedUserId by remember { mutableStateOf(task.assignedUserId ?: activeUsers.firstOrNull()?.id) }
    var plannedDate by remember { mutableStateOf(task.plannedDate ?: "") }
    var plannedStart by remember { mutableStateOf(task.plannedStartAt ?: "") }
    var plannedEnd by remember { mutableStateOf(task.plannedEndAt ?: "") }
    var planningNote by remember { mutableStateOf(task.planningNote ?: "") }
    var calendarSync by remember { mutableStateOf(task.calendarSyncEnabled) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val statuses = listOf("novy", "naplanovany", "v_reseni", "ceka_na_klienta", "ceka_na_material", "hotovo", "zruseno")
    val priorities = listOf("kriticka", "urgentni", "vysoka", "bezna", "nizka")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.edit) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) { item {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (task.clientName != null) Text("${Strings.client}: ${task.clientName}", fontSize = 13.sp, color = Color.Gray)
                if (task.description != null) Text(task.description, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Text(Strings.status, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(statuses) { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(Strings.localizeStatus(s), fontSize = 10.sp) }) }
                }
                Spacer(Modifier.height(8.dp))
                Text(Strings.priority, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(priorities) { p -> FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(Strings.localizeStatus(p), fontSize = 10.sp) }) }
                }
                Spacer(Modifier.height(8.dp))
                BackendUserDropdown(
                    label = Strings.assigned,
                    users = activeUsers,
                    selectedUserId = assignedUserId,
                    onSelect = {
                        assignedUserId = it.id
                        validationError = null
                    }
                )
                Spacer(Modifier.height(8.dp))
                DateInputField(value = plannedDate, onValueChange = { plannedDate = it }, label = "${Strings.plan} (YYYY-MM-DD)")
                Spacer(Modifier.height(8.dp))
                DateInputField(value = plannedStart, onValueChange = { plannedStart = it }, label = "${Strings.plannedStart} (YYYY-MM-DDTHH:MM:SS)", includeTime = true, defaultTime = "09:00:00")
                Spacer(Modifier.height(8.dp))
                DateInputField(value = plannedEnd, onValueChange = { plannedEnd = it }, label = "${Strings.plannedEnd} (YYYY-MM-DDTHH:MM:SS)", includeTime = true, defaultTime = "10:00:00")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = planningNote, onValueChange = { planningNote = it }, label = { Text(Strings.planningNote) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(Strings.taskResult) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = calendarSync, onCheckedChange = { calendarSync = it })
                    Text(Strings.syncCalendar)
                }
                validationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            } }
        },
        confirmButton = { TextButton(onClick = {
            val selectedUser = findHierarchyUser(activeUsers, assignedUserId)
            if (selectedUser == null) {
                validationError = Strings.assigneeRequired
                return@TextButton
            }
            if (plannedStart.isBlank() && plannedDate.isBlank()) {
                validationError = Strings.planningRequired
                return@TextButton
            }
            validationError = null
            onSave(
                mapOf(
                    "status" to status,
                    "priority" to priority,
                    "result" to notes,
                    "assigned_user_id" to selectedUser.id,
                    "assigned_to" to hierarchyUserLabel(selectedUser),
                    "planned_date" to plannedDate.ifBlank { null },
                    "deadline" to plannedDate.ifBlank { null },
                    "planned_start_at" to plannedStart.ifBlank { null },
                    "planned_end_at" to plannedEnd.ifBlank { null },
                    "planning_note" to planningNote.ifBlank { null },
                    "calendar_sync_enabled" to calendarSync
                )
            )
        }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

@Composable
fun TasksTab(state: UiState, viewModel: SecretaryViewModel) {
    var filter by remember { mutableStateOf("aktivni") }
    var editTask by remember { mutableStateOf<Task?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("aktivni" to Strings.activeTasks, "ceka" to Strings.waiting, "hotovo" to Strings.done, "vse" to Strings.allTasks).forEach { (key, label) ->
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
        if (filtered.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noTasks, color = Color.Gray) } }
        else { LazyColumn { items(filtered) { t -> TaskRow(t, viewModel, onEdit = { editTask = it }); HorizontalDivider() } } }
    }
    if (editTask != null) {
        TaskEditDialog(task = editTask!!, backendUsers = state.backendUsers, onDismiss = { editTask = null }, onSave = { data ->
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
    var syncRunning by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun runSmsImport() {
        syncRunning = true
        syncStatus = null
        scope.launch {
            try {
                val stats = viewModel.importSmsMessages(readSmsMessagesForImport(context))
                syncStatus = communicationImportStatus(stats)
                comms.value = viewModel.loadAllCommunications()
            } catch (e: Exception) {
                syncStatus = Strings.communicationImportFailed(e.message ?: "unknown error")
            } finally {
                syncRunning = false
            }
        }
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runSmsImport() else syncStatus = Strings.communicationImportFailed("READ_SMS permission denied")
    }
    
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

        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                OutlinedButton(
                    onClick = {
                        syncRunning = true
                        syncStatus = null
                        scope.launch {
                            try {
                                val (updated, scanned) = viewModel.syncWhatsappAddressesFromMessages()
                                syncStatus = Strings.whatsappAddressSyncDone(updated, scanned)
                                comms.value = viewModel.loadAllCommunications()
                            } catch (e: Exception) {
                                syncStatus = Strings.whatsappAddressSyncFailed(e.message ?: "unknown error")
                            } finally {
                                syncRunning = false
                            }
                        }
                    },
                    enabled = !syncRunning
                ) {
                    if (syncRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(Strings.syncWhatsappAddresses)
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            runSmsImport()
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    },
                    enabled = !syncRunning
                ) { Text(Strings.importSmsHistory) }
            }
            item {
                OutlinedButton(
                    onClick = {
                        syncRunning = true
                        syncStatus = null
                        scope.launch {
                            try {
                                val stats = viewModel.importServerCommunicationHistory()
                                syncStatus = communicationImportStatus(stats)
                                comms.value = viewModel.loadAllCommunications()
                            } catch (e: Exception) {
                                syncStatus = Strings.communicationImportFailed(e.message ?: "unknown error")
                            } finally {
                                syncRunning = false
                            }
                        }
                    },
                    enabled = !syncRunning
                ) { Text(Strings.importServerMessageHistory) }
            }
            syncStatus?.let {
                item { Text(it, fontSize = 12.sp, color = Color.Gray) }
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
    private var recentVoiceContact: RecentVoiceContact? = null
    private val recentVoiceContactTtlMs = 5 * 60 * 1000L

    private data class RecentVoiceContact(
        val name: String,
        val address: String?,
        val phone: String?,
        val savedAtMs: Long
    )
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val token = settingsManager?.accessToken
            val request = if (token != null) {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            } else original
            chain.proceed(request)
        }
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

    private fun extractPermissionMap(raw: Any?): Map<String, Boolean> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.entries.mapNotNull { (key, value) ->
            val code = key?.toString() ?: return@mapNotNull null
            code to when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> false
            }
        }.toMap()
    }

    private fun normalizedAppLanguageCode(langCode: String?): String {
        val source = langCode?.takeIf { it.isNotBlank() } ?: settingsManager?.appLanguage ?: "cs"
        return when (Strings.fromCode(source)) {
            Strings.Lang.EN -> "en"
            Strings.Lang.CS -> "cs"
            Strings.Lang.PL -> "pl"
        }
    }

    private fun applyAppLanguage(langCode: String?, persist: Boolean, refreshVoice: Boolean = true) {
        val normalized = normalizedAppLanguageCode(langCode)
        if (persist) {
            settingsManager?.setCurrentAppLanguage(normalized)
            // Reset recognition language to match new app language by default
            settingsManager?.recognitionLanguage = ""
        }
        Strings.setLanguage(normalized)
        val recognitionLocale = settingsManager?.recognitionLanguage
            ?.takeIf { it.isNotBlank() }
            ?: Strings.getRecognitionLocale()
        _uiState.value = _uiState.value.copy(
            appLanguage = normalized,
            recognitionLocale = recognitionLocale
        )
        if (refreshVoice) {
            voiceManager?.refreshLanguage()
        }
    }

    private fun applyCurrentUserData(raw: Map<String, @JvmSuppressWildcards Any?>?) {
        val user = raw ?: emptyMap()
        val nestedUser = user["user"] as? Map<*, *>
        val source = if (nestedUser != null) {
            nestedUser.entries.associate { (k, v) -> k.toString() to v }
        } else {
            user
        }
        val userId = when (val value = source["id"]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        val displayName = source["display_name"]?.toString()
        val email = source["email"]?.toString()
        val role = source["role"]?.toString()
            ?: source["role_name"]?.toString()
        val permissions = extractPermissionMap(source["permissions"])
        val mustChangePassword = when (val value = source["must_change_password"] ?: user["must_change_password"]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
        settingsManager?.setCurrentBackendUser(userId, role)
        val preferredLang = source["preferred_language_code"]?.toString()?.takeIf { it.isNotBlank() }
        val resolvedLang = settingsManager?.getAppLanguageForUser(userId, preferredLang ?: settingsManager?.appLanguage ?: "cs")
            ?: preferredLang
            ?: "cs"
        applyAppLanguage(resolvedLang, persist = true, refreshVoice = false)
        _uiState.value = _uiState.value.copy(
            currentUserId = userId,
            currentUserDisplayName = displayName,
            currentUserEmail = email,
            currentUserRole = role,
            currentUserPermissions = permissions,
            mustChangePassword = mustChangePassword
        )
    }

    fun setManagers(vm: VoiceManager?, cm: CalendarManager, ctm: ContactManager, mm: MailManager, sm: SettingsManager) {
        voiceManager = vm; calendarManager = cm; contactManager = ctm; mailManager = mm; settingsManager = sm
        applyAppLanguage(sm.getCurrentAppLanguage(), persist = false, refreshVoice = false)
        sanitizeVoiceAliasesStore()
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
                        applyCurrentUserData(res.body())
                        if (_uiState.value.mustChangePassword) {
                            settingsManager?.accessToken = null
                            settingsManager?.refreshToken = null
                            settingsManager?.clearCurrentBackendUser()
                            _uiState.value = _uiState.value.copy(
                                loggedIn = false,
                                firstLoginUsers = emptyList(),
                                currentUserId = null,
                                currentUserDisplayName = null,
                                currentUserEmail = null,
                                currentUserRole = null,
                                currentUserPermissions = emptyMap(),
                                mustChangePassword = false,
                                loginNotice = Strings.firstLoginPasswordChangeRequired
                            )
                            return@launch
                        }
                        invalidateAliasCache()
                        _uiState.value = _uiState.value.copy(loggedIn = true)
                        loadTenantConfig()
                        checkOnboardingStatus()
                        return@launch
                    }
                } catch (_: Exception) {}
                // Token invalid — try refresh
                if (tryRefreshToken()) {
                    try {
                        val refreshed = api.authMe("Bearer ${settingsManager?.accessToken}")
                        if (refreshed.isSuccessful) {
                            applyCurrentUserData(refreshed.body())
                            if (_uiState.value.mustChangePassword) {
                                settingsManager?.accessToken = null
                                settingsManager?.refreshToken = null
                                settingsManager?.clearCurrentBackendUser()
                                _uiState.value = _uiState.value.copy(
                                    loggedIn = false,
                                    firstLoginUsers = emptyList(),
                                    currentUserId = null,
                                    currentUserDisplayName = null,
                                    currentUserEmail = null,
                                    currentUserRole = null,
                                    currentUserPermissions = emptyMap(),
                                    mustChangePassword = false,
                                    loginNotice = Strings.firstLoginPasswordChangeRequired
                                )
                                return@launch
                            }
                            _uiState.value = _uiState.value.copy(loggedIn = true)
                            loadTenantConfig()
                            checkOnboardingStatus()
                            return@launch
                        }
                    } catch (_: Exception) {}
                }
            }
            // No valid token — show login screen
            settingsManager?.clearCurrentBackendUser()
            _uiState.value = _uiState.value.copy(
                loggedIn = false,
                firstLoginUsers = emptyList(),
                currentUserId = null,
                currentUserDisplayName = null,
                currentUserEmail = null,
                currentUserRole = null,
                currentUserPermissions = emptyMap(),
                mustChangePassword = false,
                awaitingBiometricEnrollment = false
            )
            loadFirstLoginUsers()
        }
    }

    fun login(email: String, password: String, fromBiometric: Boolean = false, onError: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.authLogin(mapOf("email" to email, "password" to password))
                if (res.isSuccessful) {
                    val body = res.body()
                    settingsManager?.accessToken = body?.get("access_token")?.toString()
                    settingsManager?.refreshToken = body?.get("refresh_token")?.toString()
                    applyCurrentUserData(body)
                    if (_uiState.value.mustChangePassword) {
                        loadFirstLoginUsers()
                        _uiState.value = _uiState.value.copy(
                            loggedIn = false,
                            awaitingBiometricEnrollment = false,
                            loginNotice = Strings.firstLoginPasswordChangeRequired
                        )
                        onError(null)
                        return@launch
                    }
                    if (fromBiometric) {
                        _uiState.value = _uiState.value.copy(
                            loggedIn = true,
                            awaitingBiometricEnrollment = false,
                            loginNotice = null
                        )
                        loadTenantConfig()
                        checkOnboardingStatus()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            loggedIn = false,
                            firstLoginUsers = _uiState.value.firstLoginUsers.filterNot { it.email.equals(email, ignoreCase = true) },
                            awaitingBiometricEnrollment = true,
                            loginNotice = null
                        )
                    }
                    onError(null) // signal success to caller
                } else {
                    onError(parseAuthError(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                onError(e.message?.let { Strings.connectionProblem(it) } ?: Strings.connectionError)
            }
        }
    }

    fun finalizeLoginAfterCredentialSetup() {
        _uiState.value = _uiState.value.copy(
            loggedIn = true,
            awaitingBiometricEnrollment = false,
            loginNotice = null
        )
        loadTenantConfig()
        checkOnboardingStatus()
    }

    fun createBackendUser(
        email: String,
        displayName: String,
        role: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = api.registerUser(
                    RegisterRequest(
                        email = email.trim(),
                        display_name = cleanUserDisplayName(displayName),
                        role = role
                    )
                )
                if (res.isSuccessful) {
                    loadBackendUsers()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, parseBackendAdminError(res.code(), rawError, Strings.createUserFailed))
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: Strings.createUserFailed)
            }
        }
    }

    fun loadBackendRoles() {
        viewModelScope.launch {
            try {
                val res = api.getAuthRoles()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(backendRoles = res.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Backend roles load error", e)
            }
        }
    }

    fun loadFirstLoginUsers() {
        viewModelScope.launch {
            try {
                val res = api.getFirstLoginUsers()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(firstLoginUsers = res.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "First login users load error", e)
            }
        }
    }

    fun loadBackendUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backendUsersLoading = true, backendUsersError = null)
            try {
                val res = api.getAuthUsers()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        backendUsers = res.body() ?: emptyList(),
                        backendUsersLoading = false,
                        backendUsersError = null
                    )
                } else {
                    val rawError = res.errorBody()?.string()
                    _uiState.value = _uiState.value.copy(
                        backendUsers = emptyList(),
                        backendUsersLoading = false,
                        backendUsersError = parseBackendAdminError(res.code(), rawError, Strings.backendUsersLoadFailed)
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Backend users load error", e)
                _uiState.value = _uiState.value.copy(
                    backendUsers = emptyList(),
                    backendUsersLoading = false,
                    backendUsersError = e.message ?: Strings.backendUsersLoadFailed
                )
            }
        }
    }

    fun updateBackendUser(
        userId: Long,
        displayName: String,
        phone: String?,
        role: String,
        status: String,
        permissions: Map<String, Boolean>,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = api.updateAuthUser(
                    userId,
                    mapOf(
                        "display_name" to cleanUserDisplayName(displayName),
                        "phone" to phone?.trim().orEmpty(),
                        "role" to role,
                        "status" to status,
                        "permissions" to permissions
                    )
                )
                if (res.isSuccessful) {
                    loadBackendUsers()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, parseBackendAdminError(res.code(), rawError, Strings.updateUserFailed))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Backend user update error", e)
                onDone(false, e.message ?: Strings.updateUserFailed)
            }
        }
    }

    fun ensureBackendUsersLoaded() {
        val state = _uiState.value
        if (!state.backendUsersLoading && state.backendUsers.isEmpty()) {
            loadBackendUsers()
        }
    }

    fun loadAssistantMemory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(assistantMemoryLoading = true, assistantMemoryError = null)
            try {
                val res = api.getAssistantMemory(limit = 100)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        assistantMemory = res.body() ?: emptyList(),
                        assistantMemoryLoading = false,
                        assistantMemoryError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        assistantMemoryLoading = false,
                        assistantMemoryError = Strings.assistantMemoryLoadFailed
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Assistant memory load error", e)
                _uiState.value = _uiState.value.copy(
                    assistantMemoryLoading = false,
                    assistantMemoryError = e.message ?: Strings.assistantMemoryLoadFailed
                )
            }
        }
    }

    fun deleteAssistantMemory(memoryId: Long) {
        viewModelScope.launch {
            try {
                val res = api.deleteAssistantMemory(memoryId)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        assistantMemory = _uiState.value.assistantMemory.filterNot { it.id == memoryId },
                        assistantMemoryError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(assistantMemoryError = Strings.assistantMemoryDeleteFailed)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Assistant memory delete error", e)
                _uiState.value = _uiState.value.copy(
                    assistantMemoryError = e.message ?: Strings.assistantMemoryDeleteFailed
                )
            }
        }
    }

    fun rememberAssistantMemory(content: String) {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(assistantMemoryLoading = true, assistantMemoryError = null)
            try {
                val res = api.rememberAssistantMemory(mapOf("content" to cleanContent, "memory_type" to "long"))
                if (res.isSuccessful) {
                    loadAssistantMemory()
                } else {
                    _uiState.value = _uiState.value.copy(
                        assistantMemoryLoading = false,
                        assistantMemoryError = Strings.assistantMemorySaveFailed
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Assistant memory save error", e)
                _uiState.value = _uiState.value.copy(
                    assistantMemoryLoading = false,
                    assistantMemoryError = e.message ?: Strings.assistantMemorySaveFailed
                )
            }
        }
    }

    private fun shouldLoadHierarchyIntegrity(): Boolean {
        val state = _uiState.value
        return state.currentUserPermissions["manage_users"] == true ||
            state.currentUserRole == "admin" ||
            state.currentUserRole == "manager"
    }

    fun loadHierarchyIntegrity() {
        if (!shouldLoadHierarchyIntegrity()) {
            _uiState.value = _uiState.value.copy(
                hierarchyIntegrityReport = null,
                hierarchyIntegrityLoading = false,
                hierarchyIntegrityError = null
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(hierarchyIntegrityLoading = true, hierarchyIntegrityError = null)
            try {
                val res = api.getHierarchyIntegrity()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        hierarchyIntegrityReport = res.body(),
                        hierarchyIntegrityLoading = false,
                        hierarchyIntegrityError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        hierarchyIntegrityLoading = false,
                        hierarchyIntegrityError = Strings.hierarchyReportLoadFailed
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Hierarchy integrity load error", e)
                _uiState.value = _uiState.value.copy(
                    hierarchyIntegrityLoading = false,
                    hierarchyIntegrityError = e.message ?: Strings.hierarchyReportLoadFailed
                )
            }
        }
    }

    fun isCurrentNextActionTask(taskId: String): Boolean {
        val state = _uiState.value
        return state.clients.any { it.next_action_task_id == taskId } || state.jobs.any { it.next_action_task_id == taskId }
    }

    fun getReplacementCandidates(task: Task): List<Task> {
        return _uiState.value.tasks.filter { candidate ->
            candidate.id != task.id &&
                !candidate.isCompleted &&
                candidate.status != "hotovo" &&
                candidate.status != "zruseno" &&
                candidate.assignedUserId != null &&
                taskHasPlanning(candidate) &&
                when {
                    task.jobId != null -> candidate.jobId == task.jobId
                    task.clientId != null -> candidate.clientId == task.clientId && candidate.jobId == null
                    else -> false
                }
        }
    }

    fun loadAdminActivityLog(actorUserId: Long? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(adminActivityLoading = true, adminActivityError = null)
            try {
                val res = api.getAdminActivityLog(limit = 300, actorUserId = actorUserId)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        adminActivityLog = res.body() ?: emptyList(),
                        adminActivityLoading = false,
                        adminActivityError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        adminActivityLog = emptyList(),
                        adminActivityLoading = false,
                        adminActivityError = Strings.adminLogsLoadFailed
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Admin activity load error", e)
                _uiState.value = _uiState.value.copy(
                    adminActivityLog = emptyList(),
                    adminActivityLoading = false,
                    adminActivityError = e.message ?: Strings.adminLogsLoadFailed
                )
            }
        }
    }

    fun resetBackendUserPassword(userId: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.updateAuthUser(userId, mapOf("reset_password_to_default" to true))
                if (res.isSuccessful) {
                    loadBackendUsers()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, parseBackendAdminError(res.code(), rawError, Strings.passwordResetFailed))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Backend user password reset error", e)
                onDone(false, e.message ?: Strings.passwordResetFailed)
            }
        }
    }

    fun deleteBackendUser(userId: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.deleteAuthUser(userId)
                if (res.isSuccessful) {
                    loadBackendUsers()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, parseBackendAdminError(res.code(), rawError, Strings.deleteUserFailed))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Backend user delete error", e)
                onDone(false, e.message ?: Strings.deleteUserFailed)
            }
        }
    }

    private fun parseBackendAdminError(code: Int, rawError: String?, fallbackAction: String): String {
        val detail = rawError?.let {
            try {
                org.json.JSONObject(it).optString("detail").ifBlank { it }
            } catch (_: Exception) {
                it
            }
        }?.trim()
        val normalized = detail?.lowercase(Locale.ROOT)

        return when {
            code == 403 -> Strings.backendPermissionDenied()
            code == 409 || normalized?.contains("already registered") == true -> Strings.backendUserAlreadyExists()
            normalized?.contains("cannot delete yourself") == true -> Strings.backendCannotDeleteSelf()
            normalized?.contains("user not found") == true -> Strings.backendUserNotFound()
            normalized?.contains("unknown role") == true -> Strings.backendUnknownRole()
            normalized?.contains("nothing to update") == true -> Strings.backendNothingToUpdate()
            normalized?.contains("default password") == true -> Strings.passwordMustDifferFromDefault
            !detail.isNullOrBlank() -> detail
            else -> Strings.backendActionFailed(fallbackAction, code)
        }
    }

    private fun extractErrorDetail(rawError: String?): String? = rawError?.let {
        try {
            org.json.JSONObject(it).optString("detail").ifBlank { it }
        } catch (_: Exception) {
            it
        }
    }?.trim()

    private fun parseWorkflowError(code: Int, rawError: String?, fallbackAction: String): String {
        val detail = extractErrorDetail(rawError)
        return when {
            code == 403 -> Strings.backendPermissionDenied()
            !detail.isNullOrBlank() -> detail
            else -> Strings.backendActionFailed(fallbackAction, code)
        }
    }

    private fun parseAuthError(code: Int, rawError: String?): String {
        val detail = rawError?.let {
            try {
                org.json.JSONObject(it).optString("detail").ifBlank { it }
            } catch (_: Exception) {
                it
            }
        }?.trim()
        val normalized = detail?.lowercase(Locale.ROOT)
        return when {
            code == 401 || normalized?.contains("invalid credentials") == true -> Strings.invalidCredentials
            normalized?.contains("old password incorrect") == true -> Strings.oldPasswordIncorrect
            normalized?.contains("at least 6 characters") == true -> Strings.passwordMinLength
            normalized?.contains("different from the default password") == true -> Strings.passwordMustDifferFromDefault
            !detail.isNullOrBlank() -> detail
            else -> Strings.serverError(code)
        }
    }

    private fun parseApiError(rawError: String?): String {
        val detail = rawError?.let {
            try {
                org.json.JSONObject(it).optString("detail").ifBlank { it }
            } catch (_: Exception) {
                it
            }
        }?.trim()?.replace(Regex("""\b[a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)+Exception:\s*"""), "")?.trim()
        val normalized = detail?.lowercase(Locale.ROOT)
        return when {
            detail.isNullOrBlank() -> Strings.connectionError
            detail.contains("not configured", ignoreCase = true) && detail.contains("mushroom", ignoreCase = true) -> Strings.mushroomRecognitionUnavailable
            detail.contains("not configured", ignoreCase = true) && detail.contains("disease", ignoreCase = true) -> Strings.plantHealthUnavailable
            detail.contains("not configured", ignoreCase = true) -> Strings.plantRecognitionUnavailable
            normalized?.contains("rejected the api key") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionAuthError
            normalized?.contains("rejected the api key") == true && normalized.contains("disease") -> Strings.plantHealthAuthError
            normalized?.contains("rejected the api key") == true -> Strings.plantRecognitionAuthError
            normalized?.contains("api key") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionAuthError
            normalized?.contains("api key") == true && normalized.contains("disease") -> Strings.plantHealthAuthError
            normalized?.contains("api key") == true && normalized.contains("plant") -> Strings.plantRecognitionAuthError
            normalized?.contains("unauthorized") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionAuthError
            normalized?.contains("unauthorized") == true && normalized.contains("disease") -> Strings.plantHealthAuthError
            normalized?.contains("unauthorized") == true && normalized.contains("plant") -> Strings.plantRecognitionAuthError
            normalized?.contains("forbidden") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionAuthError
            normalized?.contains("forbidden") == true && normalized.contains("disease") -> Strings.plantHealthAuthError
            normalized?.contains("forbidden") == true && normalized.contains("plant") -> Strings.plantRecognitionAuthError
            normalized?.contains("service unavailable") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionUnavailable
            normalized?.contains("service unavailable") == true && normalized.contains("disease") -> Strings.plantHealthUnavailable
            normalized?.contains("service unavailable") == true && normalized.contains("plant") -> Strings.plantRecognitionUnavailable
            normalized?.contains("network error") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionNetworkError
            normalized?.contains("network error") == true && normalized.contains("disease") -> Strings.plantHealthNetworkError
            normalized?.contains("network error") == true -> Strings.plantRecognitionNetworkError
            normalized?.contains("failed") == true && normalized.contains("mushroom") -> Strings.mushroomRecognitionFailed
            normalized?.contains("failed") == true && normalized.contains("disease") -> Strings.plantHealthFailed
            normalized?.contains("failed") == true && normalized.contains("plant") -> Strings.plantRecognitionFailed
            normalized?.contains("maximum of 5") == true || normalized?.contains("maximálně 5") == true || normalized?.contains("maksymalnie 5") == true -> Strings.plantTooManyPhotos
            normalized?.contains("is empty") == true || normalized?.contains("je prázdný") == true || normalized?.contains("jest pusty") == true -> Strings.plantEmptyPhoto
            else -> detail
        }
    }

    private fun parseRecognitionThrowable(error: Throwable, fallback: String): String {
        val raw = error.message?.trim().orEmpty()
        val normalized = raw.lowercase(Locale.ROOT)
        if (raw.isBlank()) return fallback
        if (normalized.contains("illegalstateexception") || normalized.contains("java.lang.")) return fallback
        if (normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout")) {
            return Strings.connectionError
        }
        if (normalized.contains("api key") || normalized.contains("unauthorized") || normalized.contains("forbidden")) {
            return when {
                fallback == Strings.mushroomRecognitionFailed -> Strings.mushroomRecognitionAuthError
                fallback == Strings.plantHealthFailed -> Strings.plantHealthAuthError
                fallback == Strings.plantRecognitionFailed -> Strings.plantRecognitionAuthError
                else -> fallback
            }
        }
        if (normalized.contains("service is not configured") || normalized.contains("service unavailable")) {
            return when {
                fallback == Strings.mushroomRecognitionFailed -> Strings.mushroomRecognitionUnavailable
                fallback == Strings.plantHealthFailed -> Strings.plantHealthUnavailable
                fallback == Strings.plantRecognitionFailed -> Strings.plantRecognitionUnavailable
                else -> fallback
            }
        }
        return raw.replace(Regex("""\b[a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)+Exception:\s*"""), "").trim().ifBlank { fallback }
    }

    private fun optionalMultipartText(value: Any?): okhttp3.RequestBody? {
        val text = value?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return text.toRequestBody("text/plain".toMediaType())
    }

    fun logout() {
        settingsManager?.accessToken = null
        settingsManager?.refreshToken = null
        settingsManager?.clearCurrentBackendUser()
        _uiState.value = _uiState.value.copy(
            loggedIn = false,
            onboardingComplete = null,
            tenantConfig = null,
            backendUsers = emptyList(),
            backendRoles = emptyList(),
            adminActivityLog = emptyList(),
            adminActivityLoading = false,
            adminActivityError = null,
            hierarchyIntegrityReport = null,
            hierarchyIntegrityLoading = false,
            hierarchyIntegrityError = null,
            firstLoginUsers = emptyList(),
            backendUsersLoading = false,
            backendUsersError = null,
            calendarFeed = emptyList(),
            currentUserId = null,
            currentUserDisplayName = null,
            currentUserEmail = null,
            currentUserRole = null,
            currentUserPermissions = emptyMap(),
            mustChangePassword = false,
            awaitingBiometricEnrollment = false,
            loginNotice = null
        )
        loadFirstLoginUsers()
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
                settingsManager?.accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
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
                val now = System.currentTimeMillis()
                if (res.isSuccessful) {
                    val config = res.body()
                    val err = config?.get("error")?.toString()
                    _uiState.value = _uiState.value.copy(
                        tenantConfig = config,
                        tenantConfigError = err,
                        tenantConfigRefreshMs = now
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        tenantConfigError = "HTTP ${res.code()}: ${res.message()}",
                        tenantConfigRefreshMs = System.currentTimeMillis()
                    )
                    Log.e("ViewModel", "Tenant config HTTP error: ${res.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tenantConfigError = e.message ?: "Network error",
                    tenantConfigRefreshMs = System.currentTimeMillis()
                )
                Log.e("ViewModel", "Tenant config load error", e)
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            val errors = mutableMapOf<String, String>()
            var versionInfo: Map<String, Any?>? = null
            var profile: Map<String, Any?>? = null
            var languages: Map<String, Any?>? = null

            // /version
            try {
                val res = api.getServerVersion()
                if (res.isSuccessful) versionInfo = res.body()
                else errors["version"] = "HTTP ${res.code()}"
            } catch (e: Exception) {
                errors["version"] = e.message ?: "error"
                Log.e("ViewModel", "loadSettings /version error", e)
            }

            // /tenant/profile
            try {
                val res = api.getTenantProfile()
                if (res.isSuccessful) profile = res.body()
                else errors["profile"] = "HTTP ${res.code()}"
            } catch (e: Exception) {
                errors["profile"] = e.message ?: "error"
                Log.e("ViewModel", "loadSettings /tenant/profile error", e)
            }

            // /tenant/languages
            try {
                val res = api.getTenantLanguages()
                if (res.isSuccessful) languages = res.body()
                else errors["languages"] = "HTTP ${res.code()}"
            } catch (e: Exception) {
                errors["languages"] = e.message ?: "error"
                Log.e("ViewModel", "loadSettings /tenant/languages error", e)
            }

            _uiState.value = _uiState.value.copy(
                serverVersionInfo = versionInfo,
                tenantProfile = profile,
                tenantLanguages = languages,
                settingsLoadErrors = errors,
                settingsLastRefreshMs = System.currentTimeMillis()
            )
        }
    }

    fun updateCustomerLanguage(customerLang: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val canManage = _uiState.value.currentUserPermissions["manage_users"] == true ||
                    _uiState.value.currentUserRole == "admin"
                if (!canManage) {
                    onDone(false, Strings.backendPermissionDenied())
                    return@launch
                }
                val res = api.updateTenantLanguages(1, mapOf("default_customer_language_code" to customerLang))
                if (res.isSuccessful) {
                    loadTenantConfig()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, parseBackendAdminError(res.code(), rawError, Strings.save))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Customer language update error", e)
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun submitOnboarding(
        companyName: String, legalType: String,
        industries: List<IndustryEntry>,
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
                val industriesPayload = industries.map { entry ->
                    mapOf("industry_group_id" to entry.groupId, "industry_subtype_id" to entry.subtypeId)
                }
                val data = mapOf<String, Any?>(
                    "tenant_id" to 1, "company_name" to companyName, "legal_type" to legalType,
                    "industries" to industriesPayload,
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

    suspend fun syncWhatsappAddressesFromMessages(): Pair<Int, Int> {
        val res = api.syncWhatsappAddresses(
            mapOf(
                "apply" to true,
                "overwrite" to false,
                "limit" to 10000
            )
        )
        if (!res.isSuccessful) {
            throw IllegalStateException(res.errorBody()?.string() ?: "HTTP ${res.code()}")
        }
        val body = res.body() ?: emptyMap()
        val summary = body["summary"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val updated = (summary["updated"] as? Number)?.toInt() ?: 0
        val scanned = (summary["scanned"] as? Number)?.toInt() ?: 0
        refreshCrmData()
        return updated to scanned
    }

    private fun parseCommunicationImportStats(body: Map<String, @JvmSuppressWildcards Any?>): CommunicationImportStats {
        val summary = body["summary"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return CommunicationImportStats(
            scanned = (summary["scanned"] as? Number)?.toInt() ?: 0,
            imported = (summary["imported"] as? Number)?.toInt() ?: 0,
            updated = (summary["updated"] as? Number)?.toInt() ?: 0,
            matched = (summary["matched"] as? Number)?.toInt() ?: 0,
            unmatched = (summary["unmatched"] as? Number)?.toInt() ?: 0,
            message = summary["message"] as? String
        )
    }

    suspend fun importSmsMessages(messages: List<Map<String, @JvmSuppressWildcards Any?>>): CommunicationImportStats {
        if (messages.isEmpty()) return CommunicationImportStats(message = "No SMS messages available")
        val res = api.importCommunications(mapOf("source" to "sms", "messages" to messages))
        if (!res.isSuccessful) {
            throw IllegalStateException(res.errorBody()?.string() ?: "HTTP ${res.code()}")
        }
        val stats = parseCommunicationImportStats(res.body() ?: emptyMap())
        refreshCrmData()
        return stats
    }

    suspend fun importServerCommunicationHistory(): CommunicationImportStats {
        val res = api.importProviderCommunicationHistory(mapOf("limit" to 5000))
        if (!res.isSuccessful) {
            throw IllegalStateException(res.errorBody()?.string() ?: "HTTP ${res.code()}")
        }
        val stats = parseCommunicationImportStats(res.body() ?: emptyMap())
        refreshCrmData()
        return stats
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
        val dateStr = task.plannedStartAt ?: task.deadline ?: task.plannedDate ?: return
        try {
            val startMs = parseCalendarDate(dateStr) ?: return
            val endMs = parseCalendarDate(task.plannedEndAt, defaultEnd = startMs + 3600000L) ?: (startMs + 3600000L)
            val description = buildString {
                append("SECRETARY_KEY:task:${task.id}")
                if (!task.assignedTo.isNullOrBlank()) append("\n${Strings.assigned}: ${task.assignedTo}")
                if (!task.clientName.isNullOrBlank()) append("\n${Strings.client}: ${task.clientName}")
                if (!task.planningNote.isNullOrBlank()) append("\n${task.planningNote}")
            }
            calendarManager?.addEvent(task.title, startMs, endMs, description)
            setStatus(Strings.addedToCalendar(task.title))
        } catch (e: Exception) { Log.e("ViewModel", "Calendar add error", e); setStatus(Strings.connectionError) }
    }

    fun syncPlanningCalendar() {
        val entries = _uiState.value.calendarFeed
        if (entries.isEmpty()) {
            setStatus(Strings.noCalendarEntries)
            return
        }
        val synced = calendarManager?.syncPlanningEntries(entries) == true
        setStatus(if (synced) Strings.planningCalendarSynced(entries.size) else Strings.planningCalendarSyncFailed)
    }

    private fun parseCalendarDate(raw: String?, defaultEnd: Long? = null): Long? {
        if (raw.isNullOrBlank()) return defaultEnd
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val parsed = sdf.parse(raw) ?: continue
                return if (pattern == "yyyy-MM-dd") parsed.time + 9 * 3600000L else parsed.time
            } catch (_: Exception) {
            }
        }
        return defaultEnd
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

    fun completeTask(
        taskId: String,
        replacementTaskId: String? = null,
        replacementDraft: WorkflowActionDraft? = null,
        onDone: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val payload = mutableMapOf<String, Any?>(
                    "is_completed" to true,
                    "status" to "hotovo"
                )
                if (!replacementTaskId.isNullOrBlank()) {
                    payload["replacement_task_id"] = replacementTaskId
                }
                if (replacementDraft != null) {
                    payload["replacement_task_payload"] = mapOf(
                        "title" to replacementDraft.title,
                        "assigned_user_id" to replacementDraft.assignedUserId,
                        "assigned_to" to replacementDraft.assignedTo,
                        "planned_start_at" to replacementDraft.plannedStartAt,
                        "deadline" to replacementDraft.deadline,
                        "priority" to replacementDraft.priority,
                        "planning_note" to replacementDraft.planningNote
                    )
                }
                val res = api.updateTask(taskId, payload)
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone?.invoke(true, null)
                } else {
                    onDone?.invoke(false, parseWorkflowError(res.code(), res.errorBody()?.string(), Strings.completeTask))
                }
            } catch (e: Exception) {
                Log.e("Task", "Complete sync error", e)
                onDone?.invoke(false, e.message ?: Strings.hierarchyActionFailed)
            }
        }
    }

    fun createTaskManual(draft: TaskCreationDraft, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val assigneeName = draft.assignedTo
                    ?: _uiState.value.backendUsers.firstOrNull { it.id == draft.assignedUserId }?.let(::hierarchyUserLabel)
                val payload = mapOf<String, Any?>(
                    "title" to draft.title.trim(),
                    "task_type" to draft.taskType,
                    "priority" to draft.priority,
                    "client_id" to draft.clientId,
                    "client_name" to draft.clientName,
                    "job_id" to draft.jobId,
                    "assigned_user_id" to draft.assignedUserId,
                    "assigned_to" to assigneeName,
                    "planned_start_at" to draft.plannedStartAt,
                    "deadline" to draft.deadline,
                    "planning_note" to draft.planningNote,
                    "set_as_next_action" to draft.setAsNextAction,
                    "source" to "manualne"
                )
                val res = api.createTask(payload)
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    onDone(false, parseWorkflowError(res.code(), res.errorBody()?.string(), Strings.newTask))
                }
            } catch (e: Exception) {
                Log.e("Task", "Create sync error", e)
                onDone(false, e.message ?: Strings.hierarchyActionFailed)
            }
        }
    }

    fun setStatus(status: String) { _uiState.value = _uiState.value.copy(status = status) }
    fun setListening(isL: Boolean) { _uiState.value = _uiState.value.copy(isListening = isL) }
    fun startListening() { voiceManager?.startListening() }

    // ===== DIALOG MODE =====

    private val dialogEndPhrases = setOf(
        // Czech
        "to staci", "konec rozhovoru", "diky", "dekuji", "ok sbohem", "sbohem",
        "konec", "to je vse", "staci", "prestanem", "dost", "zavriet",
        "ukoncit rozhovor", "ukoncime", "ukoncit dialog",
        // English
        "that's enough", "end conversation", "goodbye", "stop", "thank you",
        "thanks", "we're done", "stop listening", "end dialog", "close",
        // Polish
        "to wystarczy", "koniec", "do widzenia", "dziekuje", "zakonczymy"
    )

    private fun isDialogEndPhrase(text: String): Boolean {
        val norm = text.trim().lowercase()
            .filter { it.isLetterOrDigit() || it == ' ' }
            .replace(Regex(" +"), " ")
            .trim()
        return dialogEndPhrases.any { phrase ->
            norm == phrase || norm.startsWith("$phrase ") || norm.endsWith(" $phrase")
        }
    }

    fun enterDialogMode() {
        val greeting = Strings.dialogModeGreeting
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isDialogMode = true,
            dialogSessionHistory = emptyList(),
            dialogSessionStartMs = now,
            status = "Dialog...",
            lastAiReply = greeting
        )
        // Load history from persistent storage
        val savedHistory = loadPersistedHistory()
        if (savedHistory.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                history = savedHistory.takeLast(20)
            )
        }
        voiceManager?.speak(greeting, expectReply = false)
        voiceManager?.startDialogMode()
    }

    fun exitDialogMode(triggerText: String = "") {
        val state = _uiState.value
        val farewell = Strings.dialogModeFarewell
        _uiState.value = state.copy(
            isDialogMode = false,
            status = Strings.waitingForCommand
        )
        voiceManager?.speak(farewell, expectReply = false)
        // Summarize and store session in background
        val sessionHistory = state.dialogSessionHistory
        if (sessionHistory.size >= 4) {
            viewModelScope.launch {
                try {
                    val langCode = state.appLanguage.substringBefore("-").take(2)
                    val req = SummarizeRequest(
                        history = sessionHistory,
                        user_id = state.currentUserId,
                        tenant_id = 1,
                        internal_language = langCode
                    )
                    val token = settingsManager?.accessToken?.let { "Bearer $it" }
                    if (token != null) {
                        api.summarizeSession(req)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("Dialog", "Session summarize failed: ${e.message}")
                }
            }
        }
        // Persist history for next session
        persistHistory(state.history.takeLast(30))
    }

    private fun persistHistory(history: List<ChatMessage>) {
        try {
            val sm = settingsManager ?: return
            val json = history.joinToString("|||") { "${it.role}::${it.content.replace("|||", "")}" }
            sm.prefsPublic.edit().putString("dialog_history_v1", json).apply()
        } catch (e: Exception) {
            android.util.Log.w("Dialog", "Persist history failed: ${e.message}")
        }
    }

    private fun loadPersistedHistory(): List<ChatMessage> {
        return try {
            val sm = settingsManager ?: return emptyList()
            val raw = sm.prefsPublic.getString("dialog_history_v1", null) ?: return emptyList()
            raw.split("|||").mapNotNull { entry ->
                val parts = entry.split("::", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
                    ChatMessage(parts[0], parts[1])
                else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun speakAddress(address: String) {
        val cleanAddress = address.trim()
        if (cleanAddress.isBlank()) {
            setStatus(Strings.noAddressAvailable)
            return
        }
        val message = Strings.addressForSpeech(cleanAddress)
        _uiState.value = _uiState.value.copy(lastAiReply = message)
        voiceManager?.speak(message, expectReply = false)
    }

    fun onNavigationLaunchHandled(opened: Boolean, address: String) {
        val status = if (opened) Strings.waitingForCommand else Strings.navigationUnavailable(address)
        _uiState.value = _uiState.value.copy(
            pendingNavigationAddress = null,
            awaitingNavigationAddress = false,
            status = status
        )
        if (!opened) voiceManager?.speak(status, expectReply = false)
    }

    fun onCallLaunchHandled(opened: Boolean, phone: String) {
        _uiState.value = _uiState.value.copy(
            pendingCall = null,
            status = if (opened) Strings.waitingForCommand else Strings.callUnavailable(phone)
        )
    }

    fun onWhatsAppLaunchHandled(opened: Boolean, phone: String) {
        _uiState.value = _uiState.value.copy(
            pendingWhatsAppPhone = null,
            pendingWhatsAppMessage = null,
            status = if (opened) Strings.waitingForCommand else Strings.whatsAppUnavailable(phone)
        )
    }

    fun updateContext(id: Long?, type: String?) {
        _uiState.value = _uiState.value.copy(contextEntityId = id, contextType = type)
    }

    fun addJobAuditEntry(jobId: Long, actionType: String, description: String) {
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "job_id" to jobId.toString(),
                    "action_type" to actionType,
                    "description" to description
                )
                api.addJobAuditEntry(jobId, data)
                loadJobDetail(jobId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadJobPhoto(jobId: Long, photoType: String, file: java.io.File, description: String? = null) {
        viewModelScope.launch {
            try {
                val mediaType = "image/*".toMediaType()
                val requestFile = file.asRequestBody(mediaType)
                val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                val textType = "text/plain".toMediaType()
                val descPart = description?.toRequestBody(textType)
                val typePart = photoType.toRequestBody(textType)

                val response = api.uploadJobPhoto(jobId, body, descPart, typePart)
                if (response.isSuccessful) {
                    addJobAuditEntry(jobId, "photo_upload", "Uploaded $photoType photo: ${file.name}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun completeFirstPasswordChange(
        oldPassword: String,
        newPassword: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = api.changePassword(
                    mapOf(
                        "old_password" to oldPassword,
                        "new_password" to newPassword
                    )
                )
                if (response.isSuccessful) {
                    settingsManager?.accessToken?.let { token ->
                        try {
                            val me = api.authMe("Bearer $token")
                            if (me.isSuccessful) applyCurrentUserData(me.body())
                        } catch (_: Exception) {}
                    }
                    _uiState.value = _uiState.value.copy(
                        mustChangePassword = false,
                        loggedIn = false,
                        firstLoginUsers = _uiState.value.firstLoginUsers.filterNot {
                            it.email.equals(_uiState.value.currentUserEmail ?: "", ignoreCase = true)
                        },
                        awaitingBiometricEnrollment = true,
                        loginNotice = null
                    )
                    onDone(true, null)
                } else {
                    onDone(false, parseAuthError(response.code(), response.errorBody()?.string()))
                }
            } catch (e: Exception) {
                onDone(false, e.message?.let { Strings.connectionProblem(it) } ?: Strings.connectionError)
            }
        }
    }

    fun identifyPlant(photos: List<PlantPhotoUpload>, captureContext: RecognitionCaptureContext? = null) {
        val voiceTriggered = _uiState.value.isPlantVoiceCaptureActive
        if (photos.isEmpty()) {
            _uiState.value = _uiState.value.copy(plantRecognitionError = Strings.plantNeedsPhoto)
            if (voiceTriggered) voiceManager?.speak(Strings.plantNeedsPhoto, expectReply = false)
            return
        }
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    plantRecognitionLoading = true,
                    plantRecognitionError = null
                )
                val mediaType = "image/*".toMediaType()
                val imageParts = photos.mapIndexed { index, photo ->
                    val requestFile = photo.file.asRequestBody(mediaType)
                    okhttp3.MultipartBody.Part.createFormData(
                        "images",
                        photo.file.name.ifBlank { "plant_${index + 1}.jpg" },
                        requestFile
                    )
                }
                val textType = "text/plain".toMediaType()
                val organsJson = org.json.JSONArray(photos.map { it.organ }).toString().toRequestBody(textType)
                val language = _uiState.value.appLanguage.toRequestBody(textType)
                val response = api.identifyPlant(
                    imageParts,
                    organsJson,
                    language,
                    optionalMultipartText(captureContext?.capturedAt),
                    optionalMultipartText(captureContext?.latitude),
                    optionalMultipartText(captureContext?.longitude),
                    optionalMultipartText(captureContext?.accuracyMeters),
                    optionalMultipartText(captureContext?.locationSource)
                )
                if (response.isSuccessful) {
                    val result = response.body()
                    _uiState.value = _uiState.value.copy(
                        plantRecognitionLoading = false,
                        plantRecognitionError = null,
                        selectedPlantRecognition = result,
                        isPlantVoiceCaptureActive = false
                    )
                    loadNatureHistory()
                    val spoken = result?.spoken_summary ?: result?.display_name ?: Strings.plantRecognitionTitle
                    if (voiceTriggered) voiceManager?.speak(spoken, expectReply = false)
                } else {
                    val errorText = parseApiError(response.errorBody()?.string())
                    _uiState.value = _uiState.value.copy(
                        plantRecognitionLoading = false,
                        plantRecognitionError = errorText,
                        isPlantVoiceCaptureActive = false
                    )
                    if (voiceTriggered) voiceManager?.speak(errorText, expectReply = false)
                }
            } catch (e: Exception) {
                val message = parseRecognitionThrowable(e, Strings.plantRecognitionFailed)
                _uiState.value = _uiState.value.copy(
                    plantRecognitionLoading = false,
                    plantRecognitionError = message,
                    isPlantVoiceCaptureActive = false
                )
                if (voiceTriggered) voiceManager?.speak(message, expectReply = false)
            }
        }
    }

    fun assessPlantHealth(photos: List<PlantPhotoUpload>, captureContext: RecognitionCaptureContext? = null) {
        val voiceTriggered = _uiState.value.isPlantVoiceCaptureActive
        if (photos.isEmpty()) {
            _uiState.value = _uiState.value.copy(plantDiseaseError = Strings.plantNeedsPhoto)
            if (voiceTriggered) voiceManager?.speak(Strings.plantNeedsPhoto, expectReply = false)
            return
        }
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    plantDiseaseLoading = true,
                    plantDiseaseError = null
                )
                val mediaType = "image/*".toMediaType()
                val imageParts = photos.mapIndexed { index, photo ->
                    val requestFile = photo.file.asRequestBody(mediaType)
                    okhttp3.MultipartBody.Part.createFormData(
                        "images",
                        photo.file.name.ifBlank { "plant_health_${index + 1}.jpg" },
                        requestFile
                    )
                }
                val textType = "text/plain".toMediaType()
                val language = _uiState.value.appLanguage.toRequestBody(textType)
                val response = api.assessPlantHealth(
                    imageParts,
                    language,
                    optionalMultipartText(captureContext?.capturedAt),
                    optionalMultipartText(captureContext?.latitude),
                    optionalMultipartText(captureContext?.longitude),
                    optionalMultipartText(captureContext?.accuracyMeters),
                    optionalMultipartText(captureContext?.locationSource)
                )
                if (response.isSuccessful) {
                    val result = response.body()
                    _uiState.value = _uiState.value.copy(
                        plantDiseaseLoading = false,
                        plantDiseaseError = null,
                        selectedPlantDisease = result,
                        isPlantVoiceCaptureActive = false
                    )
                    loadNatureHistory()
                    val spoken = result?.spoken_summary ?: result?.top_issue_name ?: Strings.plantHealthTitle
                    if (voiceTriggered) voiceManager?.speak(spoken, expectReply = false)
                } else {
                    val errorText = parseApiError(response.errorBody()?.string())
                    _uiState.value = _uiState.value.copy(
                        plantDiseaseLoading = false,
                        plantDiseaseError = errorText,
                        isPlantVoiceCaptureActive = false
                    )
                    if (voiceTriggered) voiceManager?.speak(errorText, expectReply = false)
                }
            } catch (e: Exception) {
                val message = parseRecognitionThrowable(e, Strings.plantHealthFailed)
                _uiState.value = _uiState.value.copy(
                    plantDiseaseLoading = false,
                    plantDiseaseError = message,
                    isPlantVoiceCaptureActive = false
                )
                if (voiceTriggered) voiceManager?.speak(message, expectReply = false)
            }
        }
    }

    fun identifyMushroom(photos: List<PlantPhotoUpload>, captureContext: RecognitionCaptureContext? = null) {
        val voiceTriggered = _uiState.value.isPlantVoiceCaptureActive
        if (photos.isEmpty()) {
            _uiState.value = _uiState.value.copy(mushroomRecognitionError = Strings.plantNeedsPhoto)
            if (voiceTriggered) voiceManager?.speak(Strings.plantNeedsPhoto, expectReply = false)
            return
        }
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    mushroomRecognitionLoading = true,
                    mushroomRecognitionError = null
                )
                val mediaType = "image/*".toMediaType()
                val imageParts = photos.mapIndexed { index, photo ->
                    val requestFile = photo.file.asRequestBody(mediaType)
                    okhttp3.MultipartBody.Part.createFormData(
                        "images",
                        photo.file.name.ifBlank { "mushroom_${index + 1}.jpg" },
                        requestFile
                    )
                }
                val textType = "text/plain".toMediaType()
                val language = _uiState.value.appLanguage.toRequestBody(textType)
                val response = api.identifyMushroom(
                    imageParts,
                    language,
                    optionalMultipartText(captureContext?.capturedAt),
                    optionalMultipartText(captureContext?.latitude),
                    optionalMultipartText(captureContext?.longitude),
                    optionalMultipartText(captureContext?.accuracyMeters),
                    optionalMultipartText(captureContext?.locationSource)
                )
                if (response.isSuccessful) {
                    val result = response.body()
                    _uiState.value = _uiState.value.copy(
                        mushroomRecognitionLoading = false,
                        mushroomRecognitionError = null,
                        selectedMushroomRecognition = result,
                        isPlantVoiceCaptureActive = false
                    )
                    loadNatureHistory()
                    val spoken = result?.spoken_summary ?: result?.display_name ?: Strings.mushroomRecognitionTitle
                    if (voiceTriggered) voiceManager?.speak(spoken, expectReply = false)
                } else {
                    val errorText = parseApiError(response.errorBody()?.string())
                    _uiState.value = _uiState.value.copy(
                        mushroomRecognitionLoading = false,
                        mushroomRecognitionError = errorText,
                        isPlantVoiceCaptureActive = false
                    )
                    if (voiceTriggered) voiceManager?.speak(errorText, expectReply = false)
                }
            } catch (e: Exception) {
                val message = parseRecognitionThrowable(e, Strings.mushroomRecognitionFailed)
                _uiState.value = _uiState.value.copy(
                    mushroomRecognitionLoading = false,
                    mushroomRecognitionError = message,
                    isPlantVoiceCaptureActive = false
                )
                if (voiceTriggered) voiceManager?.speak(message, expectReply = false)
            }
        }
    }

    fun requestPlantCaptureFromVoice(mode: String = "identify") {
        val isHealthMode = mode == "health"
        val isMushroomMode = mode == "mushroom"
        val reply = when {
            isMushroomMode -> Strings.mushroomRecognitionVoiceGuide
            isHealthMode -> Strings.plantHealthVoiceGuide
            else -> Strings.plantRecognitionVoiceGuide
        }
        val title = when {
            isMushroomMode -> Strings.mushroomRecognitionTitle
            isHealthMode -> Strings.plantHealthTitle
            else -> Strings.plantRecognitionTitle
        }
        _uiState.value = _uiState.value.copy(
            history = (_uiState.value.history + ChatMessage("user", title) + ChatMessage("assistant", reply)).takeLast(30),
            lastAiReply = reply,
            status = Strings.plantCaptureReady,
            pendingPlantCaptureRequestId = System.currentTimeMillis(),
            isPlantVoiceCaptureActive = true,
            plantCaptureMode = mode,
            plantRecognitionError = null,
            selectedPlantRecognition = null,
            plantDiseaseError = null,
            selectedPlantDisease = null,
            mushroomRecognitionError = null,
            selectedMushroomRecognition = null
        )
        voiceManager?.speak(reply, expectReply = false, stayIdle = true)
    }

    fun setPlantCaptureMode(mode: String) {
        _uiState.value = _uiState.value.copy(
            plantCaptureMode = mode,
            selectedPlantRecognition = null,
            selectedPlantDisease = null,
            selectedMushroomRecognition = null,
            plantRecognitionError = null,
            plantDiseaseError = null,
            mushroomRecognitionError = null,
            plantRecognitionLoading = false,
            plantDiseaseLoading = false,
            mushroomRecognitionLoading = false
        )
    }

    fun consumePendingPlantCaptureRequest(resumeHotword: Boolean = true) {
        val shouldResumeHotword = _uiState.value.isPlantVoiceCaptureActive
        if (_uiState.value.pendingPlantCaptureRequestId != null) {
            _uiState.value = _uiState.value.copy(pendingPlantCaptureRequestId = null)
        }
        if (resumeHotword && shouldResumeHotword) {
            voiceManager?.startHotwordLoop()
        }
    }

    fun clearPlantRecognitionResult() {
        val shouldResumeHotword = _uiState.value.isPlantVoiceCaptureActive
        _uiState.value = _uiState.value.copy(
            selectedPlantRecognition = null,
            selectedPlantDisease = null,
            selectedMushroomRecognition = null,
            plantRecognitionError = null,
            plantDiseaseError = null,
            mushroomRecognitionError = null,
            plantRecognitionLoading = false,
            plantDiseaseLoading = false,
            mushroomRecognitionLoading = false,
            isPlantVoiceCaptureActive = false,
            pendingPlantCaptureRequestId = null
        )
        if (shouldResumeHotword) {
            voiceManager?.startHotwordLoop()
        }
    }

    fun loadNatureHistory() {
        viewModelScope.launch {
            try {
                val language = _uiState.value.appLanguage
                val res = api.getNatureHistory(limit = 30, language = language)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(recognitionHistory = res.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Nature history load error", e)
            }
        }
    }

    fun refreshCrmData() {
        viewModelScope.launch {
            try {
                val cl = api.getClients(); if (cl.isSuccessful) { _uiState.value = _uiState.value.copy(clients = cl.body() ?: emptyList()); _cachedKnownNames = null }
                val cs = api.getContactSections(); if (cs.isSuccessful) _uiState.value = _uiState.value.copy(contactSections = cs.body() ?: emptyList())
                val sc = api.getSharedContacts(); if (sc.isSuccessful) { _uiState.value = _uiState.value.copy(sharedContacts = sc.body() ?: emptyList()); checkContactDuplicates() }
                val pr = api.getProperties(); if (pr.isSuccessful) _uiState.value = _uiState.value.copy(properties = pr.body() ?: emptyList())
                val jb = api.getJobs(); if (jb.isSuccessful) _uiState.value = _uiState.value.copy(jobs = jb.body() ?: emptyList())
                val ld = api.getLeads(); if (ld.isSuccessful) _uiState.value = _uiState.value.copy(leads = ld.body() ?: emptyList())
                val qt = api.getQuotes(); if (qt.isSuccessful) _uiState.value = _uiState.value.copy(quotes = qt.body() ?: emptyList())
                val iv = api.getInvoices(); if (iv.isSuccessful) _uiState.value = _uiState.value.copy(invoices = iv.body() ?: emptyList())
                val language = _uiState.value.appLanguage
                val nh = api.getNatureHistory(limit = 30, language = language); if (nh.isSuccessful) _uiState.value = _uiState.value.copy(recognitionHistory = nh.body() ?: emptyList())
                loadTasksFromServer()
                loadWorkReportsFromServer()
                loadCalendarFeedFromServer()
                loadHierarchyIntegrity()
                loadTenantRateTypes()
            } catch (e: Exception) { Log.e("ViewModel", "Refresh Error", e) }
        }
    }

    private fun refreshCrmDataKeepTasks() {
        viewModelScope.launch {
            try {
                val cl = api.getClients(); if (cl.isSuccessful) { _uiState.value = _uiState.value.copy(clients = cl.body() ?: emptyList()); _cachedKnownNames = null }
                val cs = api.getContactSections(); if (cs.isSuccessful) _uiState.value = _uiState.value.copy(contactSections = cs.body() ?: emptyList())
                val sc = api.getSharedContacts(); if (sc.isSuccessful) _uiState.value = _uiState.value.copy(sharedContacts = sc.body() ?: emptyList())
                val pr = api.getProperties(); if (pr.isSuccessful) _uiState.value = _uiState.value.copy(properties = pr.body() ?: emptyList())
                val jb = api.getJobs(); if (jb.isSuccessful) _uiState.value = _uiState.value.copy(jobs = jb.body() ?: emptyList())
                val ld = api.getLeads(); if (ld.isSuccessful) _uiState.value = _uiState.value.copy(leads = ld.body() ?: emptyList())
                val qt = api.getQuotes(); if (qt.isSuccessful) _uiState.value = _uiState.value.copy(quotes = qt.body() ?: emptyList())
                val iv = api.getInvoices(); if (iv.isSuccessful) _uiState.value = _uiState.value.copy(invoices = iv.body() ?: emptyList())
                val language = _uiState.value.appLanguage
                val nh = api.getNatureHistory(limit = 30, language = language); if (nh.isSuccessful) _uiState.value = _uiState.value.copy(recognitionHistory = nh.body() ?: emptyList())
                loadTasksFromServer()
                loadWorkReportsFromServer()
                loadCalendarFeedFromServer()
                loadHierarchyIntegrity()
            } catch (e: Exception) { Log.e("ViewModel", "Refresh Error", e) }
        }
    }

    fun loadCalendarFeed(days: Int = 30) {
        viewModelScope.launch {
            loadCalendarFeedFromServer(days)
        }
    }

    private suspend fun loadCalendarFeedFromServer(days: Int = 30) {
        try {
            val res = api.getCalendarFeed(days)
            if (res.isSuccessful) {
                val entries = res.body() ?: emptyList()
                _uiState.value = _uiState.value.copy(calendarFeed = entries)
                if (entries.isNotEmpty()) {
                    calendarManager?.syncPlanningEntries(entries)
                }
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "Calendar feed load error", e)
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
                        plannedStartAt = m["planned_start_at"]?.toString(),
                        plannedEndAt = m["planned_end_at"]?.toString(),
                        assignedUserId = (m["assigned_user_id"] as? Number)?.toLong(),
                        assignedTo = m["assigned_to"]?.toString(),
                        planningNote = m["planning_note"]?.toString(),
                        reminderForAssigneeOnly = m["reminder_for_assignee_only"] as? Boolean ?: true,
                        clientName = m["client_name"]?.toString(),
                        clientId = (m["client_id"] as? Number)?.toLong(),
                        createdBy = m["created_by"]?.toString(),
                        result = m["result"]?.toString(),
                        calendarSyncEnabled = m["calendar_sync_enabled"] as? Boolean ?: true,
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

    fun loadTenantRateTypes() {
        viewModelScope.launch {
            try {
                val res = api.getDefaultRates(1)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(tenantRateTypes = res.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Load rate types error", e)
            }
        }
    }

    fun loadSystemSettings() {
        viewModelScope.launch {
            try {
                val res = api.getSettings()
                if (res.isSuccessful) _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTED, systemSettings = res.body() ?: emptyMap(), status = Strings.connected)
                else _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED, status = Strings.serverUnavailable)
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED, status = Strings.disconnected) }
        }
    }

    fun loadToolHubTiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toolHubTilesLoading = true)
            try {
                val auth = "Bearer ${settingsManager?.accessToken ?: ""}"
                val res = api.getToolHubTiles(auth)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        toolHubTiles = res.body()?.tiles ?: emptyList(),
                        toolHubTilesLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(toolHubTilesLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toolHubTilesLoading = false)
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.TESTING, status = Strings.testing)
            try {
                val res = api.getSettings()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        systemSettings = res.body() ?: emptyMap(),
                        status = Strings.connected
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        status = Strings.serverUnavailable
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    status = Strings.disconnected
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

    fun createClientManual(draft: ClientCreationDraft, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val actionAssigneeName = _uiState.value.backendUsers.firstOrNull { it.id == draft.firstAction.assignedUserId }?.let(::hierarchyUserLabel)
                val data = mapOf<String, Any?>(
                    "name" to draft.name.trim(),
                    "email" to draft.email.trim().ifBlank { null },
                    "phone" to draft.phone.trim().ifBlank { null },
                    "owner_user_id" to draft.ownerUserId,
                    "first_action" to mapOf(
                        "title" to draft.firstAction.title.trim(),
                        "assigned_user_id" to draft.firstAction.assignedUserId,
                        "assigned_to" to (draft.firstAction.assignedTo ?: actionAssigneeName),
                        "planned_start_at" to draft.firstAction.plannedStartAt,
                        "deadline" to draft.firstAction.deadline,
                        "priority" to draft.firstAction.priority,
                        "planning_note" to draft.firstAction.planningNote
                    )
                )
                val res = api.createClient(data)
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    onDone(false, parseWorkflowError(res.code(), res.errorBody()?.string(), Strings.newClientTitle))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Create client error", e)
                onDone(false, e.message ?: Strings.hierarchyActionFailed)
            }
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

    fun updateClientServiceRates(clientId: Long, rates: Map<String, Any?>, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.updateClientServiceRates(clientId, rates)
                if (res.isSuccessful) {
                    loadClientDetail(clientId)
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    val detail = rawError?.let {
                        try {
                            org.json.JSONObject(it).optString("detail").ifBlank { it }
                        } catch (_: Exception) {
                            it
                        }
                    }?.trim()
                    onDone(false, detail ?: Strings.backendActionFailed(Strings.individualServiceRates, res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Update client service rates error", e)
                onDone(false, e.message ?: Strings.backendActionFailed(Strings.individualServiceRates, 0))
            }
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

    private fun preparePhoneContacts(
        context: Context,
        onlyUkNumbers: Boolean = false,
        skipWithoutPhone: Boolean = false,
        skipWithoutName: Boolean = true,
        removeDuplicates: Boolean = true,
        includeEmail: Boolean = true
    ): List<Map<String, String>> {
        val cm = ContactManager(context)
        var contacts = if (includeEmail) cm.getContactsWithEmail() else cm.getAllContacts()

        if (skipWithoutPhone) contacts = contacts.filter { it["phone"].orEmpty().isNotBlank() }
        if (skipWithoutName) contacts = contacts.filter { it["name"].orEmpty().isNotBlank() }

        contacts = contacts.map { c ->
            val cleanedPhone = c["phone"].orEmpty()
                .replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            val cleanedEmail = c["email"].orEmpty().trim().lowercase(Locale.ROOT)
            val normalizedName = c["name"].orEmpty().trim()
            val contactId = c["contact_id"].orEmpty().trim()
            val phoneDigits = cleanedPhone.filter { it.isDigit() }
            val normalizedPhone = when {
                phoneDigits.startsWith("0044") -> "0" + phoneDigits.removePrefix("0044")
                phoneDigits.startsWith("44") -> "0" + phoneDigits.removePrefix("44")
                else -> phoneDigits
            }
            val contactKey = normalizedPhone
                .ifBlank { cleanedEmail.ifBlank { contactId.ifBlank { normalizedName.lowercase(Locale.ROOT) } } }
            mutableMapOf(
                "contact_key" to contactKey,
                "contact_id" to contactId,
                "name" to normalizedName,
                "phone" to cleanedPhone,
                "email" to cleanedEmail
            ).apply {
                listOf(
                    "address",
                    "address_line1",
                    "city",
                    "postcode",
                    "country",
                    "billing_address_line1",
                    "billing_city",
                    "billing_postcode",
                    "billing_country"
                ).forEach { key ->
                    c[key]?.trim()?.takeIf(String::isNotBlank)?.let { this[key] = it }
                }
            }
        }.filter { it["contact_key"].orEmpty().isNotBlank() }

        if (onlyUkNumbers) {
            contacts = contacts.filter { c ->
                val p = c["phone"].orEmpty()
                p.startsWith("+44") || p.startsWith("0")
            }
        }

        if (removeDuplicates) {
            contacts = contacts.distinctBy { it["contact_key"] }
        }
        return contacts
    }

    fun loadClientSelectionContacts(
        context: Context,
        onDone: (List<SyncedContactCandidate>, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val localContacts = preparePhoneContacts(context)
                val res = api.syncContacts(mapOf("contacts" to localContacts, "filter_uk" to false))
                if (res.isSuccessful) {
                    val remoteMap = (res.body()?.contacts ?: emptyList()).associateBy { it.contact_key }
                    val merged = localContacts.map { local ->
                        val key = local["contact_key"].orEmpty()
                        val remote = remoteMap[key]
                        SyncedContactCandidate(
                            contact_key = key,
                            name = local["name"].orEmpty(),
                            phone = local["phone"],
                            email = local["email"]?.ifBlank { null },
                            address = local["address"]?.ifBlank { null } ?: remote?.address,
                            address_line1 = local["address_line1"]?.ifBlank { null } ?: remote?.address_line1,
                            city = local["city"]?.ifBlank { null } ?: remote?.city,
                            postcode = local["postcode"]?.ifBlank { null } ?: remote?.postcode,
                            country = local["country"]?.ifBlank { null } ?: remote?.country,
                            billing_address_line1 = local["billing_address_line1"]?.ifBlank { null } ?: remote?.billing_address_line1 ?: remote?.address_line1,
                            billing_city = local["billing_city"]?.ifBlank { null } ?: remote?.billing_city ?: remote?.city,
                            billing_postcode = local["billing_postcode"]?.ifBlank { null } ?: remote?.billing_postcode ?: remote?.postcode,
                            billing_country = local["billing_country"]?.ifBlank { null } ?: remote?.billing_country ?: remote?.country,
                            selected_as_client = remote?.selected_as_client == true,
                            linked_client_id = remote?.linked_client_id,
                            linked_client_name = remote?.linked_client_name
                        )
                    }
                    onDone(merged, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    val detail = rawError?.let {
                        try {
                            org.json.JSONObject(it).optString("detail").ifBlank { it }
                        } catch (_: Exception) {
                            it
                        }
                    }
                    onDone(emptyList(), detail ?: Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Load client selection contacts error", e)
                onDone(emptyList(), e.message ?: Strings.connectionError)
            }
        }
    }

    fun saveClientSelectionContacts(
        contacts: List<SyncedContactCandidate>,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val payload = contacts.map { contact ->
                    mapOf(
                        "contact_key" to contact.contact_key,
                        "name" to contact.name,
                        "phone" to contact.phone,
                        "email" to contact.email,
                        "address" to contact.address,
                        "address_line1" to contact.address_line1,
                        "city" to contact.city,
                        "postcode" to contact.postcode,
                        "country" to contact.country,
                        "billing_address_line1" to contact.billing_address_line1,
                        "billing_city" to contact.billing_city,
                        "billing_postcode" to contact.billing_postcode,
                        "billing_country" to contact.billing_country,
                        "selected_as_client" to contact.selected_as_client
                    )
                }
                val res = api.syncContacts(mapOf("contacts" to payload, "filter_uk" to false))
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    val detail = rawError?.let {
                        try {
                            org.json.JSONObject(it).optString("detail").ifBlank { it }
                        } catch (_: Exception) {
                            it
                        }
                    }
                    onDone(false, detail ?: Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Save client selection contacts error", e)
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun loadImportableSharedContacts(
        context: Context,
        availableSections: List<ContactSection>,
        onDone: (List<ImportableSharedContact>, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val defaultSection = availableSections.firstOrNull()?.section_code.orEmpty()
                val localContacts = preparePhoneContacts(context)
                val contacts = localContacts.map {
                    ImportableSharedContact(
                        contact_key = it["contact_key"].orEmpty(),
                        name = it["name"].orEmpty(),
                        phone = it["phone"]?.ifBlank { null },
                        email = it["email"]?.ifBlank { null },
                        address = it["address"]?.ifBlank { null },
                        address_line1 = it["address_line1"]?.ifBlank { null },
                        city = it["city"]?.ifBlank { null },
                        postcode = it["postcode"]?.ifBlank { null },
                        country = it["country"]?.ifBlank { null },
                        selected = false,
                        section_code = defaultSection
                    )
                }
                onDone(contacts, null)
            } catch (e: Exception) {
                Log.e("ViewModel", "Load importable shared contacts error", e)
                onDone(emptyList(), e.message ?: Strings.connectionError)
            }
        }
    }

    fun createContactSection(displayName: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.createContactSection(mapOf("display_name" to displayName))
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, rawError ?: Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Create contact section error", e)
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun saveSharedContact(data: Map<String, Any?>, contactId: Long? = null, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = if (contactId == null) api.createSharedContact(data) else api.updateSharedContact(contactId, data)
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, rawError ?: Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Save shared contact error", e)
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun deleteSharedContact(contactId: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.deleteSharedContact(contactId)
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, rawError ?: Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Delete shared contact error", e)
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun importSharedContacts(contacts: List<ImportableSharedContact>, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = contacts.filter { it.selected }.map {
                    mapOf(
                        "contact_key" to it.contact_key,
                        "display_name" to it.name,
                        "phone_primary" to it.phone,
                        "email_primary" to it.email,
                        "address" to it.address,
                        "address_line1" to it.address_line1,
                        "city" to it.city,
                        "postcode" to it.postcode,
                        "country" to it.country,
                        "section_code" to it.section_code,
                        "selected" to it.selected
                    )
                }
                val res = api.importSharedContacts(mapOf("contacts" to payload))
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    val rawError = res.errorBody()?.string()
                    onDone(false, rawError ?: Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Import shared contacts error", e)
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun syncPhoneContacts(
        context: Context,
        onlyUkNumbers: Boolean = false,
        skipWithoutPhone: Boolean = false,
        skipWithoutName: Boolean = true,
        removeDuplicates: Boolean = true,
        includeEmail: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val contacts = preparePhoneContacts(context, onlyUkNumbers, skipWithoutPhone, skipWithoutName, removeDuplicates, includeEmail)
                val res = api.syncContacts(mapOf("contacts" to contacts, "filter_uk" to onlyUkNumbers))
                if (res.isSuccessful) {
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

    fun addJobNote(jobId: Long, note: String, noteType: String = "general") {
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "job_id" to jobId.toString(),
                    "note" to note,
                    "note_type" to noteType
                )
                val response = api.addJobNote(jobId, data)
                if (response.isSuccessful) {
                    addJobAuditEntry(jobId, "note_added", "Added $noteType note: $note")
                    loadJobDetail(jobId)
                }
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

    fun createJobManual(draft: JobCreationDraft, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val ownerName = _uiState.value.backendUsers.firstOrNull { it.id == draft.assignedUserId }?.let(::hierarchyUserLabel)
                val actionAssigneeName = _uiState.value.backendUsers.firstOrNull { it.id == draft.firstAction.assignedUserId }?.let(::hierarchyUserLabel)
                val data = mapOf<String, Any?>(
                    "title" to draft.title.trim(),
                    "client_id" to draft.clientId,
                    "client_name" to draft.clientName,
                    "assigned_user_id" to draft.assignedUserId,
                    "assigned_to" to (draft.assignedTo ?: ownerName),
                    "start_date" to draft.startDate,
                    "first_action" to mapOf(
                        "title" to draft.firstAction.title.trim(),
                        "assigned_user_id" to draft.firstAction.assignedUserId,
                        "assigned_to" to (draft.firstAction.assignedTo ?: actionAssigneeName),
                        "planned_start_at" to draft.firstAction.plannedStartAt,
                        "deadline" to draft.firstAction.deadline,
                        "priority" to draft.firstAction.priority,
                        "planning_note" to draft.firstAction.planningNote
                    )
                )
                val res = api.createJob(data)
                if (res.isSuccessful) {
                    refreshCrmData()
                    onDone(true, null)
                } else {
                    onDone(false, parseWorkflowError(res.code(), res.errorBody()?.string(), Strings.newJob))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Create job error", e)
                onDone(false, e.message ?: Strings.hierarchyActionFailed)
            }
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

    fun updateTask(taskId: String, data: Map<String, Any?>, onDone: ((Boolean, String?) -> Unit)? = null) {
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
                                plannedDate = data["planned_date"]?.toString() ?: t.plannedDate,
                                plannedStartAt = data["planned_start_at"]?.toString() ?: t.plannedStartAt,
                                plannedEndAt = data["planned_end_at"]?.toString() ?: t.plannedEndAt,
                                assignedUserId = (data["assigned_user_id"] as? Number)?.toLong() ?: t.assignedUserId,
                                assignedTo = data["assigned_to"]?.toString() ?: t.assignedTo,
                                planningNote = data["planning_note"]?.toString() ?: t.planningNote,
                                reminderForAssigneeOnly = data["reminder_for_assignee_only"] as? Boolean ?: t.reminderForAssigneeOnly,
                                calendarSyncEnabled = data["calendar_sync_enabled"] as? Boolean ?: t.calendarSyncEnabled,
                                isCompleted = data["is_completed"] as? Boolean ?: t.isCompleted
                            )
                        } else t
                    }
                    _uiState.value = _uiState.value.copy(tasks = tasks)
                    onDone?.invoke(true, null)
                } else {
                    onDone?.invoke(false, parseWorkflowError(res.code(), res.errorBody()?.string(), Strings.edit))
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Update task error", e)
                onDone?.invoke(false, e.message ?: Strings.hierarchyActionFailed)
            }
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
                val lang = _uiState.value.appLanguage
                val data = mapOf<String, Any?>("tenant_id" to 1, "language" to lang, "work_date" to java.text.SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date()))
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
                val aliasedText = applyVoiceAliasesToFreeText(text)
                val data = mapOf<String, Any?>("session_id" to sid, "text" to aliasedText, "tenant_id" to 1, "language" to _uiState.value.appLanguage)
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
                voiceManager?.speak(Strings.connectionError, expectReply = true)
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

    private fun sanitizeContactName(name: String): String =
        name.replace(Regex("\\*+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sanitizeAssistantText(text: String): String {
        val avoidAsterisks = settingsManager?.avoidAsterisksInReplies != false
        if (!avoidAsterisks) return text
        val sanitized = text.replace(Regex("\\*+"), " ").replace(Regex(" {2,}"), " ").trim()
        return sanitized.ifBlank { text }
    }

    private fun rememberRecentVoiceContact(name: String?, address: String?, phone: String?) {
        val cleanName = name?.let(::sanitizeContactName)?.takeIf(String::isNotBlank) ?: return
        recentVoiceContact = RecentVoiceContact(
            name = cleanName,
            address = address?.trim()?.takeIf(String::isNotBlank),
            phone = phone?.trim()?.takeIf(String::isNotBlank),
            savedAtMs = SystemClock.elapsedRealtime()
        )
    }

    private fun currentRecentVoiceContact(): RecentVoiceContact? {
        val current = recentVoiceContact ?: return null
        val age = SystemClock.elapsedRealtime() - current.savedAtMs
        if (age > recentVoiceContactTtlMs) {
            recentVoiceContact = null
            return null
        }
        return current
    }

    // =================== VOICE CONTEXT + RESOLVE ===================

    /** Fire-and-forget: tell the server which screen the user is on. */
    fun updateVoiceContext(
        screenCode: String,
        entityType: String? = null,
        entityId: String? = null
    ) {
        _uiState.value = _uiState.value.copy(currentScreenCode = screenCode)
        val uid = _uiState.value.currentUserId?.toInt() ?: 1
        viewModelScope.launch {
            try {
                api.voiceContext(
                    mapOf(
                        "tenant_id" to 1,
                        "user_id" to uid,
                        "screen_code" to screenCode,
                        "entity_type" to entityType,
                        "entity_id" to entityId
                    )
                )
            } catch (_: Exception) {
                // non-critical — ignore silently
            }
        }
    }

    /**
     * Suspend version of voice resolve — called directly from the AI fallback coroutine.
     * Returns true if the command was handled (show disambiguation / execute / confirm),
     * false if we should fall through to the AI server.
     */
    private suspend fun tryVoiceResolveSuspend(text: String, screenCode: String): Boolean {
        val uid  = _uiState.value.currentUserId?.toInt() ?: 1
        val lang = _uiState.value.appLanguage.ifBlank { "cs" }
        return try {
            val resp = api.voiceResolve(
                mapOf(
                    "text"       to text,
                    "tenant_id"  to 1,
                    "user_id"    to uid,
                    "lang"       to lang,
                    "session_id" to screenCode
                )
            )
            if (!resp.isSuccessful) return false
            val body = resp.body() ?: return false

            val resolved              = body["resolved"] as? Boolean ?: false
            val requiresClarification = body["requires_clarification"] as? Boolean ?: false
            if (!resolved && !requiresClarification) return false

            @Suppress("UNCHECKED_CAST")
            val candidates = (body["candidates"] as? List<Map<String, Any?>>)
                ?.map { c ->
                    VoiceDisambiguationCandidate(
                        contactId          = c["contact_id"]?.toString() ?: "",
                        displayName        = c["display_name"]?.toString() ?: "",
                        companyName        = c["company_name"]?.toString(),
                        matchedAlias       = c["matched_alias"]?.toString() ?: "",
                        matchConfidence    = (c["match_confidence"] as? Number)?.toFloat() ?: 0f,
                        disambiguationHint = c["disambiguation_hint"]?.toString() ?: "",
                        aliasType          = c["alias_type"]?.toString() ?: ""
                    )
                } ?: emptyList()

            val result = VoiceResolveResult(
                originalText          = text,
                resolved              = resolved,
                controlCode           = body["control_code"]?.toString(),
                actionCode            = body["action_code"]?.toString(),
                confidence            = (body["confidence"] as? Number)?.toFloat() ?: 0f,
                resolutionMethod      = body["resolution_method"]?.toString(),
                requiresClarification = requiresClarification,
                clarificationQuestion = body["clarification_question"]?.toString(),
                candidates            = candidates,
                args                  = @Suppress("UNCHECKED_CAST")
                                        (body["args"] as? Map<String, Any?>) ?: emptyMap(),
                riskLevel             = body["risk_level"]?.toString() ?: "low",
                error                 = body["error"]?.toString()
            )

            when {
                requiresClarification -> {
                    _uiState.value = _uiState.value.copy(
                        status = Strings.waitingForCommand,
                        pendingVoiceResolve = result
                    )
                    val q = result.clarificationQuestion ?: "Koho myslíš?"
                    voiceManager?.speak(q, expectReply = true)
                    true
                }
                resolved && result.riskLevel == "low" -> {
                    _uiState.value = _uiState.value.copy(
                        status = Strings.waitingForCommand,
                        pendingVoiceResolve = null
                    )
                    executeVoiceResolvedAction(result)
                    true
                }
                else -> {
                    // Medium/high risk — store for confirmation UI
                    _uiState.value = _uiState.value.copy(
                        status = Strings.waitingForCommand,
                        pendingVoiceResolve = result
                    )
                    val msg = "Chceš opravdu: ${result.actionCode ?: result.controlCode}?"
                    voiceManager?.speak(msg, expectReply = true)
                    true
                }
            }
        } catch (e: Exception) {
            Log.w("VoiceResolve", "tryVoiceResolveSuspend error: ${e.message}")
            false
        }
    }

    /** Execute an already-resolved low-risk action from the voice resolver. */
    private fun executeVoiceResolvedAction(result: VoiceResolveResult) {
        val action = result.actionCode ?: result.controlCode ?: return
        when {
            action.startsWith("navigate_to_") || action.startsWith("open_") -> {
                val target = action
                    .removePrefix("navigate_to_")
                    .removePrefix("open_")
                    .replace("_screen", "")
                    .replace("_", "")
                _uiState.value = _uiState.value.copy(pendingAppNavigation = target)
                voiceManager?.speak("Otevírám.", expectReply = false)
            }
            action.startsWith("create_") || action.startsWith("add_") -> {
                val entity = action.removePrefix("create_").removePrefix("add_")
                _uiState.value = _uiState.value.copy(pendingAppAction = "add_$entity")
                voiceManager?.speak("V pořádku.", expectReply = false)
            }
            else -> {
                // Unknown action — announce and store for screen to handle
                _uiState.value = _uiState.value.copy(pendingAppAction = action)
                voiceManager?.speak("Provádím.", expectReply = false)
            }
        }
    }

    /** Called by UI to confirm a medium/high-risk resolved action. */
    fun confirmVoiceResolvedAction() {
        val result = _uiState.value.pendingVoiceResolve ?: return
        _uiState.value = _uiState.value.copy(pendingVoiceResolve = null)
        if (result.resolved) executeVoiceResolvedAction(result)
    }

    /** Called by UI to select a disambiguation candidate and proceed. */
    fun selectVoiceDisambiguationCandidate(candidate: VoiceDisambiguationCandidate) {
        val result = _uiState.value.pendingVoiceResolve ?: return
        _uiState.value = _uiState.value.copy(pendingVoiceResolve = null)
        // Re-resolve is not needed: map the selected contact to the original action
        val action = result.actionCode ?: result.controlCode
        when {
            action?.contains("call") == true -> {
                val phone = candidate.disambiguationHint.takeIf { it.isNotBlank() }
                if (phone != null) startVoiceCallTarget(phone, result.originalText)
                else voiceManager?.speak("Nemám číslo pro ${candidate.displayName}.")
            }
            action?.contains("whatsapp") == true -> {
                val phone = candidate.disambiguationHint.takeIf { it.isNotBlank() }
                if (phone != null) startVoiceWhatsApp(WhatsAppVoiceCommand(target = phone, message = result.originalText), result.originalText)
                else voiceManager?.speak("Nemám WhatsApp číslo pro ${candidate.displayName}.")
            }
            else -> {
                // Generic: navigate to contact's client detail if source_client_id known
                voiceManager?.speak("Vybráno: ${candidate.displayName}.", expectReply = false)
            }
        }
    }

    /** Dismiss the pending voice resolve result without taking action. */
    fun dismissVoiceResolve() {
        _uiState.value = _uiState.value.copy(
            pendingVoiceResolve = null,
            status = Strings.waitingForCommand
        )
    }

    // ================================================================

    fun onVoiceInput(text: String) {
        val lower = text.lowercase().trim()
        val normalized = normalizeVoiceCommand(text)
        Log.d("VoiceInput", "Processing: '$text' (norm: '$normalized')")

        // 1. QUICK EXIT / DIALOG END
        if (isDialogEndPhrase(lower)) {
            if (_uiState.value.isDialogMode) {
                _uiState.value = _uiState.value.copy(
                    dialogSessionHistory = _uiState.value.dialogSessionHistory + ChatMessage("user", text)
                )
            }
            exitDialogMode(text)
            return
        }

        // 2. TOOL TRIGGERS (PLANTS/MUSHROOMS)
        if (Strings.matchesPlantHealthCommand(text)) {
            requestPlantCaptureFromVoice("health")
            return
        }
        if (Strings.matchesMushroomRecognitionCommand(text)) {
            requestPlantCaptureFromVoice("mushroom")
            return
        }
        if (Strings.matchesPlantRecognitionCommand(text)) {
            requestPlantCaptureFromVoice("identify")
            return
        }

        // 3. SPECIAL MODES (CONTACT SORTING, WORK REPORT SESSION)
        if (_uiState.value.contactSortingSession != null) {
            handleContactSortingVoiceInput(text)
            return
        }
        if (_uiState.value.isVoiceSessionActive && _uiState.value.voiceSessionId != null) {
            processVoiceSessionInput(applyVoiceAliasesToFreeText(text))
            return
        }

        // 4. NAVIGATION COMMANDS (LOCALLY HANDLED)
        Strings.matchesNavigationCommand(text)?.let { target ->
            val route = when (target) {
                "home" -> Screen.Home.route
                "crm", "clients", "jobs", "leads", "quotes", "invoices", "reports", "contacts" -> Screen.Crm.route
                "tasks" -> Screen.Tasks.route
                "calendar" -> Screen.Calendar.route
                "tools" -> Screen.Tools.route
                "settings" -> Screen.Settings.route
                else -> null
            }
            if (route != null) {
                _uiState.value = _uiState.value.copy(
                    pendingAppNavigation = target,
                    status = Strings.waitingForCommand,
                    lastAiReply = "Otevírám $target.",
                    history = (_uiState.value.history + ChatMessage("user", text) + ChatMessage("assistant", "Otevírám $target.")).takeLast(30)
                )
                voiceManager?.speak("Otevírám.", expectReply = false)
                return
            }
        }

        // 5. ACTION COMMANDS (LOCALLY HANDLED)
        val action = when {
            normalized.contains("nov") && (normalized.contains("klient") || normalized.contains("client") || normalized.contains("klien")) -> "add_client"
            normalized.contains("nov") && (normalized.contains("ukol") || normalized.contains("task") || normalized.contains("zadan")) -> "add_task"
            normalized.contains("nov") && (normalized.contains("zakazk") || normalized.contains("job") || normalized.contains("zleceni")) -> "add_job"
            normalized.contains("nov") && (normalized.contains("vykaz") || normalized.contains("report") || normalized.contains("raport")) -> "add_work_report"
            normalized.contains("nov") && (normalized.contains("lead") || normalized.contains("poptav") || normalized.contains("potencjal")) -> "add_lead"
            normalized.contains("nov") && (normalized.contains("nabid") || normalized.contains("quote") || normalized.contains("ofert")) -> "add_quote"
            normalized.contains("nov") && (normalized.contains("faktur") || normalized.contains("invoice")) -> "add_invoice"
            normalized.contains("nov") && (normalized.contains("kontakt") || normalized.contains("contact")) -> "add_contact"
            normalized.contains("histor") && (normalized.contains("ukaz") || normalized.contains("zobraz") || normalized.contains("show") || normalized.contains("pokaz")) -> "show_admin_logs"
            normalized.contains("histor") && (normalized.contains("smaz") || normalized.contains("vymaz") || normalized.contains("clear") || normalized.contains("wyczysc")) -> "clear_history"
            normalized.contains("mluv") && (normalized.contains("cesky") || normalized.contains("czech")) || normalized.contains("jezyk czeski") -> "lang_cs"
            normalized.contains("mow") && (normalized.contains("polsku") || normalized.contains("polish")) || normalized.contains("jezyk polski") -> "lang_pl"
            normalized.contains("mluv") && (normalized.contains("anglicky") || normalized.contains("english")) || normalized.contains("jezyk angielski") -> "lang_en"
            normalized.contains("pomoc") || normalized.contains("umis") || normalized.contains("help") || normalized.contains("what can you") -> "help"
            else -> null
        }
        if (action != null) {
            when (action) {
                "help" -> {
                    val helpMsg = "Umím otevírat okna, vytvářet záznamy nebo navigovat. Ptejte se na cokoliv."
                    _uiState.value = _uiState.value.copy(lastAiReply = helpMsg)
                    voiceManager?.speak(helpMsg, expectReply = true)
                }
                "lang_cs" -> { changeLanguage("cs"); voiceManager?.speak("Rozumím, mluvím česky.") }
                "lang_pl" -> { changeLanguage("pl"); voiceManager?.speak("Rozumiem, mówię po polsku.") }
                "lang_en" -> { changeLanguage("en"); voiceManager?.speak("Understood, I'm speaking English.") }
                "clear_history" -> {
                    clearHistory()
                    val msg = "Historie smazána."
                    _uiState.value = _uiState.value.copy(lastAiReply = msg)
                    voiceManager?.speak(msg)
                }
                "show_admin_logs" -> {
                    _uiState.value = _uiState.value.copy(pendingAppNavigation = "settings")
                    voiceManager?.speak("Otevírám historii.")
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        pendingAppAction = action,
                        status = Strings.waitingForCommand,
                        lastAiReply = "Otevírám formulář.",
                        history = (_uiState.value.history + ChatMessage("user", text) + ChatMessage("assistant", "Otevírám formulář.")).takeLast(30)
                    )
                    voiceManager?.speak("V pořádku.", expectReply = false)
                }
            }
            return
        }

        // 6. ONGOING FLOWS (NAVIGATION ADDRESS, TRAINING, SORTING QUESTIONS)
        if (_uiState.value.awaitingNavigationAddress) {
            if (isVoiceCancelCommand(normalized)) {
                _uiState.value = _uiState.value.copy(awaitingNavigationAddress = false, status = Strings.waitingForCommand)
                voiceManager?.speak(Strings.navigationCancelled)
            } else {
                startVoiceNavigationTarget(text, text)
            }
            return
        }
        if (_uiState.value.voiceAliasTraining != null) {
            processVoiceAliasTrainingInput(text, normalized)
            return
        }
        if (_uiState.value.isDialogMode && _uiState.value.contactSortingSession == null && _uiState.value.lastAiReply == Strings.contactSortingAskMethod) {
            // Sorting question handle...
            val n = normalizeVoiceCommand(text)
            if (n.contains("abeced") || n.contains("jmeno")) startContactSortingSession("name")
            else if (n.contains("cislo") || n.contains("predvolb")) startContactSortingSession("phone_prefix", "+44")
            else voiceManager?.speak(Strings.contactSortingAskMethod, expectReply = true)
            return
        }

        // 7. ALIAS LEARNING / FORGETTING
        parseVoiceAliasLearning(text)?.let { learnVoiceAlias(it.alias, it.target, text); return }
        parseVoiceAliasForget(text)?.let { forgetVoiceAlias(it, text); return }

        // 8. BUILT-IN HARD ACTION TRIGGERS (CALL, WHATSAPP, NAV)
        if (handleMergeVoiceCommand(text)) return
        parseVoiceAddressReadTarget(text)?.let { readVoiceAddressTarget(it, text); return }
        parseVoiceNavigationAddress(text)?.let { addr ->
            if (addr.isBlank()) {
                currentRecentVoiceContact()?.address?.let { a -> startVoiceNavigation(a, currentRecentVoiceContact()!!.name, text) }
                    ?: askForNavigationAddress(text)
            } else { startVoiceNavigationTarget(addr, text) }
            return
        }
        parseVoiceCallTarget(text)?.let { startVoiceCallTarget(it, text); return }
        parseVoiceWhatsAppCommand(text)?.let { startVoiceWhatsApp(it, text); return }
        if (matchesStartWorkReportCommand(normalized)) { startWorkReportSession(); return }

        // 9. LOGOUT
        if (Strings.matchesLogoutCommand(lower)) {
            voiceManager?.speak(Strings.loggingOutMessage())
            viewModelScope.launch { kotlinx.coroutines.delay(2000); logout() }
            return
        }

        // 10+11. VOICE RESOLVE + AI SERVER FALLBACK (both run in the same coroutine)
        val currentState = _uiState.value
        val correctedText = applyVoiceAliasesToFreeText(text)
        val newUserMessage = ChatMessage("user", correctedText)
        val updatedHistory = (currentState.history + newUserMessage).takeLast(30)
        
        if (currentState.isDialogMode) {
            _uiState.value = currentState.copy(dialogSessionHistory = currentState.dialogSessionHistory + newUserMessage)
        }
        
        _uiState.value = currentState.copy(isListening = false, status = Strings.processing, history = updatedHistory)
        viewModelScope.launch {
            try {
                // 10. VOICE RESOLVE — try AI control bridge first (screen-aware resolver)
                val screenCode = _uiState.value.currentScreenCode
                if (screenCode != null) {
                    val handled = tryVoiceResolveSuspend(text, screenCode)
                    if (handled) return@launch
                }

                // 11. AI SERVER FALLBACK
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val res = api.processMessage(MessageRequest(
                    text = correctedText, history = updatedHistory,
                    context_entity_id = currentState.contextEntityId, context_type = currentState.contextType,
                    internal_language = currentState.appLanguage,
                    external_language = currentState.tenantConfig?.get("default_customer_lang")?.toString()?.takeIf { it.isNotBlank() } ?: "en",
                    calendar_context = calendarManager?.getCalendarContext(),
                    current_datetime = sdf.format(Date())
                ))
                if (res.isSuccessful) {
                    res.body()?.let { response ->
                        val assistantReply = sanitizeAssistantText(response.reply_cs)
                        val newAssistantMessage = ChatMessage("assistant", assistantReply)
                        _uiState.value = _uiState.value.copy(
                            lastAiReply = assistantReply,
                            status = if (response.is_question) "${Strings.listening}..." else Strings.waitingForCommand,
                            history = _uiState.value.history + newAssistantMessage,
                            dialogSessionHistory = if (_uiState.value.isDialogMode) (_uiState.value.dialogSessionHistory + newAssistantMessage).takeLast(100) else _uiState.value.dialogSessionHistory
                        )
                        handleAction(response)
                        if (response.action_type !in setOf("LIST_CALENDAR_EVENTS", "START_WORK_REPORT", "CALL_CONTACT", "SEND_WHATSAPP", "START_NAVIGATION", "OPEN_NAVIGATION", "NAVIGATE")) {
                            voiceManager?.speak(assistantReply, expectReply = response.is_question || _uiState.value.isDialogMode)
                        }
                        refreshCrmDataKeepTasks()
                    }
                } else {
                    _uiState.value = _uiState.value.copy(status = Strings.serverError(res.code()))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = Strings.connectionError)
                voiceManager?.speak(Strings.cantReachServer)
            }
        }
    }

    fun onAppNavigationHandled() {
        _uiState.value = _uiState.value.copy(pendingAppNavigation = null)
    }

    fun onAppActionHandled() {
        _uiState.value = _uiState.value.copy(pendingAppAction = null)
    }

    private fun askForNavigationAddress(originalText: String) {
        val message = Strings.sayNavigationAddress
        _uiState.value = _uiState.value.copy(
            awaitingNavigationAddress = true,
            isListening = false,
            status = Strings.listening,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = true)
    }

    fun requestNavigationAddress() {
        askForNavigationAddress(Strings.startNavigation)
    }

    fun startVoiceAliasTraining(targetName: String, targetType: String = "contact") {
        val cleanTarget = targetName.trim()
        if (cleanTarget.isBlank()) return
        val training = VoiceAliasTrainingState(
            target = cleanTarget,
            targetType = targetType,
            samples = emptyList()
        )
        val message = Strings.voiceAliasTrainingPrompt(cleanTarget, 1)
        _uiState.value = _uiState.value.copy(
            voiceAliasTraining = training,
            awaitingNavigationAddress = false,
            isListening = false,
            status = Strings.listening,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = true)
    }

    private fun processVoiceAliasTrainingInput(text: String, normalized: String) {
        val training = _uiState.value.voiceAliasTraining ?: return
        if (isVoiceCancelCommand(normalized)) {
            val message = Strings.voiceAliasTrainingCancelled
            _uiState.value = _uiState.value.copy(
                voiceAliasTraining = null,
                status = Strings.waitingForCommand,
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", text) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = false)
            return
        }
        val sample = stripContactCommandPrefixes(text).trim()
        if (sample.length < 2) {
            val message = Strings.voiceAliasTrainingNeedSpeech
            _uiState.value = _uiState.value.copy(
                status = Strings.listening,
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", text) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = true)
            return
        }
        val samples = (training.samples + sample).takeLast(3)
        if (samples.size < 3) {
            val updatedTraining = training.copy(samples = samples)
            val message = Strings.voiceAliasTrainingPrompt(training.target, samples.size + 1)
            _uiState.value = _uiState.value.copy(
                voiceAliasTraining = updatedTraining,
                status = Strings.listening,
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", text) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = true)
            return
        }
        val alias = chooseVoiceAliasSample(samples)
        settingsManager?.upsertVoiceAlias(alias, training.target, training.targetType)
        val message = Strings.voiceAliasTrainingSaved(alias, training.target)
        _uiState.value = _uiState.value.copy(
            voiceAliasTraining = null,
            status = Strings.waitingForCommand,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", text) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = false)
    }

    private fun chooseVoiceAliasSample(samples: List<String>): String =
        samples
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .firstOrNull()
            ?.key
            ?: samples.last()

    private data class NavigationAddressCandidate(
        val name: String,
        val address: String?,
        val score: Int
    )

    private data class PhoneContactCandidate(
        val name: String,
        val phone: String?,
        val score: Int,
        val matchedAlias: String? = null
    )

    private data class WhatsAppVoiceCommand(
        val target: String,
        val message: String
    )

    private data class VoiceAliasCommand(
        val alias: String,
        val target: String
    )

    private data class VoiceQueryVariant(
        val value: String,
        val matchedAlias: String? = null
    )

    private fun startVoiceNavigationTarget(target: String, originalText: String = target) {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) {
            askForNavigationAddress(originalText)
            return
        }
        val candidate = resolveNavigationTarget(cleanTarget)
        if (candidate != null) {
            rememberRecentVoiceContact(candidate.name, candidate.address, null)
            val address = candidate.address
            if (address.isNullOrBlank()) {
                val message = Strings.noAddressAvailableFor(candidate.name)
                _uiState.value = _uiState.value.copy(
                    awaitingNavigationAddress = false,
                    status = Strings.waitingForCommand,
                    lastAiReply = message,
                    history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
                )
                voiceManager?.speak(message, expectReply = false)
                return
            }
            startVoiceNavigation(address, candidate.name, originalText)
            return
        }
        startVoiceNavigation(cleanTarget, null, originalText)
    }

    private fun readVoiceAddressTarget(target: String, originalText: String) {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) {
            val message = Strings.sayNavigationAddress
            _uiState.value = _uiState.value.copy(
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = true)
            return
        }
        val candidate = resolveNavigationTarget(cleanTarget)
        if (candidate != null) {
            rememberRecentVoiceContact(candidate.name, candidate.address, null)
        }
        val message = when {
            candidate == null -> Strings.noContactFound(cleanTarget)
            candidate.address.isNullOrBlank() -> Strings.noAddressAvailableFor(candidate.name)
            else -> "${candidate.name}. ${Strings.addressForSpeech(candidate.address)}"
        }
        _uiState.value = _uiState.value.copy(
            awaitingNavigationAddress = false,
            status = Strings.waitingForCommand,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = false)
    }

    private fun startVoiceNavigation(address: String, label: String? = null, originalText: String = address) {
        android.util.Log.d("VoiceNav", "startVoiceNavigation address=[$address] label=[$label]")
        val cleanAddress = address.trim()
        if (cleanAddress.isBlank()) {
            askForNavigationAddress(Strings.startNavigation)
            return
        }
        val message = if (label.isNullOrBlank()) {
            Strings.startingNavigation(cleanAddress)
        } else {
            Strings.startingNavigationFor(label, cleanAddress)
        }
        _uiState.value = _uiState.value.copy(
            awaitingNavigationAddress = false,
            pendingNavigationAddress = cleanAddress,
            isListening = false,
            status = Strings.startNavigation,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = false)
    }

    private fun learnVoiceAlias(alias: String, target: String, originalText: String) {
        val cleanAlias = stripContactCommandPrefixes(alias)
        val cleanTarget = target.trim()
        if (cleanAlias.length < 2 || cleanTarget.length < 2) return
        val resolvedTarget = resolvePhoneTarget(cleanTarget)?.name
            ?: resolveNavigationTarget(cleanTarget)?.name
            ?: cleanTarget
        val aliasNorm = normalizeVoiceCommand(cleanAlias)
        val targetNorm = normalizeVoiceCommand(resolvedTarget)
        if (isReservedWakeAlias(aliasNorm) || looksLikeCommandAlias(aliasNorm)) return
        if (aliasNorm in knownVoiceNameSet() && aliasNorm != targetNorm) return
        settingsManager?.upsertVoiceAlias(cleanAlias, resolvedTarget)
        invalidateAliasCache()
        val message = Strings.voiceAliasLearned(cleanAlias, resolvedTarget)
        _uiState.value = _uiState.value.copy(
            status = Strings.waitingForCommand,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = false)
    }

    private fun forgetVoiceAlias(alias: String, originalText: String) {
        val cleanAlias = stripContactCommandPrefixes(alias)
        val removed = settingsManager?.removeVoiceAlias(cleanAlias) == true
        if (removed) invalidateAliasCache()
        val message = if (removed) Strings.voiceAliasForgotten(cleanAlias) else Strings.voiceAliasNotFound(cleanAlias)
        _uiState.value = _uiState.value.copy(
            status = Strings.waitingForCommand,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = false)
    }

    // Cache for knownVoiceNameSet — invalidated when clients refresh
    private var _cachedKnownNames: Set<String>? = null
    private var _cachedKnownNamesClientCount: Int = -1

    private fun knownVoiceNameSet(): Set<String> {
        val clientCount = _uiState.value.clients.size
        if (_cachedKnownNames != null && _cachedKnownNamesClientCount == clientCount) {
            return _cachedKnownNames!!
        }
        val names = linkedSetOf<String>()
        _uiState.value.clients.forEach { client ->
            names += client.display_name
            client.company_name?.let { names += it }
        }
        _uiState.value.sharedContacts.forEach { contact ->
            names += contact.display_name
            contact.company_name?.let { names += it }
        }
        contactManager?.getAllContacts().orEmpty().forEach { contact ->
            contact["name"]?.let { names += it }
        }
        val expanded = linkedSetOf<String>()
        names.map(::normalizeVoiceCommand)
            .filter { it.length >= 2 }
            .forEach { normalized ->
                expanded += normalized
                val tokens = normalized.split(" ").filter { it.length >= 3 }
                tokens.forEach { token -> expanded += token }
                tokens.firstOrNull()?.let { expanded += it }
                tokens.lastOrNull()?.let { expanded += it }
            }
        _cachedKnownNames = expanded
        _cachedKnownNamesClientCount = clientCount
        return expanded
    }

    private fun looksLikeCommandAlias(aliasNorm: String): Boolean {
        if (aliasNorm.isBlank()) return false
        val commandPrefixes = listOf(
            "zavolej ", "volej ", "volat ", "vytoc ", "vytocit ", "call ", "dial ",
            "posli ", "napis ", "whatsapp ", "send ",
            "spust ", "spustit ", "naviguj ", "navigace ", "navigate ",
            "zapamatuj ", "remember ", "alias "
        )
        if (commandPrefixes.any { aliasNorm.startsWith(it) }) return true
        val commandTokens = setOf(
            "zavolej", "volej", "volat", "vytoc", "vytocit", "call", "dial",
            "posli", "napis", "zpravu", "message", "whatsapp", "send",
            "spust", "spustit", "naviguj", "navigace", "navigate",
            "zapamatuj", "remember", "alias"
        )
        val tokens = aliasNorm.split(" ").filter { it.isNotBlank() }
        return tokens.size >= 3 && tokens.any { it in commandTokens }
    }

    // Cache for effectiveVoiceAliases — invalidate when aliases change
    private var _cachedAliases: List<VoiceAlias>? = null
    private var _cachedAliasesUserId: Long = -1L
    private var _cachedAliasesCount: Int = -1

    fun invalidateAliasCache() {
        _cachedAliases = null
    }

    // ===== CONTACT SORTING SESSION =====

    private val SECTION_VOICE_MAP = mapOf(
        // Czech
        "klienti" to "client", "klient" to "client",
        "soukrome" to "private", "soukromi" to "private", "soukroma" to "private",
        "subdodavatele" to "subcontractor", "subdodavatel" to "subcontractor",
        "zamestnanci" to "employee", "zamestnanec" to "employee",
        "pujcovny" to "equipment_vehicle_rental", "pujcovna" to "equipment_vehicle_rental",
        "dodavatele materialu" to "material_supplier", "dodavatel materialu" to "material_supplier",
        "dodavatele" to "material_supplier", "material" to "material_supplier",
        "ostatni" to "other", "jine" to "other",
        // English
        "clients" to "client", "client" to "client",
        "private" to "private", "personal" to "private",
        "subcontractors" to "subcontractor", "subcontractor" to "subcontractor",
        "employees" to "employee", "employee" to "employee",
        "rentals" to "equipment_vehicle_rental", "rental" to "equipment_vehicle_rental",
        "suppliers" to "material_supplier", "supplier" to "material_supplier",
        "other" to "other", "others" to "other"
    )

    private val SECTION_DISPLAY = mapOf(
        "client" to "Klienti",
        "private" to "Soukromé",
        "subcontractor" to "Subdodavatelé",
        "employee" to "Zaměstnanci",
        "equipment_vehicle_rental" to "Půjčovny",
        "material_supplier" to "Dodavatelé materiálu",
        "other" to "Ostatní"
    )

    fun startContactSortingSession(sortBy: String = "name", phonePrefix: String = "+44") {
        if (sortBy == "ask") {
            _uiState.value = _uiState.value.copy(
                isDialogMode = true,
                status = "Třídění kontaktů...",
                lastAiReply = Strings.contactSortingAskMethod
            )
            voiceManager?.speak(Strings.contactSortingAskMethod, expectReply = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = Strings.processing)
            try {
                val res = api.getContactsForSorting(sortBy = sortBy, phonePrefix = phonePrefix)
                if (!res.isSuccessful) {
                    voiceManager?.speak(Strings.errorLoadingContacts, expectReply = false)
                    return@launch
                }
                val body = res.body() ?: return@launch

                @Suppress("UNCHECKED_CAST")
                val serverContacts = try {
                    (body["contacts"] as? List<*>)?.mapNotNull { item ->
                        (item as? Map<*, *>)?.let { m ->
                            PhoneContactEntry(
                                displayName = m["display_name"]?.toString() ?: return@mapNotNull null,
                                phone = m["phone_primary"]?.toString() ?: "",
                                existingSectionCode = m["contact_role"]?.toString(),
                                existingId = (m["id"] as? Number)?.toLong()
                            )
                        }
                    } ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.w("ContactSort", "Parse error: ${e.message}")
                    emptyList()
                }

                if (serverContacts.isEmpty()) {
                    val msg = "Žádné kontakty k třídění. Všechny jsou již zařazeny."
                    voiceManager?.speak(msg, expectReply = false)
                    _uiState.value = _uiState.value.copy(status = Strings.waitingForCommand, lastAiReply = msg)
                    return@launch
                }

                val sorted = when (sortBy) {
                    "phone_prefix" -> serverContacts.sortedBy { it.phone }
                    else -> serverContacts.sortedBy { it.displayName.lowercase() }
                }

                val session = ContactSortingSession(contacts = sorted, sortBy = sortBy, phonePrefix = phonePrefix)
                _uiState.value = _uiState.value.copy(contactSortingSession = session, isDialogMode = true)
                announceCurrentSortingContact(session)
            } catch (e: Exception) {
                voiceManager?.speak(Strings.errorLoadingContacts, expectReply = false)
                android.util.Log.e("ContactSort", "Error: ${e.message}")
            }
        }
    }


    private fun findContactDuplicates(contacts: List<PhoneContactEntry>): List<PhoneContactEntry> {
        // Just return as-is — duplicates detected during session when consecutive names are very similar
        return contacts
    }

    private fun announceCurrentSortingContact(session: ContactSortingSession) {
        val contact = session.current ?: run {
            finishContactSortingSession()
            return
        }
        val existingInfo = if (contact.existingSectionCode != null) {
            val display = SECTION_DISPLAY[contact.existingSectionCode] ?: contact.existingSectionCode
            " — aktuálně: $display"
        } else ""
        val msg = "${session.progressText}: ${contact.displayName}, ${contact.phone}$existingInfo. ${Strings.contactSortingPrompt}"
        _uiState.value = _uiState.value.copy(lastAiReply = msg, status = "Třídění ${session.progressText}")
        voiceManager?.speak(msg, expectReply = true)
    }

    fun handleContactSortingVoiceInput(text: String): Boolean {
        val session = _uiState.value.contactSortingSession ?: return false
        val norm = normalizeVoiceCommand(text)

        // End session
        if (isDialogEndPhrase(norm) || norm.contains("konec") || norm.contains("hotovo")) {
            finishContactSortingSession()
            return true
        }

        // Skip
        if (norm.startsWith("preskoc") || norm.startsWith("dalsi") || norm == "skip" || norm == "next") {
            val updated = session.copy(currentIndex = session.currentIndex + 1, skippedCount = session.skippedCount + 1)
            _uiState.value = _uiState.value.copy(contactSortingSession = updated)
            announceCurrentSortingContact(updated)
            return true
        }

        // Delete contact
        if (norm.startsWith("smazat") || norm.startsWith("odstranit") || norm == "delete" || norm == "remove") {
            session.current?.existingId?.let { id ->
                viewModelScope.launch {
                    try { api.deleteSharedContact(id) } catch (_: Exception) {}
                }
            }
            val msg = "${session.current?.displayName} odstraněn."
            val updated = session.copy(currentIndex = session.currentIndex + 1)
            _uiState.value = _uiState.value.copy(contactSortingSession = updated)
            voiceManager?.speak(msg, expectReply = true)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                announceCurrentSortingContact(_uiState.value.contactSortingSession ?: return@postDelayed)
            }, 1500)
            return true
        }

        // Merge with next contact
        if (norm.startsWith("sloucit") || norm.startsWith("same") || norm == "merge") {
            val next = session.contacts.getOrNull(session.currentIndex + 1)
            if (next != null && session.current != null) {
                val mergeMsg = "Sloučit ${session.current?.displayName} s ${next.displayName}? Řekněte ano nebo ne."
                val updated = session.copy(pendingMerge = Pair(session.current!!, next))
                _uiState.value = _uiState.value.copy(contactSortingSession = updated)
                voiceManager?.speak(mergeMsg, expectReply = true)
                return true
            }
        }

        // Confirm merge
        if (session.pendingMerge != null) {
            if (norm == "ano" || norm == "yes") {
                val (primary, secondary) = session.pendingMerge
                viewModelScope.launch {
                    try {
                        if (primary.existingId != null && secondary.existingId != null) {
                            api.mergeSharedContacts(mapOf(
                                "primary_id" to primary.existingId,
                                "secondary_id" to secondary.existingId
                            ))
                        }
                    } catch (_: Exception) {}
                }
                val updated = session.copy(
                    pendingMerge = null,
                    currentIndex = session.currentIndex + 2,
                    assignedCount = session.assignedCount + 1
                )
                _uiState.value = _uiState.value.copy(contactSortingSession = updated)
                voiceManager?.speak("Sloučeno.", expectReply = true)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    announceCurrentSortingContact(_uiState.value.contactSortingSession ?: return@postDelayed)
                }, 1200)
                return true
            } else if (norm == "ne" || norm == "no") {
                val updated = session.copy(pendingMerge = null)
                _uiState.value = _uiState.value.copy(contactSortingSession = updated)
                announceCurrentSortingContact(updated)
                return true
            }
        }

        // Section assignment
        val sectionCode = SECTION_VOICE_MAP.entries
            .firstOrNull { (key, _) -> norm == key || norm.startsWith("$key ") }
            ?.value

        if (sectionCode != null && session.current != null) {
            val contact = session.current!!
            viewModelScope.launch {
                try {
                    api.assignContactSection(mapOf(
                        "display_name" to contact.displayName,
                        "phone" to contact.phone,
                        "section_code" to sectionCode,
                        "contact_id" to contact.existingId
                    ).filterValues { it != null })
                } catch (e: Exception) {
                    android.util.Log.w("ContactSort", "Assign failed: ${e.message}")
                }
            }
            val sectionName = SECTION_DISPLAY[sectionCode] ?: sectionCode
            val updated = session.copy(
                currentIndex = session.currentIndex + 1,
                assignedCount = session.assignedCount + 1
            )
            _uiState.value = _uiState.value.copy(contactSortingSession = updated)
            voiceManager?.speak("$sectionName.", expectReply = true)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                announceCurrentSortingContact(_uiState.value.contactSortingSession ?: return@postDelayed)
            }, 800)
            return true
        }

        // Unknown — repeat current contact
        announceCurrentSortingContact(session)
        return true
    }

    // ===== DUPLICATE DETECTION & MERGE =====

    fun checkContactDuplicates() {
        viewModelScope.launch {
            try {
                val res = api.getContactDuplicates()
                if (res.isSuccessful) {
                    val body = res.body() ?: return@launch
                    @Suppress("UNCHECKED_CAST")
                    val dupes = (body["duplicates"] as? List<Map<String, Any?>>)?.map { d ->
                        ContactDuplicate(
                            id1 = (d["id1"] as? Number)?.toLong() ?: 0,
                            name1 = d["name1"]?.toString() ?: "",
                            phone1 = d["phone1"]?.toString(),
                            section1 = d["section1"]?.toString(),
                            id2 = (d["id2"] as? Number)?.toLong() ?: 0,
                            name2 = d["name2"]?.toString() ?: "",
                            phone2 = d["phone2"]?.toString(),
                            section2 = d["section2"]?.toString(),
                            reason = d["reason"]?.toString() ?: "same_phone"
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(contactDuplicates = dupes)
                }
            } catch (e: Exception) {
                android.util.Log.w("ContactMerge", "Duplicate check failed: ${e.message}")
            }
        }
    }

    fun mergeContactsById(primaryId: Long, secondaryId: Long) {
        viewModelScope.launch {
            try {
                val res = api.mergeSharedContacts(mapOf("primary_id" to primaryId, "secondary_id" to secondaryId))
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        contactDuplicates = _uiState.value.contactDuplicates.filter {
                            it.id1 != primaryId && it.id2 != secondaryId && it.id1 != secondaryId && it.id2 != primaryId
                        },
                        pendingMergeDialog = null
                    )
                    refreshSharedContacts()
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactMerge", "Merge failed: ${e.message}")
            }
        }
    }

    fun showMergeDialog(dupe: ContactDuplicate) {
        _uiState.value = _uiState.value.copy(pendingMergeDialog = dupe)
    }

    fun dismissMergeDialog() {
        _uiState.value = _uiState.value.copy(pendingMergeDialog = null)
    }

    private fun refreshSharedContacts() {
        viewModelScope.launch {
            try {
                val res = api.getSharedContacts()
                if (res.isSuccessful) _uiState.value = _uiState.value.copy(sharedContacts = res.body() ?: emptyList())
            } catch (_: Exception) {}
        }
    }

    fun handleMergeVoiceCommand(text: String): Boolean {
        val norm = normalizeVoiceCommand(text)
        if (!norm.contains("sloucit") && !norm.contains("duplikat") && !norm.contains("merge contact")) return false
        val dupes = _uiState.value.contactDuplicates
        if (dupes.isEmpty()) {
            checkContactDuplicates()
            voiceManager?.speak("Kontroluji duplicitní kontakty...", expectReply = false)
            return true
        }
        val first = dupes.first()
        _uiState.value = _uiState.value.copy(pendingMergeDialog = first)
        voiceManager?.speak("Našel jsem ${dupes.size} duplicit. Otevírám dialog.", expectReply = false)
        return true
    }

        private fun finishContactSortingSession() {
        val session = _uiState.value.contactSortingSession
        val msg = if (session != null) {
            "Třídění dokončeno. Zařazeno: ${session.assignedCount}, přeskočeno: ${session.skippedCount}."
        } else "Třídění dokončeno."
        _uiState.value = _uiState.value.copy(
            contactSortingSession = null,
            isDialogMode = false,
            status = Strings.waitingForCommand,
            lastAiReply = msg
        )
        voiceManager?.speak(msg, expectReply = false)
        voiceManager?.startHotwordLoop()
    }

    private fun effectiveVoiceAliases(): List<VoiceAlias> {
        val sm = settingsManager ?: return emptyList()
        val userId = sm.currentBackendUserId
        val rawAliases = sm.getVoiceAliases()
        // Return cache if user and count unchanged
        if (_cachedAliases != null && _cachedAliasesUserId == userId && _cachedAliasesCount == rawAliases.size) {
            return _cachedAliases!!
        }
        val knownNames = knownVoiceNameSet()
        val filtered = rawAliases
            .filter { alias ->
                val aliasNorm = normalizeVoiceCommand(alias.alias)
                val targetNorm = normalizeVoiceCommand(alias.target)
                if (aliasNorm.length < 2 || targetNorm.length < 2) return@filter false
                if (isReservedWakeAlias(aliasNorm)) return@filter false
                if (looksLikeCommandAlias(aliasNorm)) return@filter false
                if (aliasNorm in knownNames && aliasNorm != targetNorm) return@filter false
                true
            }
            .distinctBy { normalizeVoiceCommand(it.alias) }
        _cachedAliases = filtered
        _cachedAliasesUserId = userId
        _cachedAliasesCount = rawAliases.size
        return filtered
    }

    private fun sanitizeVoiceAliasesStore() {
        val sm = settingsManager ?: return
        val current = sm.getVoiceAliases()
        if (current.isEmpty()) return
        val sanitized = effectiveVoiceAliases()
        if (sanitized.size != current.size) {
            Log.d("SecretaryViewModel", "Sanitized voice aliases: ${current.size} -> ${sanitized.size}")
            sm.saveVoiceAliases(sanitized)
        }
    }

    private fun isReservedWakeAlias(aliasNorm: String): Boolean {
        val activation = settingsManager?.activationWord?.let(::normalizeVoiceCommand).orEmpty()
        if (activation.isBlank()) return false
        val reservedExact = setOf(
            activation,
            "hej $activation",
            "hey $activation"
        )
        if (aliasNorm in reservedExact) return true
        return aliasNorm.startsWith("$activation ") ||
            aliasNorm.endsWith(" $activation") ||
            aliasNorm.contains(" $activation ")
    }

    private fun resolveNavigationTarget(query: String): NavigationAddressCandidate? {
        val normalizedQuery = applyVoiceAliasToQuery(normalizeVoiceCommand(query))
            .removePrefix("klient ")
            .removePrefix("klienta ")
            .removePrefix("kontakt ")
            .removePrefix("kontaktu ")
            .removePrefix("client ")
            .removePrefix("contact ")
            .removePrefix("do ")
            .removePrefix("na ")
            .trim()
        if (normalizedQuery.length < 2) return null
        fun score(labels: List<String>): Int? = scoreVoiceLabels(labels, voiceQueryVariants(normalizedQuery))?.first
        val clientMatches = _uiState.value.clients.mapNotNull { client ->
            val labels = listOfNotNull(
                client.display_name,
                client.company_name,
                client.client_code,
                client.phone_primary,
                client.email_primary
            )
            score(labels)?.let { NavigationAddressCandidate(client.display_name, client.navigationAddress(), it) }
        }
        val contactMatches = _uiState.value.sharedContacts.mapNotNull { contact ->
            val labels = listOfNotNull(
                contact.display_name,
                contact.company_name,
                contact.phone_primary,
                contact.email_primary
            )
            score(labels)?.let { NavigationAddressCandidate(contact.display_name, contact.navigationAddress(), it + 1) }
        }
        val phoneMatches = contactManager
            ?.getAllContacts()
            .orEmpty()
            .mapIndexedNotNull { index, contact ->
                val name = contact["name"]?.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
                val address = contact["address"]?.takeIf(String::isNotBlank)
                score(listOfNotNull(name, contact["phone"], contact["email"]))?.let {
                    NavigationAddressCandidate(name, address, 10 + index + it)
                }
            }
        return (clientMatches + contactMatches + phoneMatches).minWithOrNull(
            compareBy<NavigationAddressCandidate> { if (it.address.isNullOrBlank()) it.score + 50 else it.score }
                .thenBy { it.score }
                .thenBy { it.name.length }
        )
    }

    private fun startVoiceCallTarget(target: String, originalText: String = target) {
        val cleanTarget = applyVoiceAliasToQuery(normalizeVoiceCommand(target.trim()))
        if (cleanTarget.isBlank()) {
            val message = Strings.sayCallContact
            _uiState.value = _uiState.value.copy(
                status = Strings.waitingForCommand,
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = true)
            return
        }
        val candidate = resolvePhoneTarget(cleanTarget)
        val message = when {
            candidate == null -> Strings.noContactFound(cleanTarget)
            candidate.phone.isNullOrBlank() -> Strings.noPhoneAvailableFor(candidate.name)
            else -> Strings.dialingContact(candidate.name)
        }
        if (candidate != null) {
            rememberRecentVoiceContact(candidate.name, null, candidate.phone)
        }
        _uiState.value = _uiState.value.copy(
            pendingCall = candidate?.phone?.takeIf(String::isNotBlank),
            isListening = false,
            status = if (candidate?.phone.isNullOrBlank()) Strings.waitingForCommand else Strings.dialing,
            lastAiReply = message,
            history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
        )
        voiceManager?.speak(message, expectReply = false)
    }

    private fun startVoiceWhatsApp(command: WhatsAppVoiceCommand, originalText: String) {
        if (command.target == "__INBOX__") {
            val message = "Otevírám WhatsApp."
            _uiState.value = _uiState.value.copy(
                pendingWhatsAppPhone = "__INBOX__",
                pendingWhatsAppMessage = "",
                status = Strings.waitingForCommand,
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = false)
            return
        }
        val cleanTarget = applyVoiceAliasToQuery(normalizeVoiceCommand(command.target.trim()))
        val cleanMessage = command.message.trim()
        if (cleanTarget.isBlank()) {
            val message = Strings.sayWhatsAppContact
            _uiState.value = _uiState.value.copy(
                status = Strings.waitingForCommand,
                lastAiReply = message,
                history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", message)).takeLast(30)
            )
            voiceManager?.speak(message, expectReply = true)
            return
        }
        val candidate = resolvePhoneTarget(cleanTarget)
        val reply = when {
            candidate == null -> Strings.noContactFound(cleanTarget)
            candidate.phone.isNullOrBlank() -> Strings.noPhoneAvailableFor(candidate.name)
            cleanMessage.isBlank() -> Strings.sayWhatsAppMessage(candidate.name)
            else -> Strings.openingWhatsAppFor(candidate.name)
        }
        if (candidate != null) {
            rememberRecentVoiceContact(candidate.name, null, candidate.phone)
        }
        val hasContact = !candidate?.phone.isNullOrBlank()
        val hasMessage = cleanMessage.isNotBlank()
        if (hasContact && hasMessage) {
            // Translate message to customer language before sending
            val targetLang = _uiState.value.tenantConfig?.get("default_customer_lang")?.toString() ?: "en"
            val internalLang = _uiState.value.appLanguage
            _uiState.value = _uiState.value.copy(
                isListening = false,
                status = Strings.processing,
                lastAiReply = reply,
                history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", reply)).takeLast(30)
            )
            voiceManager?.speak(reply, expectReply = false)
            viewModelScope.launch {
                val translatedMessage = if (internalLang != targetLang && targetLang.isNotBlank()) {
                    try {
                        val res = api.translateMessage(mapOf("text" to cleanMessage, "target_language" to targetLang))
                        if (res.isSuccessful) {
                            res.body()?.get("translated")?.toString()?.takeIf { it.isNotBlank() } ?: cleanMessage
                        } else cleanMessage
                    } catch (e: Exception) {
                        android.util.Log.w("WhatsApp", "Translation failed, using original: ${e.message}")
                        cleanMessage
                    }
                } else cleanMessage
                // Try to send via server (Meta WhatsApp Business API)
                val phone = candidate?.phone ?: return@launch
                var sentViaServer = false
                try {
                    val sendRes = api.sendWhatsAppMessage(mapOf(
                        "to" to phone,
                        "message" to translatedMessage,
                        "target_language" to targetLang
                    ))
                    if (sendRes.isSuccessful) {
                        sentViaServer = true
                        val confirmMsg = Strings.whatsAppSentViaServer
                        _uiState.value = _uiState.value.copy(
                            status = Strings.waitingForCommand,
                            lastAiReply = confirmMsg,
                            history = (_uiState.value.history + ChatMessage("assistant", confirmMsg)).takeLast(30)
                        )
                        voiceManager?.speak(confirmMsg, expectReply = _uiState.value.isDialogMode)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WhatsApp", "Server send failed, falling back to intent: ${e.message}")
                }
                if (!sentViaServer) {
                    // Fallback: open WhatsApp with prefilled message
                    _uiState.value = _uiState.value.copy(
                        pendingWhatsAppPhone = phone,
                        pendingWhatsAppMessage = translatedMessage,
                        status = Strings.openingWhatsApp
                    )
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(
                pendingWhatsAppPhone = null,
                pendingWhatsAppMessage = null,
                isListening = false,
                status = Strings.waitingForCommand,
                lastAiReply = reply,
                history = (_uiState.value.history + ChatMessage("user", originalText) + ChatMessage("assistant", reply)).takeLast(30)
            )
            voiceManager?.speak(reply, expectReply = false)
        }
    }

    private fun resolvePhoneTarget(query: String): PhoneContactCandidate? {
        val normalizedQuery = stripContactCommandPrefixes(applyVoiceAliasToQuery(normalizeVoiceCommand(query)))
        if (normalizedQuery.length < 2) return null
        val queryVariants = voiceQueryVariants(normalizedQuery)
        fun score(labels: List<String>): Pair<Int, String?>? = scoreVoiceLabels(labels, queryVariants)
        val clientMatches = _uiState.value.clients.mapNotNull { client ->
            val labels = listOfNotNull(
                client.display_name,
                client.company_name,
                client.client_code,
                client.phone_primary,
                client.phone_secondary,
                client.email_primary
            )
            score(labels)?.let { (score, alias) ->
                PhoneContactCandidate(client.display_name, client.phone_primary?.takeIf(String::isNotBlank) ?: client.phone_secondary, score, alias)
            }
        }
        val sharedMatches = _uiState.value.sharedContacts.mapNotNull { contact ->
            val labels = listOfNotNull(
                contact.display_name,
                contact.company_name,
                contact.phone_primary,
                contact.email_primary
            )
            score(labels)?.let { (score, alias) -> PhoneContactCandidate(contact.display_name, contact.phone_primary, score + 1, alias) }
        }
        val deviceMatches = contactManager
            ?.getAllContacts()
            .orEmpty()
            .mapIndexedNotNull { index, contact ->
                val name = contact["name"]?.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
                score(listOfNotNull(name, contact["phone"], contact["email"]))?.let { (score, alias) ->
                    PhoneContactCandidate(name, contact["phone"]?.takeIf(String::isNotBlank), score + 10 + index, alias)
                }
            }
        val best = (clientMatches + sharedMatches + deviceMatches).minWithOrNull(
            compareBy<PhoneContactCandidate> { if (it.phone.isNullOrBlank()) it.score + 100 else it.score }.thenBy { it.name.length }
        )
        return best
    }

    private fun voiceQueryVariants(normalizedQuery: String): List<VoiceQueryVariant> {
        val base = normalizedQuery.trim()
        val variants = mutableListOf(VoiceQueryVariant(base))
        effectiveVoiceAliases().forEach { alias ->
            val aliasNorm = normalizeVoiceCommand(alias.alias)
            val targetNorm = normalizeVoiceCommand(alias.target)
            if (aliasNorm.isBlank() || targetNorm.isBlank()) return@forEach
            if (isReservedWakeAlias(aliasNorm)) return@forEach
            if (base == aliasNorm) {
                variants += VoiceQueryVariant(targetNorm, alias.alias)
            }
        }
        return variants.distinctBy { it.value }
    }

    private fun scoreVoiceLabels(labels: List<String>, queries: List<VoiceQueryVariant>): Pair<Int, String?>? {
        val normalizedLabels = labels.map { normalizeVoiceCommand(it) }.filter { it.isNotBlank() }
        var best: Pair<Int, String?>? = null
        for (query in queries) {
            val q = query.value.trim()
            if (q.length < 2) continue
            val words = q.split(" ").filter { it.isNotBlank() }
            val compactQ = q.replace(" ", "")
            val score = when {
                normalizedLabels.any { it == q || it.replace(" ", "") == compactQ } -> 0
                normalizedLabels.any { it.startsWith(q) || it.replace(" ", "").startsWith(compactQ) } -> 1
                normalizedLabels.any { label -> words.all { it.length > 1 && label.contains(it) } } -> 2
                normalizedLabels.any { it.contains(q) || it.replace(" ", "").contains(compactQ) } -> 3
                normalizedLabels.any { label -> fuzzyVoiceMatch(q, label) } -> 5
                else -> null
            } ?: continue
            val adjustedScore = if (query.matchedAlias != null) score - 1 else score
            if (best == null || adjustedScore < best!!.first) best = adjustedScore to query.matchedAlias
        }
        return best
    }

    private fun fuzzyVoiceMatch(query: String, label: String): Boolean {
        val compactQuery = query.replace(" ", "")
        val compactLabel = label.replace(" ", "")
        if (compactQuery.length < 4 || compactLabel.length < 4) return false
        val maxDistance = if (compactQuery.length <= 6) 1 else 2
        if (levenshteinDistance(compactQuery, compactLabel) <= maxDistance) return true
        val queryTokens = query.split(" ").filter { it.length >= 4 }
        val labelTokens = label.split(" ").filter { it.length >= 4 }
        if (queryTokens.isEmpty() || labelTokens.isEmpty()) return false
        val matchedTokenCount = queryTokens.count { q ->
            labelTokens.any { l ->
                kotlin.math.abs(q.length - l.length) <= 2 && levenshteinDistance(q, l) <= maxDistance
            }
        }
        return if (queryTokens.size >= 2) matchedTokenCount >= 2 else matchedTokenCount >= 1
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private fun stripContactCommandPrefixes(query: String): String {
        var value = normalizeVoiceCommand(query)
        listOf(
            "klient ", "klienta ", "kontakt ", "kontaktu ", "client ", "contact ",
            "zavolej ", "volej ", "volat ", "vytoc ", "vytocit ", "call ", "dial ",
            "posli whatsapp ", "posli zpravu whatsapp ", "napis whatsapp ", "napis na whatsapp ",
            "whatsapp ", "send whatsapp ", "whatsapp message "
        ).forEach { prefix ->
            value = value.removePrefix(prefix)
        }
        return value.trim()
    }

    private fun applyVoiceAliasToQuery(normalizedQuery: String): String {
        var value = normalizedQuery
        effectiveVoiceAliases().forEach { alias ->
            val aliasNorm = normalizeVoiceCommand(alias.alias)
            val targetNorm = normalizeVoiceCommand(alias.target)
            if (aliasNorm.isBlank() || targetNorm.isBlank()) return@forEach
            if (isReservedWakeAlias(aliasNorm)) return@forEach
            if (value == aliasNorm) value = targetNorm
        }
        return value
    }

    private fun applyVoiceAliasesToFreeText(text: String): String {
        // Apply aliases as word-boundary replacements across entire text (same as VoiceManager)
        var result = normalizeVoiceCommand(text)
        effectiveVoiceAliases()
            .sortedByDescending { normalizeVoiceCommand(it.alias).length }
            .forEach { alias ->
                val aliasNorm = normalizeVoiceCommand(alias.alias)
                val targetNorm = normalizeVoiceCommand(alias.target)
                if (aliasNorm.length < 2 || targetNorm.isBlank()) return@forEach
                if (isReservedWakeAlias(aliasNorm)) return@forEach
                // Word-boundary replace — same logic as VoiceManager.applyVoiceAliases()
                result = result.replace(
                    Regex("(?<![a-z])${Regex.escape(aliasNorm)}(?![a-z])"),
                    targetNorm
                )
            }
        return result
    }

    private fun parseVoiceAliasLearning(text: String): VoiceAliasCommand? {
        val normalized = normalizeVoiceCommand(text)
        val patterns = listOf(
            Regex("^kdyz reknu (.+) myslim (.+)$"),
            Regex("^kdyz reknu (.+) znamena to (.+)$"),
            Regex("^kdyz rikam (.+) myslim (.+)$"),
            Regex("^kdyz vyslovim (.+) myslim (.+)$"),
            Regex("^nauc se ze (.+) je (.+)$"),
            Regex("^nauc se (.+) jako (.+)$"),
            Regex("^zapamatuj si alias (.+) je (.+)$"),
            Regex("^zapamatuj si opravu (.+) je (.+)$"),
            Regex("^zapamatuj si ze kdyz reknu (.+) myslim (.+)$"),
            Regex("^oprav jmeno (.+) na (.+)$"),
            Regex("^oprav kontakt (.+) na (.+)$"),
            Regex("^alias (.+) je (.+)$"),
            Regex("^alias (.+) znamena (.+)$"),
            Regex("^learn name (.+) as (.+)$"),
            Regex("^remember alias (.+) is (.+)$"),
            Regex("^remember correction (.+) is (.+)$"),
            Regex("^when i say (.+) i mean (.+)$")
        )
        for (pattern in patterns) {
            val match = pattern.find(normalized) ?: continue
            return VoiceAliasCommand(match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        return null
    }

    private fun parseVoiceAliasForget(text: String): String? {
        val normalized = normalizeVoiceCommand(text)
        val prefixes = listOf(
            "zapomen alias ",
            "zapomen opravu ",
            "smaz alias ",
            "smaz opravu ",
            "forget alias ",
            "remove alias "
        )
        prefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            return normalized.drop(prefix.length).trim()
        }
        return null
    }

    private fun parseVoiceNavigationAddress(text: String): String? {
        val normalized = normalizeVoiceCommand(text)
        val askOnlyCommands = setOf(
            "spustit navigaci",
            "spust navigaci",
            "spousti navigaci",
            "zapni navigaci",
            "otevri navigaci",
            "otevri mapy",
            "spust mapy",
            "spousti mapy",
            "navigace",
            "naviguj",
            "trasa",
            "start navigation",
            "navigate",
            "navigation",
            "directions",
            "open maps",
            "uruchom nawigacje",
            "nawiguj",
            "nawigacja",
            "mapy"
        )
        if (normalized in askOnlyCommands) return ""
        val prefixes = listOf(
            "navigate me to ",
            "start navigation to ",
            "start navigation ",
            "directions to ",
            "take me to ",
            "drive to ",
            "open maps to ",
            "open maps ",
            "maps to ",
            "spustit navigaci na ",
            "spust navigaci na ",
            "spousti navigaci na ",
            "spustit navigaci do ",
            "spust navigaci do ",
            "spousti navigaci do ",
            "spustit navigaci k ",
            "spust navigaci k ",
            "spousti navigaci k ",
            "spustit navigaci ke ",
            "spust navigaci ke ",
            "spousti navigaci ke ",
            "spustit navigaci ",
            "spust navigaci ",
            "spousti navigaci ",
            "zapni navigaci na ",
            "zapni navigaci do ",
            "zapni navigaci k ",
            "zapni navigaci ke ",
            "zapni navigaci ",
            "otevri navigaci na ",
            "otevri navigaci do ",
            "otevri navigaci k ",
            "otevri navigaci ke ",
            "otevri mapy na ",
            "otevri mapy do ",
            "otevri mapy k ",
            "otevri mapy ke ",
            "spust mapy na ",
            "spousti mapy na ",
            "spust mapy do ",
            "spousti mapy do ",
            "spust mapy k ",
            "spousti mapy k ",
            "spust mapy ke ",
            "spousti mapy ke ",
            "mapy na ",
            "mapy do ",
            "mapy k ",
            "mapy ke ",
            "ukaz cestu na ",
            "ukaz cestu do ",
            "ukaz cestu k ",
            "ukaz cestu ke ",
            "ukaz trasu na ",
            "ukaz trasu do ",
            "ukaz trasu k ",
            "ukaz trasu ke ",
            "trasu na ",
            "trasu do ",
            "trasu k ",
            "trasu ke ",
            "vezmi me na ",
            "vezmi me do ",
            "vezmi me k ",
            "vezmi me ke ",
            "jed na ",
            "jed do ",
            "jed k ",
            "jed ke ",
            "naviguj na ",
            "naviguj do ",
            "naviguj k ",
            "naviguj ke ",
            "navigovat na ",
            "navigovat do ",
            "navigovat k ",
            "navigovat ke ",
            "navigace na ",
            "navigace do ",
            "navigace k ",
            "navigace ke ",
            "navigace ",
            "navigate to ",
            "navigation to ",
            "prowadz do ",
            "prowadz na ",
            "jedz do ",
            "jedz na ",
            "trasa do ",
            "trasa na ",
            "mapy do ",
            "mapy na ",
            "uruchom nawigacje do ",
            "uruchom nawigacje na ",
            "uruchom nawigacje ",
            "nawiguj do ",
            "nawiguj na ",
            "nawigacja do ",
            "nawigacja na ",
            "nawigacja "
        )
        prefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            return normalized.drop(prefix.length).trim()
        }
        val markers = listOf(
            " navigaci na ",
            " navigaci do ",
            " navigaci k ",
            " navigaci ke ",
            " navigace na ",
            " navigace do ",
            " navigace k ",
            " navigace ke ",
            " mapy na ",
            " mapy do ",
            " mapy k ",
            " mapy ke ",
            " trasu na ",
            " trasu do ",
            " trasu k ",
            " trasu ke ",
            " cestu na ",
            " cestu do ",
            " cestu k ",
            " cestu ke ",
            " directions to ",
            " navigation to ",
            " navigate to ",
            " nawigacje do ",
            " nawigacje na ",
            " nawigacja do ",
            " nawigacja na "
        )
        markers.firstOrNull { normalized.contains(it) }?.let { marker ->
            return normalized.substringAfter(marker).trim()
        }
        return null
    }

    private fun parseVoiceAddressReadTarget(text: String): String? {
        val normalized = normalizeVoiceCommand(text)
        val prefixes = listOf(
            "precti adresu kontaktu ",
            "precti adresu klienta ",
            "precti adresu ",
            "rekni adresu kontaktu ",
            "rekni adresu klienta ",
            "rekni adresu ",
            "read address for ",
            "read address ",
            "say address for ",
            "say address ",
            "odczytaj adres kontaktu ",
            "odczytaj adres klienta ",
            "odczytaj adres ",
            "powiedz adres "
        )
        prefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            return normalized.drop(prefix.length).trim()
        }
        return null
    }

    private fun parseVoiceCallTarget(text: String): String? {
        val normalized = normalizeVoiceCommand(text)
        val prefixes = listOf(
            "zavolej ",
            "volej ",
            "volat ",
            "vytoc ",
            "vytocit ",
            "call ",
            "dial ",
            "zadzwon do ",
            "zadzwon "
        )
        prefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            return normalized.drop(prefix.length).trim()
        }
        return null
    }

    private fun parseVoiceWhatsAppCommand(text: String): WhatsAppVoiceCommand? {
        val normalized = normalizeVoiceCommand(text)
        // Open WhatsApp inbox (no target, no message)
        val openOnlyPhrases = listOf(
            "otevri whatsapp", "otevri whats app", "otevri zpravy whatsapp",
            "otevri whatsapp zpravy", "whatsapp zpravy", "zpravy whatsapp",
            "open whatsapp", "open whats app", "whatsapp messages",
            "zobraz whatsapp", "whatsapp inbox", "whatsapp otevrit"
        )
        if (openOnlyPhrases.any { normalized == it || normalized.startsWith("$it ") }) {
            return WhatsAppVoiceCommand("__INBOX__", "")
        }
        val prefixes = listOf(
            "posli whatsapp ",
            "posli zpravu whatsapp ",
            "posli zpravu na whatsapp ",
            "napis whatsapp ",
            "napis na whatsapp ",
            "whatsapp message ",
            "whatsapp ",
            "send whatsapp ",
            "wyslij whatsapp ",
            "napisz whatsapp "
        )
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) } ?: return null
        val remainder = normalized.drop(prefix.length).trim()
        if (remainder.isBlank()) return WhatsAppVoiceCommand("", "")

        val separator = Regex("\\b(zpravu|zprava|message|text|ze|aby)\\b").find(remainder)
        if (separator != null && separator.range.first > 0) {
            val target = remainder.substring(0, separator.range.first).trim()
            val message = remainder.substring(separator.range.last + 1).trim()
            return WhatsAppVoiceCommand(target, message)
        }

        val words = remainder.split(" ").filter { it.isNotBlank() }
        for (count in minOf(4, words.size - 1) downTo 1) {
            val target = words.take(count).joinToString(" ")
            if (resolvePhoneTarget(target) != null) {
                return WhatsAppVoiceCommand(target, words.drop(count).joinToString(" "))
            }
        }
        return WhatsAppVoiceCommand(remainder, "")
    }

    private fun normalizeVoiceCommand(text: String): String =
        java.text.Normalizer.normalize(text.trim().lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")

    private fun parseAsteriskPreference(normalized: String): Boolean? {
        val text = normalized.trim()
        if (text.isBlank()) return null
        val disablePhrases = listOf(
            "bez hvezdicek",
            "nepouzivej hvezdicky",
            "nepouzivej hvezdicku",
            "nemas pouzivat hvezdicky",
            "nechci hvezdicky",
            "odstran hvezdicky",
            "without asterisks",
            "do not use asterisks",
            "dont use asterisks",
            "no asterisks"
        )
        if (disablePhrases.any { text.contains(it) }) return true

        val enablePhrases = listOf(
            "pouzivej hvezdicky",
            "muzes pouzivat hvezdicky",
            "muze pouzivat hvezdicky",
            "allow asterisks",
            "use asterisks again",
            "asterisks again"
        )
        if (enablePhrases.any { text.contains(it) }) return false
        return null
    }

    private fun isVoiceCancelCommand(normalized: String): Boolean =
        normalized in setOf("ne", "nee", "no", "nie", "zadny", "zadna", "zadne", "nula", "zero", "nic", "zrusit", "zrus", "cancel", "stop")

    private fun matchesStartWorkReportCommand(normalized: String): Boolean =
        listOf(
            "vykaz prace",
            "spustit vykaz",
            "spust vykaz",
            "spustit hlasovy vykaz",
            "hlasovy vykaz",
            "start work report",
            "work report",
            "raport pracy",
            "uruchom raport",
            "raport glosowy"
        ).any { normalized.contains(it) }

    private fun handleAction(response: AssistantResponse) {
        when (response.action_type) {
            "REFRESH" -> { refreshCrmData() }
            "MEMORY_REMEMBERED", "MEMORY_FORGOTTEN" -> {
                loadAssistantMemory()
            }
            "SEARCH_CONTACTS" -> {
                val query = response.action_data?.get("query") as? String ?: return
                val results = contactManager?.searchContact(query) ?: emptyList()
                if (results.isNotEmpty()) {
                    results.firstOrNull()?.let {
                        rememberRecentVoiceContact(it["name"], it["address"], it["phone"])
                    }
                    _uiState.value = _uiState.value.copy(contactResults = results)
                    val names = results.joinToString(", ") {
                        val name = sanitizeContactName(it["name"].orEmpty())
                        val address = it["address"]?.takeIf(String::isNotBlank)
                        if (address == null) name else "$name (${Strings.address}: $address)"
                    }
                    onVoiceInput("SYSTÉM: Našla jsem tyto kontakty: $names. Přečti je uživateli a nabídni volání, přečtení adresy a navigaci, pokud je adresa dostupná.")
                } else {
                    voiceManager?.speak(Strings.noContactFound(query))
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
                    plannedDate = data["planned_date"]?.toString(),
                    plannedStartAt = data["planned_start_at"]?.toString(),
                    plannedEndAt = data["planned_end_at"]?.toString(),
                    assignedUserId = (data["assigned_user_id"] as? Number)?.toLong(),
                    assignedTo = data["assigned_to"]?.toString(),
                    planningNote = data["planning_note"]?.toString(),
                    clientName = data["client_name"]?.toString(),
                    clientId = (data["client_id"] as? Number)?.toLong(),
                    createdBy = data["created_by"]?.toString() ?: "system",
                    calendarSyncEnabled = data["calendar_sync_enabled"] as? Boolean ?: true
                )
                _uiState.value = _uiState.value.copy(tasks = _uiState.value.tasks + newTask)
            }
            "CALL_CONTACT" -> {
                val phone = response.action_data?.get("phone") as? String
                val target = listOf("contact_name", "client_name", "name", "query")
                    .firstNotNullOfOrNull { response.action_data?.get(it) as? String }
                if (!phone.isNullOrBlank()) {
                    rememberRecentVoiceContact(target, null, phone)
                    val label = target?.takeIf(String::isNotBlank) ?: phone
                    val message = Strings.dialingContact(label)
                    _uiState.value = _uiState.value.copy(
                        pendingCall = phone,
                        lastAiReply = message,
                        history = (_uiState.value.history + ChatMessage("assistant", message)).takeLast(30)
                    )
                    voiceManager?.speak(message, expectReply = false)
                } else if (!target.isNullOrBlank()) {
                    startVoiceCallTarget(target, target)
                }
            }
            "SEND_WHATSAPP" -> {
                val data = response.action_data ?: return
                val phone = data["phone"] as? String
                val target = listOf("client_name", "contact_name", "name", "query")
                    .firstNotNullOfOrNull { data[it] as? String }
                val message = (data["message"] as? String).orEmpty()
                if (!phone.isNullOrBlank()) {
                    rememberRecentVoiceContact(target, null, phone)
                    val label = target?.takeIf(String::isNotBlank) ?: phone
                    val targetLang = _uiState.value.tenantConfig?.get("default_customer_lang")?.toString()?.takeIf { it.isNotBlank() } ?: "en"
                    viewModelScope.launch {
                        var sentViaServer = false
                        try {
                            val sendRes = api.sendWhatsAppMessage(mapOf(
                                "to" to phone,
                                "message" to message,
                                "target_language" to targetLang
                            ))
                            if (sendRes.isSuccessful) {
                                sentViaServer = true
                                val confirmMsg = Strings.whatsAppSentViaServer
                                _uiState.value = _uiState.value.copy(
                                    lastAiReply = confirmMsg,
                                    history = (_uiState.value.history + ChatMessage("assistant", confirmMsg)).takeLast(30)
                                )
                                voiceManager?.speak(confirmMsg, expectReply = _uiState.value.isDialogMode)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("WhatsApp", "Server send failed, fallback: ${e.message}")
                        }
                        if (!sentViaServer) {
                            val reply = Strings.openingWhatsAppFor(label)
                            _uiState.value = _uiState.value.copy(
                                pendingWhatsAppPhone = phone,
                                pendingWhatsAppMessage = message,
                                lastAiReply = reply,
                                history = (_uiState.value.history + ChatMessage("assistant", reply)).takeLast(30)
                            )
                            voiceManager?.speak(reply, expectReply = false)
                        }
                    }
                } else if (!target.isNullOrBlank()) {
                    startVoiceWhatsApp(WhatsAppVoiceCommand(target, message), target)
                }
            }
            "START_NAVIGATION", "OPEN_NAVIGATION", "NAVIGATE" -> {
                val data = response.action_data ?: return
                val target = listOf("address", "target", "client_name", "contact_name", "name", "query")
                    .mapNotNull { data[it] as? String }
                    .firstOrNull { it.isNotBlank() }
                if (!target.isNullOrBlank()) {
                    startVoiceNavigationTarget(target, target)
                } else {
                    askForNavigationAddress(response.reply_cs)
                }
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

    fun updateDefaultRates(rates: Map<String, Any?>, onDone: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val res = api.updateDefaultRates(1, rates)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(tenantRateTypes = res.body() ?: _uiState.value.tenantRateTypes)
                    onDone?.invoke(true, null)
                } else {
                    val msg = res.errorBody()?.string()?.let {
                        try { org.json.JSONObject(it).optString("detail").ifBlank { it } } catch (_: Exception) { it }
                    }
                    onDone?.invoke(false, msg ?: Strings.backendActionFailed(Strings.serviceRates, res.code()))
                }
            } catch (e: Exception) {
                onDone?.invoke(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun addServiceRateType(rateType: String, description: String, defaultRate: Double, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.addServiceRateType(1, mapOf(
                    "rate_type" to rateType,
                    "description" to description,
                    "rate" to defaultRate
                ))
                if (res.isSuccessful) {
                    loadTenantRateTypes()
                    onDone(true, null)
                } else {
                    val msg = res.errorBody()?.string()?.let {
                        try { org.json.JSONObject(it).optString("detail").ifBlank { it } } catch (_: Exception) { it }
                    }
                    onDone(false, msg ?: Strings.backendActionFailed(Strings.addCustomRateType, res.code()))
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: Strings.connectionError)
            }
        }
    }

    fun deleteServiceRateType(rateType: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.deleteServiceRateType(1, rateType)
                if (res.isSuccessful) {
                    loadTenantRateTypes()
                    onDone(true, null)
                } else {
                    val msg = res.errorBody()?.string()?.let {
                        try { org.json.JSONObject(it).optString("detail").ifBlank { it } } catch (_: Exception) { it }
                    }
                    onDone(false, msg ?: Strings.backendActionFailed(Strings.deleteRateType, res.code()))
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: Strings.connectionError)
            }
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
        applyAppLanguage(langCode, persist = true)
        _uiState.value = _uiState.value.copy(status = Strings.ready, lastAiReply = Strings.waitingForCommand)
        loadNatureHistory()
    }
    fun resetSettings() { settingsManager?.resetAll(); setStatus(Strings.settingsRestored) }
    fun exportCrmData() { viewModelScope.launch { setStatus(Strings.exportUnavailable) } }
    fun triggerManualImport() {
        val path = settingsManager?.importFilePath ?: ""
        val table = settingsManager?.importTargetTable ?: "clients"
        if (path.isBlank()) { setStatus(Strings.pathNotSet); return }
        _uiState.value = _uiState.value.copy(pendingImport = mapOf("source" to path, "table" to table))
    }
    fun cancelImport() { _uiState.value = _uiState.value.copy(pendingImport = null) }
    fun confirmImport() { _uiState.value = _uiState.value.copy(pendingImport = null); setStatus(Strings.importStarted) }

    fun toggleBackground() {
        val current = _uiState.value.isBackgroundActive
        _uiState.value = _uiState.value.copy(isBackgroundActive = !current)
        if (current) {
            voiceManager?.stop()
            setStatus(Strings.backgroundDisabled)
        } else {
            voiceManager?.startHotwordLoop()
            setStatus(Strings.backgroundListening)
        }
    }

    fun restartVoice() {
        settingsManager?.pendingVoiceSessionId = null
        endVoiceSession()
        voiceManager?.stop()
        setStatus(Strings.restarting)
        voiceManager?.startHotwordLoop()
        setStatus(Strings.ready)
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

data class VoiceAliasTrainingState(
    val target: String = "",
    val targetType: String = "contact",
    val samples: List<String> = emptyList()
)

/** One candidate returned by /voice/resolve when requires_clarification = true. */
data class VoiceDisambiguationCandidate(
    val contactId: String,
    val displayName: String,
    val companyName: String?,
    val matchedAlias: String,
    val matchConfidence: Float,
    val disambiguationHint: String,
    val aliasType: String
)

/** Full result of a /voice/resolve call that the UI needs to act on. */
data class VoiceResolveResult(
    val originalText: String,
    val resolved: Boolean,
    val controlCode: String?,
    val actionCode: String?,
    val confidence: Float,
    val resolutionMethod: String?,
    val requiresClarification: Boolean,
    val clarificationQuestion: String?,
    val candidates: List<VoiceDisambiguationCandidate>,
    val args: Map<String, Any?>,
    val riskLevel: String,
    val error: String?
)

data class UiState(
    val isListening: Boolean = false, 
    val appLanguage: String = Strings.getLangCode(),
    val recognitionLocale: String = Strings.getRecognitionLocale(),
    val status: String = Strings.ready, 
    val lastAiReply: String = Strings.waitingForYourCommand,
    val history: List<ChatMessage> = emptyList(),
    val pendingNavigationAddress: String? = null,
    val awaitingNavigationAddress: Boolean = false,
    val contactResults: List<Map<String, String>> = emptyList(),
    val systemSettings: Map<String, Any> = emptyMap(),
    val calendarFeed: List<CalendarFeedEntry> = emptyList(),
    val tasks: List<Task> = emptyList(), 
    val clients: List<Client> = emptyList(), 
    val sharedContacts: List<SharedContact> = emptyList(),
    val contactSections: List<ContactSection> = emptyList(),
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
    val firstLoginUsers: List<FirstLoginUser> = emptyList(),
    val backendUsers: List<BackendUser> = emptyList(),
    val backendRoles: List<BackendRole> = emptyList(),
    val backendUsersLoading: Boolean = false,
    val backendUsersError: String? = null,
    val assistantMemory: List<AssistantMemoryItem> = emptyList(),
    val assistantMemoryLoading: Boolean = false,
    val assistantMemoryError: String? = null,
    val adminActivityLog: List<AdminActivityLogEntry> = emptyList(),
    val adminActivityLoading: Boolean = false,
    val adminActivityError: String? = null,
    val hierarchyIntegrityReport: HierarchyIntegrityReport? = null,
    val hierarchyIntegrityLoading: Boolean = false,
    val hierarchyIntegrityError: String? = null,
    val pendingImport: Map<String, String>? = null,
    val pendingCall: String? = null,
    val pendingWhatsAppPhone: String? = null,
    val pendingWhatsAppMessage: String? = null,
    val voiceAliasTraining: VoiceAliasTrainingState? = null,
    val pendingPhotoTaskId: String? = null,
    val pendingPhotoTaskTitle: String? = null,
    val pendingPlantCaptureRequestId: Long? = null,
    val plantCaptureMode: String = "identify",
    val selectedPlantRecognition: PlantRecognitionResponse? = null,
    val selectedPlantDisease: PlantDiseaseResponse? = null,
    val selectedMushroomRecognition: MushroomRecognitionResponse? = null,
    val recognitionHistory: List<RecognitionHistoryEntry> = emptyList(),
    val plantRecognitionLoading: Boolean = false,
    val plantRecognitionError: String? = null,
    val plantDiseaseLoading: Boolean = false,
    val plantDiseaseError: String? = null,
    val mushroomRecognitionLoading: Boolean = false,
    val mushroomRecognitionError: String? = null,
    val isPlantVoiceCaptureActive: Boolean = false,
    val isBackgroundActive: Boolean = true,
    val voiceSessionId: String? = null,
    val voiceSessionStep: String? = null,
    val voiceSessionPrompt: String? = null,
    val voiceSessionSummary: String? = null,
    val voiceSessionWhatsApp: String? = null,
    val isVoiceSessionActive: Boolean = false,
    val onboardingComplete: Boolean? = null,
    val tenantConfig: Map<String, Any?>? = null,
    val tenantConfigError: String? = null,
    val tenantConfigRefreshMs: Long = 0L,
    // Settings screen — dedicated clean endpoints
    val serverVersionInfo: Map<String, Any?>? = null,
    val tenantProfile: Map<String, Any?>? = null,
    val tenantLanguages: Map<String, Any?>? = null,
    val settingsLoadErrors: Map<String, String> = emptyMap(),
    val settingsLastRefreshMs: Long = 0L,
    val currentUserId: Long? = null,
    val currentUserDisplayName: String? = null,
    val currentUserEmail: String? = null,
    val currentUserRole: String? = null,
    val currentUserPermissions: Map<String, Boolean> = emptyMap(),
    val mustChangePassword: Boolean = false,
    val awaitingBiometricEnrollment: Boolean = false,
    val loginNotice: String? = null,
    val loggedIn: Boolean? = null,
    // Dialog mode — continuous conversation until end phrase
    val isDialogMode: Boolean = false,
    val dialogSessionHistory: List<ChatMessage> = emptyList(),  // full session for summarization
    val dialogSessionStartMs: Long = 0L,
    val contactSortingSession: ContactSortingSession? = null,
    val contactDuplicates: List<ContactDuplicate> = emptyList(),
    val pendingMergeDialog: ContactDuplicate? = null,
    val pendingAppNavigation: String? = null,
    val pendingAppAction: String? = null,
    val toolHubTiles: List<ToolHubTile> = emptyList(),
    val toolHubTilesLoading: Boolean = false,
    // Voice resolve / AI control bridge
    val currentScreenCode: String? = null,
    val pendingVoiceResolve: VoiceResolveResult? = null,
    // Dynamic service rate types loaded from server
    val tenantRateTypes: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList()
)
