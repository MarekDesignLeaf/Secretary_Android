# Secretary Android – Opravy chyb (Prompt pro programátora)

> Vygenerováno automatickou analýzou kódu.
> Repozitář: `marekdesignleaf/secretary_android` | Branch: `claude/create-secretary-map-B9XV0`
> Stack: **Kotlin + Jetpack Compose + Retrofit** | Min SDK: 26 (Android 8)

---

## Kontext projektu

Secretary Android je CRM aplikace s hlasovým ovládáním pro terénní servisní firmy. Hlavní soubory:
- `MainActivity.kt` (~239 KB) – hlavní aktivita, veškerý UI stav, API volání
- `VoiceManager.kt` – STT/TTS, hotword detekce, state machine
- `CalendarManager.kt` – Google Calendar integrace
- `SettingsManager.kt` – SharedPreferences wrapper, JWT tokeny, konfigurace
- `Models.kt` – 150+ data classes pro API
- `SecretaryApi.kt` – Retrofit interface (100+ endpointů)

---

## KRITICKÉ OPRAVY (implementovat okamžitě)

---

### [A1] Chybí automatický refresh tokenu při 401

**Závažnost:** KRITICKÁ – po expiraci access tokenu (24h) všechna API volání tiše selhávají

**Kde:** `MainActivity.kt` – vytvoření `OkHttpClient`

**Problém:** Interceptor přidává Bearer token, ale při 401 odpovědi se nepokusí o refresh a retry.

**Oprava:** Přidat response interceptor:
```kotlin
// V OkHttpClient.Builder(), po stávajícím request interceptoru:
.addInterceptor { chain ->
    val originalRequest = chain.request().newBuilder()
        .header("Authorization", "Bearer ${settingsManager?.accessToken ?: ""}")
        .build()
    var response = chain.proceed(originalRequest)
    
    if (response.code == 401 && settingsManager?.refreshToken != null) {
        response.close()
        val refreshed = runBlocking { tryRefreshToken() }
        if (refreshed) {
            val retryRequest = chain.request().newBuilder()
                .header("Authorization", "Bearer ${settingsManager?.accessToken ?: ""}")
                .build()
            response = chain.proceed(retryRequest)
        }
    }
    response
}
```

**Poznámka:** `tryRefreshToken()` musí být dostupná z tohoto kontextu. Pokud je suspend funkce, použít `runBlocking { tryRefreshToken() }` (v interceptoru není coroutine kontext).

---

### [A2] VoiceManager – TTS inicializace může způsobit stuck state

**Závažnost:** KRITICKÁ – hlasové ovládání přestane fungovat bez jakékoliv informace uživateli

**Kde:** `VoiceManager.kt` – funkce `setupTts()` a `speak()`

**Problém:** Pokud TTS init selže (`status != TextToSpeech.SUCCESS`), `isTtsReady` zůstane `false` navždy. `speak()` pak pořád postuje handlery bez přehrání zvuku → stuck state.

**Oprava:**
```kotlin
// 1. Přidat field na úrovni třídy:
private var ttsFailedPermanently = false

// 2. V setupTts() callback:
TextToSpeech(context.applicationContext) { status ->
    if (status != TextToSpeech.SUCCESS) {
        ttsFailedPermanently = true
        isTtsReady = false
        Log.e(TAG, "TTS initialization failed with status $status")
        onStatusChange?.invoke("TTS není dostupné – hlasové odpovědi vypnuty")
    } else {
        isTtsReady = true
        ttsFailedPermanently = false
        // ... nastavení jazyka atd.
    }
}

// 3. Na začátku speak():
fun speak(text: String, expectReply: Boolean = false, stayIdleAfterSpeak: Boolean = false) {
    if (ttsFailedPermanently) {
        // TTS nefunguje – přeskočit přehrání, pokračovat stavem
        handler.post {
            when {
                stayIdleAfterSpeak -> stop()
                expectReply -> startListening()
                else -> startHotwordLoop()
            }
        }
        return
    }
    // ... původní kód
}
```

---

### [A3] CalendarManager – hardcoded osobní emaily

**Závažnost:** KRITICKÁ – u jiných uživatelů Calendar sync nikdy nenajde kalendář

**Kde:** `CalendarManager.kt` – hledej `MY_EMAILS` nebo seznam emailů obsahující `hutrat05@gmail.com`

**Problém:** Seznam emailů pro výběr Google Kalendáře je natvrdo zakódován s osobními emaily vývojáře.

