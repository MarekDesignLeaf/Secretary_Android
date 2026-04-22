# Secretary Android – Mapa aplikace

Kotlin + Jetpack Compose klient pro Secretary CRM. Voice-control, AI rozpoznání rostlin, Google Calendar integrace, Android Contacts sync.

---

## 1. Systémová architektura

```mermaid
graph TD
    subgraph Android[Android zařízení]
        UI[Jetpack Compose UI]
        VM[ViewModel<br/>MainViewModel]
        SF[StateFlow<br/>UiState]
        SM[SettingsManager<br/>DataStore]
        VS[VoiceService<br/>ForegroundService]
        CM[ContactManager<br/>Android Contacts]
        CAL[CalendarManager<br/>Google Calendar]
        API_C[SecretaryApi<br/>Retrofit]
    end

    UI <-->|state + events| VM
    VM -->|update| SF
    SF --> UI
    VM --> API_C
    VM --> SM
    VM --> CM
    VM --> CAL
    VM --> VS

    API_C -->|HTTPS + JWT| S[FastAPI Server<br/>Railway]
    S --> DB[(PostgreSQL)]
    S -->|AI| OAI[OpenAI GPT-4o-mini]
    S -->|AI| PN[PlantNet / Plant.id / Kindwise]
    VS -->|STT/TTS| AS[Android SpeechRecognizer<br/>+ TextToSpeech]
    CAL -->|OAuth2| GC[Google Calendar API]
    CM -->|Content Resolver| AC[Android Contacts Provider]

    style UI fill:#3ddc84,color:#000
    style S fill:#4a90e2,color:#fff
    style VS fill:#ff9800,color:#000
```

---

## 2. Architektura aplikace (MVVM)

```mermaid
graph TB
    subgraph View[View – Composable]
        HS[HomeScreen]
        CS[ClientsScreen]
        JS[JobsScreen]
        TS[TasksScreen]
        CAL_S[CalendarScreen]
        TOOLS[ToolsScreen]
        SET[SettingsScreen]
    end

    subgraph VM[ViewModel]
        MVM[MainViewModel<br/>onVoiceInput, startContactUpdate,<br/>refreshCrmData, ...]
        UIS[UiState<br/>clients, jobs, tasks,<br/>isContactUpdateActive, ...]
    end

    subgraph Data[Data layer]
        API[SecretaryApi<br/>Retrofit interface]
        SETTINGS[SettingsManager<br/>Preferences DataStore]
        CACHE[In-memory cache<br/>in UiState]
    end

    subgraph Services[Services]
        VOICE[VoiceService<br/>ForegroundService +<br/>hotword 'hej kundo']
        CONTACT[ContactManager]
        CALMGR[CalendarManager]
        MAIL[MailManager]
    end

    View -->|collectAsState| UIS
    View -->|onEvent| MVM
    MVM --> UIS
    MVM --> API
    MVM --> SETTINGS
    MVM --> Services
    API -->|HTTPS| Remote[(FastAPI)]

    style MVM fill:#4a90e2,color:#fff
    style UIS fill:#ffeb3b,color:#000
```

---

## 3. Navigační flow

```mermaid
flowchart TD
    Start([App start]) --> A{JWT uložen?}
    A -->|Ne| Login[LoginScreen]
    A -->|Ano a validní| Onb{Onboarding<br/>dokončen?}
    Login -->|Úspěch| Onb
    Onb -->|Ne| OnbScreen[OnboardingScreen<br/>tenant + work hours]
    Onb -->|Ano| Home[HomeScreen<br/>Dashboard]
    OnbScreen --> Home

    Home --> Nav[Bottom Navigation]
    Nav --> H[Home]
    Nav --> CRM[CRM]
    Nav --> TSK[Tasks]
    Nav --> CAL[Calendar]
    Nav --> TLS[Tools]
    Nav --> SET[Settings]

    CRM --> CL[Clients list]
    CL --> CD[Client detail]
    CD --> JD[Job detail]
    JD --> TD[Task detail]

    CRM --> PR[Properties]
    CRM --> LD[Leads]
    CRM --> QT[Quotes]
    CRM --> INV[Invoices]
    CRM --> CON[Contacts directory]

    TLS --> PLT[Plant recognition]
    TLS --> MUSH[Mushroom recognition]
    TLS --> VR[Voice reports]

    SET --> PROF[Profile]
    SET --> THM[Theme + language]
    SET --> WH[Work hours]
    SET --> ROL[Roles + permissions]
```

