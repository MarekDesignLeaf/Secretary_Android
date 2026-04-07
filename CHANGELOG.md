# CHANGELOG
# Secretary DesignLeaf
# Kompletni auditni zaznam vsech verzi
# ========================================
# PRAVIDLA: Viz VERSIONING.md
# Novy zaznam vzdy na ZACATEK souboru.
# Nikdy nemazat ani nemenit historicke zaznamy.
# ========================================

## [1.1a] - 2026-03-31
### Theme system, opravy Railway deploy, vylepseni UI
- **Typ**: MINOR (nova funkce)
- **Autor**: Marek Sima
- **Kodovani**: Claude AI (Anthropic)
- **Build**: 2

### Zmeny
- Theme.kt: DesignLeaf brand barvy (zelena/hneda/modra), svetly a tmavy motiv
- SettingsScreen: nova sekce Motiv aplikace (system/svetly/tmavy)
- SettingsManager: themeMode property
- MainActivity: theme cte z nastaveni, barvy z MaterialTheme misto hardcoded
- MainActivity: Retrofit cte URL z settingsManager.apiUrl
- MainActivity: historie chatu zvetsena z 10 na 30 zprav
- VoiceManager: pridano setPitch, kontrola pracovnich hodin
- Models.kt: nullable defaults (prevence crashu)
- SecretaryApi: pridany endpointy invoices, search, export, health
- build.gradle.kts: BASE_URL na Railway production
- Server deploy na Railway (PostgreSQL + FastAPI + GPT-4o)

### Zname problemy
- Inbox je placeholder
- Tasks jen lokalne
- PDF export neni

## [1.0a] - 2026-03-30
### Zakladni vydani s verzovanim
- **Typ**: MAJOR (prvni verzovane vydani)
- **Autor**: Marek Sima (marek@designleaf.co.uk)
- **Kodovani**: Claude AI (Anthropic) pod vedenim Marka Simy
- **Build**: 1

### Soucasti tohoto vydani

#### Jadro aplikace
- MainActivity.kt: Hlavni aktivita, ViewModel, Compose UI, navigace
- Navigation.kt: Screen sealed class, 5 hlavnich tabulek + detail klienta
- Models.kt: Datove tridy (Client, Property, Job, Invoice, WasteLoad, Communication, Task, ChatMessage, MessageRequest, AssistantResponse, ConnectionStatus)

#### Hlasove ovladani
- VoiceManager.kt: STT/TTS, hotword detekce ("hej kundo"), stavovy automat (IDLE/HOTWORD/COMMAND), normalizace diakritiky, konfigurovatelne silence timeouty
- VoiceService.kt: ForegroundService s persistent notifikaci pro beh na pozadi

#### CRM integrace
- SecretaryApi.kt: Retrofit rozhrani (process, clients CRUD, properties, jobs, waste, leads, invoices, communications, search, import, export, settings, health)
- CalendarManager.kt: Plny CRUD Google kalendare (add, update, delete, list, listDetailed, findByTitle)
- ContactManager.kt: Vyhledavani v telefonnim adresari
- MailManager.kt: Odesilani emailu pres intent

#### Nastaveni
- SettingsManager.kt: SharedPreferences, 7 sekci nastaveni, uzivatelske profily s opravnenimi, heslo spravce (SHA-256), ulozene emailove podpisy, import cesty, pracovni hodiny
- SettingsScreen.kt: Compose UI pro vsechna nastaveni vcetne spravy uzivatelu a prav

#### Verzovani
- VersionInfo.kt: Embeddovane verzovaci informace, changelog, pravidla jako kod
- VERSIONING.md: Povinny dokument s pravidly verzovani
- CHANGELOG.md: Tento soubor

#### Server (FastAPI)
- server/main.py: FastAPI server, OpenAI GPT-4o integrace, PostgreSQL connection pooling, CRM endpointy (clients CRUD + search, properties, jobs, waste, leads, invoices, communications, import JSON/CSV, export CSV), AI process s calendar/contacts/email/task tools, datetime awareness
- server/schema.sql: PostgreSQL schema (crm schema, audit log, roles, users, clients, properties, zones, leads, quotes, jobs, tasks, waste, invoices, communications, triggery, indexy)
- server/setup_db.py: Inicializace databaze

#### Manifest a build
- AndroidManifest.xml: INTERNET, RECORD_AUDIO, READ/WRITE_CALENDAR, READ_CONTACTS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS, VoiceService registrace
- build.gradle.kts: compileSdk 35, minSdk 26, targetSdk 35, Compose, Retrofit, OkHttp, Navigation, Material3, Coroutines

### Zname problemy v 1.0a
- Inbox obrazovka je placeholder (zadna funkcionalita)
- Tasks se ukladaji jen lokalne v pameti (ne do DB)
- Offline fronta prikazu neni implementovana (jen prepinac v nastaveni)
- PDF export neni soucasti tohoto vydani
- Auto-import pri spusteni neni aktivni (jen nastaveni)

### Predchozi neverzoovane zmeny (audit)
- 2026-03-26: Vytvoreni projektu v Android Studio, zakladni struktura
- 2026-03-27: CalendarManager, prvni verze VoiceManager s hotword detekci
- 2026-03-28: Server main.py s OpenAI integraci, CRM endpointy
- 2026-03-29: ContactManager, MailManager, SettingsManager, VoiceService, rozsireni UI
- 2026-03-30: Zavedeni verzovani, VersionInfo.kt, kompletni SettingsScreen s uzivatelskymi profily a pravy
