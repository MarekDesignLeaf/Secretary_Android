# Secretary CRM – Kompletní Mapa Systému (Android)

> Verze: 1.2a | Platform: Android 8+ (API 26+) | Stack: Kotlin + Jetpack Compose

---

## 1. Systémová Architektura

```mermaid
graph TD
    subgraph ANDROID ["Android App – Kotlin + Jetpack Compose"]
        UI["UI Layer\nJetpack Compose Screens"]
        VM["ViewModel + StateFlow"]
        MGR["Managers\nVoice / Calendar / Contacts / Settings"]
        APICLIENT["SecretaryApi\nRetrofit + OkHttp"]
        VOICESVC["VoiceService\nForeground Service"]
    end

    subgraph SERVER ["Railway Server – FastAPI Python"]
        AUTH_M["Auth Module\nJWT HS256"]
        CRM_M["CRM Module"]
        NATURE_M["Nature Recognition"]
        VOICE_M["Voice Sessions"]
        WORKREP_M["Work Reports"]
    end

    subgraph DB ["PostgreSQL – Railway"]
        DBMAIN[("Hlavní databáze")]
    end

    subgraph EXT ["Externí API"]
        OPENAI["OpenAI GPT-4o-mini"]
        PLANTNET["PlantNet API"]
        PLANTID["Plant.id API"]
        KINDWISE["Kindwise Mushroom"]
    end

    subgraph ANDROIDSYS ["Android Systém"]
        GCAL["Google Calendar Provider"]
        CONTACTS["Contacts Provider"]
        SPEECH["SpeechRecognizer / TextToSpeech"]
        BIOMETRIC["BiometricPrompt"]
        NOTIFICATION["NotificationManager"]
    end

    APICLIENT -->|"HTTPS REST / Bearer JWT"| AUTH_M
    APICLIENT --> CRM_M
    APICLIENT --> NATURE_M
    APICLIENT --> VOICE_M
    APICLIENT --> WORKREP_M

    AUTH_M --> DBMAIN
    CRM_M --> DBMAIN
    NATURE_M -->|"AI"| OPENAI
    NATURE_M --> PLANTNET & PLANTID & KINDWISE

    MGR -->|"CalendarManager"| GCAL
    MGR -->|"ContactManager"| CONTACTS
    MGR -->|"VoiceManager"| SPEECH
    VOICESVC -->|"Background STT"| SPEECH
    VOICESVC --> NOTIFICATION

    UI --> VM --> APICLIENT
    VM --> MGR
    VOICESVC --> VM
```

---

## 2. Architektura Aplikace (MVVM)

```mermaid
graph TD
    subgraph UILAYER ["UI Layer – Jetpack Compose"]
        MA["MainActivity"]
        NAV["NavHost (Navigation.kt)"]
        SCREENS["Compose Screens"]
        DIALOGS["Dialogy (CrmDialogs)"]
    end

    subgraph VMLAYER ["ViewModel + State"]
        STATE["StateFlow / mutableStateOf"]
        COROUTINES["Coroutines (viewModelScope)"]
    end

    subgraph SERVICELAYER ["Services & Managers"]
        VM_MGR["VoiceManager\nSTT + TTS + Hotword"]
        CAL_MGR["CalendarManager\nGoogle Calendar CRUD"]
        CONT_MGR["ContactManager\nAndroid Contacts"]
        SET_MGR["SettingsManager\nSharedPreferences"]
        VOICE_SVC["VoiceService\nForegroundService"]
    end

    subgraph DATALAYER ["Data Layer"]
        RETROFIT["SecretaryApi (Retrofit)"]
        MODELS["Models.kt (150+ data classes)"]
    end

    MA --> NAV --> SCREENS --> DIALOGS
    SCREENS --> STATE
    STATE --> COROUTINES --> RETROFIT
    COROUTINES --> VM_MGR & CAL_MGR & CONT_MGR
    VM_MGR --> VOICE_SVC
    RETROFIT --> MODELS
    SET_MGR -.->|"konfigurace"| VM_MGR & RETROFIT
```

---

## 3. Obrazovky a Navigace