---

## 4. Voice control flow

```mermaid
stateDiagram-v2
    [*] --> IDLE

    IDLE --> LISTENING_HOTWORD: VoiceService start
    LISTENING_HOTWORD --> COMMAND: 'hej kundo'
    LISTENING_HOTWORD --> IDLE: timeout

    COMMAND --> PROCESSING: STT text
    PROCESSING --> PROCESSING: POST /voice/command<br/>GPT-4o-mini
    PROCESSING --> TTS_RESPONSE: odpověď
    TTS_RESPONSE --> LISTENING_HOTWORD: dokončeno

    COMMAND --> CONTACT_UPDATE_MODE: 'aktualizuj kontakty'
    CONTACT_UPDATE_MODE --> CONTACT_SECTION: 'hlasová'
    CONTACT_UPDATE_MODE --> IDLE: 'manuální' → otevřít UI

    CONTACT_SECTION --> NEXT_CONTACT: sekce rozpoznána
    CONTACT_SECTION --> CONTACT_SECTION: skip / delete / retry
    NEXT_CONTACT --> CONTACT_SECTION: další kontakt
    NEXT_CONTACT --> IDLE: dokončeno

    COMMAND --> WORK_REPORT_FLOW: 'nahrát výkaz'
    WORK_REPORT_FLOW --> WORK_REPORT_FLOW: pracovník/materiál/...
    WORK_REPORT_FLOW --> LISTENING_HOTWORD: dokončeno
```

### Voice contact update – 2-step state machine

```mermaid
flowchart LR
    Start[startContactUpdate] --> Ask[voiceOrManualUpdate TTS]
    Ask --> Mode{handleContactUpdateModeSelection}
    Mode -->|hlasová| Speak[speakCurrentContactUpdate]
    Mode -->|manuální| OpenUI[openContactsManually]

    Speak --> Sec[Step: section]
    Sec --> SecResp{handleContactUpdateSectionResponse}
    SecResp -->|skip| Advance[advanceContactUpdate]
    SecResp -->|delete| Advance
    SecResp -->|match| NextQ[nextContactQuestion TTS]

    NextQ --> Next[Step: next_contact]
    Next --> NextResp{handleContactUpdateNextContactResponse<br/>parseRelativeDate + parseContactMethod}
    NextResp -->|save API| Advance

    Advance --> More{další kontakt?}
    More -->|ano| Speak
    More -->|ne| Finish[finishContactUpdate<br/>refreshCrmDataKeepTasks]
```

---

## 5. Klíčové soubory

| Soubor | Odpovědnost |
|--------|-------------|
| `MainActivity.kt` | MainViewModel, UiState, onVoiceInput, voice state machines, CRM orchestration |
| `Models.kt` | Data classes: Client, Job, Task, SharedContact, ContactSection, ... |
| `Navigation.kt` | Compose NavHost, screen routes |
| `SecretaryApi.kt` | Retrofit interface – HTTP endpointy |
| `SettingsManager.kt` | DataStore – preferences, theme, language, work hours |
| `VoiceService.kt` | ForegroundService, hotword detection |
| `VoiceManager.kt` | STT/TTS wrapper |
| `ContactManager.kt` | Android Contacts Provider (read + write) |
| `CalendarManager.kt` | Google Calendar API OAuth + sync |
| `MailManager.kt` | SMTP odeslání přes Intent |
| `LoginScreen.kt` | Email + password → JWT |
| `OnboardingScreen.kt` | První nastavení tenantu |
| `SettingsScreen.kt` | Nastavení – profil, téma, role |
| `ContactsDirectoryTab.kt` | Seznam sdílených kontaktů, hierarchické sekce, next_contact coloring |
| `PlantRecognitionTab.kt` | Rostliny + houby – foto upload |
| `CrmDialogs.kt` | Dialogy pro CRUD operace |
| `ClientServiceRatesDialog.kt` | Individuální sazby klienta |
| `Strings.kt` | i18n (cs/en/pl), voice command matchers |