**Oprava:**
```kotlin
// 1. Přidat SettingsManager do konstruktoru (pokud tam není):
class CalendarManager(private val context: Context, private val settingsManager: SettingsManager? = null) {

// 2. Nahradit hardcoded email check:
private fun isUserCalendar(ownerEmail: String): Boolean {
    // Zkusit získat email přihlášeného uživatele
    val userEmail = settingsManager?.loginEmail  // nebo jakékoliv pole drží user email
        ?: settingsManager?.currentUserEmail
        ?: return false  // pokud nevíme, nesynchronizovat
    return ownerEmail.equals(userEmail, ignoreCase = true)
}

// 3. V getDefaultCalendarId() nahradit:
// ŠPATNĚ:
// if (MY_EMAILS.contains(ownerEmail)) { ... }
// SPRÁVNĚ:
// if (isUserCalendar(ownerEmail)) { ... }
```

**Poznámka:** Zkontrolovat, jaké pole v `SettingsManager` drží email přihlášeného uživatele (může být `loginEmail`, `currentUserEmail`, nebo email z `currentBackendUserId` lookup).

---

## VYSOKÁ PRIORITA

---

### [A4] Work hours – timezone bug při cestování

**Kde:** `SettingsManager.kt` – funkce `isWithinWorkHours()`

**Problém:** `Calendar.getInstance()` používá aktuální timezone zařízení. Pokud uživatel cestuje, pracovní doba se posune.

**Oprava:**
```kotlin
// 1. Přidat property:
var workHoursTimezone: String
    get() = getScopedString("work_hours_timezone", java.util.TimeZone.getDefault().id)
    set(v) = setScopedString("work_hours_timezone", v)

// 2. V isWithinWorkHours():
fun isWithinWorkHours(): Boolean {
    if (!workHoursEnabled) return true
    return try {
        val tz = java.util.TimeZone.getTimeZone(workHoursTimezone)
        val now = java.util.Calendar.getInstance(tz)  // <-- použít uloženou TZ
        val current = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                      now.get(java.util.Calendar.MINUTE)
        val start = parseTimeToMinutes(workHoursStart)
        val end = parseTimeToMinutes(workHoursEnd)
        current in start..end
    } catch (e: Exception) {
        true  // fallback: mimo pracovní dobu nezablokovat
    }
}
```

---

### [A5] Offline mode setting je mrtvý flag

**Kde:** `SettingsManager.kt` (definice), `MainActivity.kt` (použití)

**Problém:** `offlineMode = true` nastavení nemá žádný efekt.

**Oprava – přidat kontrolu před API voláními:**
```kotlin
// V MainActivity nebo ViewModel, před každým API voláním:
if (settingsManager?.offlineMode == true) {
    Log.w("Secretary", "Offline mode – skipping API call")
    // Zobrazit uživateli info
    _uiState.value = _uiState.value.copy(statusMessage = "Offline režim – data nejsou aktuální")
    return
}
```

---

### [A6] Nullable pole API response bez null-check v UI

**Kde:** `MainActivity.kt`, `Models.kt`

**Problém:** Nullable pole jako `email_primary`, `job_status`, `planned_start_at` použita přímo v UI → potenciální NullPointerException.

**Oprava – příklady:**
```kotlin
// ŠPATNĚ:
Text("✉ $it")  // kde it = client.email_primary (nullable)

// SPRÁVNĚ:
client.email_primary?.takeIf { it.isNotBlank() }?.let { Text("✉ $it") }

// ŠPATNĚ:
val color = jobStatusColors[job.job_status]!!

// SPRÁVNĚ:
val color = jobStatusColors[job.job_status ?: "unknown"] ?: Color.Gray

// ŠPATNĚ:
val dateStr = entry.planned_start_at ?: entry.planned_date ?: entry.planned_end_at

// SPRÁVNĚ:
val dateStr = listOfNotNull(entry.planned_start_at, entry.planned_date, entry.planned_end_at)
    .firstOrNull()?.takeIf { it.isNotBlank() } ?: ""
```

---

### [A7] VoiceManager – zombie recognizer po destroy()

**Kde:** `VoiceManager.kt` – funkce `destroy()` a `ensureRecognizerAndListen()`

**Problém:** Handler.post callbacks naplánované před `destroy()` mohou znovu vytvořit recognizer po destrukci.

**Oprava:**
```kotlin
// 1. Přidat field:
@Volatile
private var isDestroyed = false

// 2. V destroy() – první řádek:
fun destroy() {
    isDestroyed = true
    stop()
    handler.removeCallbacksAndMessages(null)
    recognizer?.destroy()
    recognizer = null
    tts?.shutdown()
    tts = null
}

// 3. V každém handler.post bloku:
handler.post {
    if (isDestroyed) return@post  // guard na začátku
    // ... původní kód
}
```