```mermaid
flowchart TD
    START(["Spuštění aplikace"])
    LOGIN["LoginScreen\nemail + heslo / biometrika"]
    ONBOARD["OnboardingScreen\nNastavení firmy"]
    HOME["Home\nDashboard"]
    
    subgraph BOTTOMNAV ["Spodní Navigace"]
        CRM_TAB["CRM Tab"]
        TASKS_TAB["Tasks Tab"]
        CAL_TAB["Calendar Tab"]
        TOOLS_TAB["Tools Tab"]
        SETTINGS_TAB["Settings Tab"]
    end

    subgraph CRM_DETAIL ["CRM Detail Obrazovky"]
        CLIENT_LIST["Seznam klientů"]
        CLIENT_DETAIL["Detail klienta"]
        JOB_DETAIL["Detail zakázky"]
        TASK_DETAIL["Detail úkolu"]
        LEAD_LIST["Leady"]
        INVOICE_LIST["Faktury"]
        QUOTE_LIST["Nabídky"]
        COMM_LIST["Komunikace"]
    end

    subgraph SETTINGS_DETAIL ["Nastavení Sekce"]
        S1["Hlasové ovládání"]
        S2["Server konfigurace"]
        S3["CRM výchozí hodnoty"]
        S4["Notifikace"]
        S5["Pracovní profil"]
        S6["Import / Data"]
        S7["Správa uživatelů"]
        S8["Téma"]
        S9["Jazyk"]
    end

    subgraph TOOLS_DETAIL ["Tools Obrazovky"]
        PLANT_TAB["PlantRecognitionTab\nRostliny / Houby / Nemoci"]
        CONTACTS_TAB["ContactsDirectoryTab\nSdílené kontakty"]
    end

    START --> LOGIN
    LOGIN -->|"První spuštění"| ONBOARD
    LOGIN -->|"Přihlášen"| HOME
    ONBOARD --> HOME

    HOME --- BOTTOMNAV
    CRM_TAB --> CLIENT_LIST
    CLIENT_LIST --> CLIENT_DETAIL
    CLIENT_DETAIL --> JOB_DETAIL
    JOB_DETAIL --> TASK_DETAIL
    CRM_TAB --> LEAD_LIST & INVOICE_LIST & QUOTE_LIST & COMM_LIST
    TOOLS_TAB --> PLANT_TAB & CONTACTS_TAB
    SETTINGS_TAB --> S1 & S2 & S3 & S4 & S5 & S6 & S7 & S8 & S9
```

---

## 4. Voice Control Flow

```mermaid
flowchart TD
    IDLE(["IDLE – čekání"])
    HW_DETECT["Detekce hotword\n'hej kundo'"]
    BEEP["Pípnutí + vizuální indikátor"]
    LISTEN["STT Naslouchání\nAndroid SpeechRecognizer"]
    PROCESS["Zpracování příkazu\nPOST /process + kontext"]
    AI_RESP["OpenAI GPT-4o-mini\nNLP zpracování"]
    TTS_RESP["TTS Odpověď\nTextToSpeech"]
    ACTION["Akce v UI / API call"]
    ERROR["Chyba / Timeout"]
    RETRY["Retry s exponential backoff"]

    IDLE -->|"mikrofon aktivní"| HW_DETECT
    HW_DETECT -->|"shoda s hotword"| BEEP
    HW_DETECT -->|"neshoda"| IDLE
    BEEP --> LISTEN
    LISTEN -->|"příkaz rozpoznán"| PROCESS
    LISTEN -->|"silence timeout"| IDLE
    PROCESS --> AI_RESP
    AI_RESP --> TTS_RESP
    TTS_RESP --> ACTION
    ACTION -->|"expectReply=true"| LISTEN
    ACTION -->|"stayIdle=true"| IDLE
    LISTEN -->|"chyba"| ERROR
    ERROR -->|"max retries < 3"| RETRY
    RETRY --> LISTEN
    ERROR -->|"max retries = 3"| IDLE

    subgraph WORKH ["Pracovní Doba"]
        WH_CHECK["workHoursEnabled?"]
        WH_BLOCK["Zablokováno mimo\npracovní dobu"]
    end

    IDLE --> WH_CHECK
    WH_CHECK -->|"mimo pracovní dobu"| WH_BLOCK
    WH_BLOCK --> IDLE
```