---

## 6. Feature mapa

```mermaid
mindmap
    root((Secretary<br/>Android))
        CRM
            Klienti
                Detail + poznámky
                Nemovitosti
                Service rates
            Zakázky
                Úkoly
                Fotky
                Výkazy
            Leady
                Konverze na klienta
            Nabídky + faktury
            Komunikace
        Voice Control
            Hotword 'hej kundo'
            STT + TTS
            Hlasové příkazy GPT
            Voice work reports
            Voice contact update
                Section assignment
                Next contact scheduling
                Relative date parser
        Nature AI
            Rozpoznání rostlin
            Nemoci rostlin
            Houby
            Historie
        Kalendář
            Google Calendar OAuth
            Sync zakázek
            Přehled dne/týdne
        Kontakty
            Import z telefonu
            Sdílené kontakty
            Hierarchické sekce
                Zákazníci aktivní/stálí/ztracení/potenciální
                Dodavatelé materiál/půjčovna
                Zaměstnanci
                Subkontraktoři
            Color-coded next contact
        Nastavení
            Profil uživatele
            Role + oprávnění
            Téma světlé/tmavé
            Jazyk cs/en/pl
            Pracovní doba + timezone
```

---

## 7. Data flow – typická akce (vytvoření zakázky)

```mermaid
sequenceDiagram
    participant U as User
    participant UI as JobsScreen
    participant VM as MainViewModel
    participant API as SecretaryApi
    participant S as FastAPI
    participant DB as PostgreSQL

    U->>UI: Tap "Nová zakázka"
    UI->>VM: onCreateJob(payload)
    VM->>VM: update UiState.isLoading=true
    VM->>API: POST /crm/jobs
    API->>S: HTTPS + Bearer JWT
    S->>S: verify JWT, permission check
    S->>DB: INSERT INTO jobs
    DB-->>S: new job row
    S-->>API: 201 + Job JSON
    API-->>VM: Job
    VM->>VM: UiState.jobs += newJob
    VM->>VM: isLoading=false
    UI-->>U: re-compose with new job
```

---

## 8. Voice contact update – detailní interakce

```mermaid
sequenceDiagram
    participant U as User
    participant VS as VoiceService
    participant VM as MainViewModel
    participant TTS as TextToSpeech
    participant API as SecretaryApi

    U->>VS: "hej kundo, aktualizuj kontakty"
    VS->>VM: onVoiceInput
    VM->>VM: matchesUpdateContactsCommand ✓
    VM->>VM: startContactUpdate
    VM->>TTS: "Manuální nebo hlasová aktualizace?"

    U->>VS: "hlasová"
    VS->>VM: onVoiceInput
    VM->>VM: handleContactUpdateModeSelection
    VM->>TTS: speakCurrentContactUpdate (jméno, telefon)
    VM->>TTS: "Řekni sekci, skip nebo smazat"

    U->>VS: "aktivní zákazníci"
    VS->>VM: handleContactUpdateSectionResponse
    VM->>VM: match 'zakaznici_aktivni'
    VM->>VM: step = 'next_contact'
    VM->>TTS: "Kdy a jak příště kontaktovat?"

    U->>VS: "za týden telefonem"
    VS->>VM: handleContactUpdateNextContactResponse
    VM->>VM: parseRelativeDate → +7d<br/>parseContactMethod → 'phone'
    VM->>API: PUT /crm/contacts/{id}<br/>{section, next_contact_at, next_contact_method}
    API-->>VM: 200 OK
    VM->>VM: advanceContactUpdate → další kontakt

    Note over U,API: Opakuje se dokud queue není prázdný
    VM->>VM: finishContactUpdate → refreshCrmDataKeepTasks
    VM->>TTS: "Aktualizace kontaktů dokončena."
```