---

### [A8] Race condition – voice input po odhlášení

**Kde:** `MainActivity.kt` – funkce zpracovávající voice input (hledej `onVoiceInput` nebo volání API po rozpoznání hlasu)

**Oprava:**
```kotlin
fun onVoiceInput(text: String) {
    // Guard: ignorovat voice input pokud uživatel není přihlášen
    if (_uiState.value.loggedIn != true) {
        Log.w("Secretary", "Voice input '$text' received while not logged in – ignoring")
        return
    }
    viewModelScope.launch {
        // ... původní zpracování
    }
}
```

---

### [A9] Hardcoded "Marek" jako autor úkolu

**Kde:** `MainActivity.kt` – hledej `"created_by" to "Marek"`

**Oprava:**
```kotlin
// ŠPATNĚ:
val taskData = mapOf(
    "title" to title,
    "created_by" to "Marek",  // HARDCODED!
    // ...
)

// SPRÁVNĚ:
val taskData = mapOf(
    "title" to title,
    "created_by" to (settingsManager?.currentUserDisplayName
        ?: settingsManager?.loginEmail
        ?: "Unknown"),
    // ...
)
```

---

## STŘEDNÍ PRIORITA

---

### [A10] Navigation back stack – zpět z detailu skočí na Home

**Kde:** `MainActivity.kt` – NavHost navigace, hledej `popUpTo(startDestinationId)`

**Problém:** `popUpTo(startDestinationId)` v každém `navigate()` volání maže celý back stack.

**Oprava:**
```kotlin
// Pro bottom navigation tab items – správně:
navController.navigate(screen.route) {
    popUpTo(navController.graph.startDestinationId) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}

// Pro navigaci na detail obrazovky – bez popUpTo!
navController.navigate("client_detail/$clientId")
// Zpět pak funguje přirozeně
```

---

### [A11] Retrofit instance ignoruje změnu URL v nastavení

**Kde:** `MainActivity.kt` – vytvoření API klienta (hledej `by lazy` nebo `Retrofit.Builder()`)

**Problém:** Retrofit je vytvořen jednou (lazy) – změna API URL v nastavení nemá efekt.

**Oprava:**
```kotlin
private var _apiInstance: SecretaryApi? = null
private var _lastApiUrl: String? = null

private val api: SecretaryApi
    get() {
        val currentUrl = settingsManager?.apiUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://web-production-4b451.up.railway.app"
        
        if (_apiInstance == null || _lastApiUrl != currentUrl) {
            _lastApiUrl = currentUrl
            _apiInstance = Retrofit.Builder()
                .baseUrl(if (currentUrl.endsWith("/")) currentUrl else "$currentUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)  // reuse stejný OkHttpClient
                .build()
                .create(SecretaryApi::class.java)
        }
        return _apiInstance!!
    }
```

---

### [A12] Tichá selhání API – uživatel nedostane feedback

**Kde:** `MainActivity.kt` – `catch (e: Exception)` bloky

**Oprava – přidat error state:**
```kotlin
// Přidat do UI state:
data class UiState(
    // ... ostatní pole ...
    val lastError: String? = null,
    val errorTimestamp: Long = 0
)

// V catch blocích:
catch (e: Exception) {
    Log.e("Secretary", "API error", e)
    _uiState.value = _uiState.value.copy(
        lastError = e.message ?: "Neznámá chyba",
        errorTimestamp = System.currentTimeMillis()
    )
}

// V UI – zobrazit snackbar nebo toast:
LaunchedEffect(uiState.errorTimestamp) {
    if (uiState.lastError != null) {
        snackbarHostState.showSnackbar(uiState.lastError)
    }
}
```

---

### [A13] Calendar sync – selhání jednotlivých eventů jsou ignorována

**Kde:** `CalendarManager.kt` – funkce `syncPlanningEntries()`

**Oprava:**
```kotlin
fun syncPlanningEntries(entries: List<CalendarFeedEntry>): Pair<Int, List<String>> {
    val errors = mutableListOf<String>()
    var synced = 0
    
    entries.forEach { entry ->
        try {
            val result = syncSingleEntry(entry)  // refactor jednotlivé sync logiky
            if (result) synced++ else errors.add("${entry.entry_key}: sync failed")
        } catch (e: Exception) {
            errors.add("${entry.entry_key}: ${e.message}")
        }
    }
    
    if (errors.isNotEmpty()) {
        Log.w(TAG, "Calendar sync errors: $errors")
    }
    return synced to errors
}
```