---

## 5. Feature Mapa

```mermaid
mindmap
  root((Secretary\nAndroid 1.2a))
    CRM
      Klienti
        Seznam s vyhledáváním
        Detail profilu
        Poznámky
        Servisní sazby
        Sync z kontaktů
      Nemovitosti
        Správa ploch
        Zóny a plochy m2
      Zakázky
        Fotodokumentace
        Úkoly zakázky
        Audit log
      Úkoly
        Prioritizace
        Checklist položky
      Leady
        Konverze na klienta
      Nabídky a Faktury
        Vytváření položek
        Stavy workflow
      Komunikace
        Log telefon / email / SMS
    Hlasové Ovládání
      Hotword detekce
        'hej kundo'
        Normalizace diakritiky
      STT Naslouchání
        Android SpeechRecognizer
        Konfigurace jazyka
      TTS Odpovědi
        Konfigurace hlasu
        Rate a Pitch
      Voice Session
        Multi-step dialogy
        Kontext klient/zakázka
      Pracovní Výkaz Hlasem
        Zadání hodin
        Materiály
        Odpad
      Pracovní Doba
        Automatické blokování
    Nature AI
      Rozpoznání Rostlin
        PlantNet
        Foto upload
        Lokalizace EN/CS/PL
      Nemoci Rostlin
        Plant.id diagnóza
        GPT-4o guidance
      Houby
        Kindwise taxonomie
        Jedlost a nebezpečí
      Historie rozpoznání
    Kalendář
      Google Calendar sync
      Přehled zakázek
      CRUD události
    Kontakty
      Import z telefonu
      Sdílené kontakty firmy
      Sekce kontaktů
    Autentizace
      Email + heslo
      Biometrika
        Otisk prstu
        Rozpoznání obličeje
      Více profilů
      JWT refresh
    Nastavení
      Hlasové ovládání
      Server URL
      CRM výchozí hodnoty
      Notifikace
      Pracovní profil
      Témata LightDarkSystem
      Jazyky CSENPLL
      Správa uživatelů
```

---

## 6. Klíčové Soubory

| Soubor | Balíček | Popis |
|--------|---------|-------|
| `MainActivity.kt` | `com.example.secretary` | Hlavní aktivita, Compose host, navigace, veškerý UI stav (239 KB) |
| `Navigation.kt` | `com.example.secretary` | Sealed class – 9 obrazovek s routami, ikonami, názvy |
| `Models.kt` | `com.example.secretary` | 150+ data class pro API kontrakty |
| `SecretaryApi.kt` | `com.example.secretary` | Retrofit interface, 100+ endpointů |
| `VoiceManager.kt` | `com.example.secretary` | STT/TTS, hotword, state machine, retry logika |
| `VoiceService.kt` | `com.example.secretary` | ForegroundService pro pozadí voice listening |
| `CalendarManager.kt` | `com.example.secretary` | Google Calendar CRUD přes Calendar Provider |
| `ContactManager.kt` | `com.example.secretary` | Import kontaktů z Contacts Provider |
| `SettingsManager.kt` | `com.example.secretary` | SharedPreferences wrapper – 9 kategorií nastavení |
| `SettingsScreen.kt` | `com.example.secretary` | Compose UI pro nastavení (64 KB) |
| `LoginScreen.kt` | `com.example.secretary` | Auth obrazovka – email, heslo, biometrika |
| `OnboardingScreen.kt` | `com.example.secretary` | První spuštění – nastavení firmy |
| `CrmDialogs.kt` | `com.example.secretary` | Modální dialogy pro CRM |
| `Strings.kt` | `com.example.secretary` | Všechny UI texty CS/EN/PL (87 KB) |
| `Theme.kt` | `com.example.secretary.ui.theme` | Material3 + DesignLeaf branding |
| `VersionInfo.kt` | `com.example.secretary` | Verze a changelog |

---

## 7. Android Oprávnění