---

### [A14] Biometrika – chybí fallback na manuální login

**Kde:** `LoginScreen.kt` – funkce `tryBiometricLogin()`

**Oprava:**
```kotlin
fun tryBiometricLogin(profile: BiometricProfile, onError: (String) -> Unit, onSuccess: () -> Unit) {
    val canAuth = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        Log.w("LoginScreen", "Biometric unavailable: $canAuth")
        onError("Biometrika není dostupná – prosím přihlaste se heslem")
        showManualLoginForm()  // automaticky zobrazit formulář
        return
    }
    // ... zbytek
}
```

---

### [A15] Job audit entry – redundantní job_id v body

**Kde:** `MainActivity.kt` – hledej `addJobAuditEntry` volání

**Problém:** Body obsahuje `"job_id"` i když je job_id už v URL path parametru.

**Oprava:**
```kotlin
// ŠPATNĚ:
val data = mapOf(
    "job_id" to jobId.toString(),  // REDUNDANTNÍ – je v URL!
    "action_type" to actionType,
    "description" to description
)

// SPRÁVNĚ:
val data = mapOf(
    // job_id není potřeba – API ho bere z URL
    "action_type" to actionType,
    "description" to description
)
```

---

### [A16] Calendar permission denial při běhu aplikace

**Kde:** `MainActivity.kt` – volání `loadCalendarFeed()`

**Oprava:**
```kotlin
fun loadCalendarFeed(days: Int = 30) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    
    if (!hasPermission) {
        Log.w("Secretary", "READ_CALENDAR permission not granted")
        _uiState.value = _uiState.value.copy(calendarFeed = emptyList())
        return
    }
    viewModelScope.launch { /* ... */ }
}
```

---

## NÍZKÁ PRIORITA

---

### [A17] SimpleDateFormat není thread-safe

**Kde:** `CalendarManager.kt` – parsování dat

**Oprava:** Nahradit `SimpleDateFormat` za `java.time.format.DateTimeFormatter` (thread-safe, dostupné od API 26):
```kotlin
// ŠPATNĚ:
val sdf = java.text.SimpleDateFormat(pattern, Locale.getDefault())
val date = sdf.parse(dateStr)

// SPRÁVNĚ (minSdk 26+):
val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
val localDate = java.time.LocalDateTime.parse(dateStr, formatter)
val millis = localDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
```

---

### [A18] TTS fallback jazyk – silent fail pokud ani fallback není dostupný

**Kde:** `VoiceManager.kt` – nastavení jazyka TTS

**Oprava:**
```kotlin
val primaryResult = tts?.setLanguage(Locale.forLanguageTag(language))
if (primaryResult == TextToSpeech.LANG_MISSING_DATA ||
    primaryResult == TextToSpeech.LANG_NOT_SUPPORTED) {
    
    val fallbackResult = tts?.setLanguage(Locale.UK)  // en-GB fallback
    if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
        fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
        
        Log.e(TAG, "No TTS language available – disabling voice output")
        isTtsReady = false
        ttsFailedPermanently = true
        onStatusChange?.invoke("Hlasový výstup není dostupný – nainstalujte TTS data")
        return
    }
}
```

---

## Pořadí implementace

```
Fáze 1 (okamžitě – tento sprint):
  A3 – hardcoded emaily v CalendarManager  
  A1 – token refresh interceptor (401 handling)
  A2 – VoiceManager TTS stuck state
  A9 – hardcoded "Marek" jako autor

Fáze 2 (příští sprint):
  A7 – VoiceManager zombie recognizer
  A8 – race condition voice/logout
  A4 – work hours timezone
  A6 – null safety v UI
  A11 – Retrofit URL reload
  A15 – job audit redundant field

Fáze 3 (backlog):
  A5 – offline mode implementace
  A10 – navigation back stack
  A12 – error feedback uživateli
  A13 – calendar sync error reporting
  A14 – biometric fallback
  A16 – calendar permission guard
  A17 – thread-safe DateFormat
  A18 – TTS fallback
```

---

## Testování

Pro každou opravu ověřit:
1. Sestavit projekt (`./gradlew assembleDebug`)
2. Spustit na emulátoru/zařízení
3. Přihlásit se a ověřit základní flow
4. Pro A1: vymazat token ručně a ověřit auto-refresh
5. Pro A2: simulovat TTS init failure (emulátoru odpojit TTS)
6. Pro A3: přihlásit se s jiným účtem než `marek@designleaf.co.uk`
7. Pro A4: změnit timezone zařízení a ověřit pracovní dobu