```mermaid
graph LR
    subgraph MANIFEST ["AndroidManifest.xml – Oprávnění"]
        P1["INTERNET"]
        P2["RECORD_AUDIO"]
        P3["READ_CALENDAR / WRITE_CALENDAR"]
        P4["READ_CONTACTS"]
        P5["ACCESS_COARSE_LOCATION / FINE_LOCATION"]
        P6["MODIFY_AUDIO_SETTINGS"]
        P7["FOREGROUND_SERVICE"]
        P8["POST_NOTIFICATIONS"]
    end

    P1 --> USE1["API volání"]
    P2 --> USE2["Hlasové ovládání"]
    P3 --> USE3["Google Calendar sync"]
    P4 --> USE4["Import kontaktů"]
    P5 --> USE5["Lokalizace (Nature AI)"]
    P6 --> USE6["Muting během nahrávání"]
    P7 --> USE7["VoiceService na pozadí"]
    P8 --> USE8["Notifikace (VoiceService)"]
```

---

## 8. SettingsManager – Kategorie

| Kategorie | Klíčové vlastnosti |
|-----------|--------------------|
| **Hlas** | `hotwordEnabled`, `activationWord`, `recognitionLanguage`, `ttsRate`, `ttsPitch`, `silenceLength` |
| **Server** | `apiUrl`, `offlineMode` |
| **Auth** | `accessToken`, `refreshToken`, `currentBackendUserId`, `currentBackendUserRole` |
| **CRM** | `autoRefreshInterval`, `defaultCrmTab`, `clientSortOrder` |
| **UI** | `appLanguage`, `themeMode` |
| **Notifikace** | `persistentNotification`, `reminderMinutes` |
| **Pracovní Profil** | `workHoursEnabled`, `workHoursStart`, `workHoursEnd`, `emailSignature` |
| **Biometrika** | `BiometricProfile` list (hashed email + password) |
| **Import** | `autoImportContacts`, `cacheSize` |

---

## 9. API Integrace – Skupiny Endpointů

| Skupina | Endpointy | Použití v Androidu |
|---------|-----------|-------------------|
| `/auth/*` | login, refresh, me, change-password, users | LoginScreen, SettingsManager |
| `/crm/clients/*` | CRUD, notes, service-rates, sync-contacts | CRM Tab, Client Detail |
| `/crm/properties/*` | CRUD | Client Detail |
| `/crm/jobs/*` | CRUD, photos, notes, audit | Job Detail |
| `/crm/tasks/*` | CRUD | Tasks Tab |
| `/crm/leads/*` | CRUD, convert-to-client | CRM Tab |
| `/crm/quotes/*` | CRUD + items | CRM Tab |
| `/crm/invoices/*` | CRUD | CRM Tab |
| `/crm/communications/*` | CRUD | Client Detail |
| `/crm/contacts/*` | CRUD, import | ContactsDirectoryTab |
| `/crm/calendar-feed` | GET | Calendar Tab |
| `/work-reports/*` | CRUD + workers/entries/materials/waste | Voice Work Report |
| `/plants/*` | identify, health-assessment | PlantRecognitionTab |
| `/mushrooms/*` | identify | PlantRecognitionTab |
| `/nature/*` | history | PlantRecognitionTab |
| `/voice/*` | session start/respond/end | VoiceManager |
| `/tenant/*` | config, default-rates | SettingsScreen |
| `/health` | GET | Startup check |

---

## 10. Rychlá Reference

| Položka | Hodnota |
|---------|--------|
| App ID | `com.example.secretary` |
| Min SDK | 26 (Android 8) |
| Target SDK | 35 (Android 15) |
| Base URL | `https://web-production-4b451.up.railway.app` |
| Verze | 1.2a (Build 3) |
| Architektura | MVVM + Repository |
| UI Framework | Jetpack Compose + Material3 |
| Sítě | Retrofit2 + OkHttp3 |
| Async | Kotlin Coroutines + StateFlow |
| Auth | JWT Bearer + BiometricPrompt |
| Jazyky | CS / EN / PL |
| Témata | Systém / Světlé / Tmavé |
| Voice | Android SpeechRecognizer + TextToSpeech |
