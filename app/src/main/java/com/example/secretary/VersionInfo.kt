package com.example.secretary

/**
 * ============================================================
 * POVINNY SOUBOR - VERZOVANI SECRETARY DESIGNLEAF
 * ============================================================
 *
 * KAZDY VYVOJAR MUSI PRED JAKOUKOLI ZMENOU KODU:
 * 1. Precist VERSIONING.md v rootu projektu
 * 2. Urcit typ zmeny (patch/minor/major)
 * 3. PO DOKONCENI aktualizovat tento soubor:
 *    - VERSION_NAME: nova verze
 *    - VERSION_CODE: +1
 *    - CHANGELOG: novy zaznam na zacatek seznamu
 * 4. Aktualizovat CHANGELOG.md ve stejnem formatu
 *
 * VERZOVACI SCHEMA:
 *   Patch (pismeno):  1.0a -> 1.0b  (jen bugfix, zadne nove funkce)
 *   Minor (cislo):    1.0a -> 1.1a  (nova funkce, nove UI, nove endpointy)
 *   Major (cislo):    1.0a -> 2.0a  (zmena architektury, breaking changes)
 *
 * PRAVIDLA:
 *   - Nikdy nemazat historicke zaznamy
 *   - Vzdy zachovat predchozi verzi
 *   - Jedna minimalni zmena najednou
 *   - Pracovat jen v reverziblnich krocich
 *   - Nepridavat vylepseni bez explicitniho souhlasu
 *
 * ============================================================
 */
object VersionInfo {

    // === AKTUALNI VERZE ===
    const val VERSION_NAME = "1.3a"
    const val VERSION_CODE = 4
    const val BUILD_DATE = "2026-05-04"

    // === METADATA PROJEKTU ===
    const val APP_NAME = "Secretary DesignLeaf"
    const val APP_DESCRIPTION = "Hlasem ovladany CRM asistent pro zahradnicke sluzby"
    const val PACKAGE_NAME = "com.example.secretary"

    // === AUTOR ===
    const val AUTHOR_NAME = "Marek Sima"
    const val AUTHOR_EMAIL = "marek@designleaf.co.uk"
    const val AUTHOR_PHONE = "+447395813008"
    const val AUTHOR_WEB = "www.designleaf.co.uk"
    const val COMPANY = "DesignLeaf"

    // === TECHNICKE INFO ===
    const val PLATFORM = "Android (Kotlin, Jetpack Compose)"
    const val BACKEND = "FastAPI + PostgreSQL + GPT-4o"
    const val ARCHITECTURE = "MVVM + Retrofit + ForegroundService"
    const val MIN_SDK = 26
    const val TARGET_SDK = 35
    const val AI_ENGINE = "OpenAI GPT-4o via tool calling"
    const val DB_ENGINE = "PostgreSQL s CRM schematem"

    // === DATA SOURCES / ZDROJE DAT ===
    const val REPO_ANDROID = "https://github.com/MarekDesignLeaf/Secretary_Android"
    const val REPO_SERVER = "https://github.com/MarekDesignLeaf/Secretary_Server"
    const val POSTGRES_URL = "postgresql://postgres:dvgmddHlRimErVhaYTFWSMtKKYeuJGMG@hopper.proxy.rlwy.net:33530/railway"
    const val WEB_PRODUCTION = "https://web-production-4b451.up.railway.app"

    // === LICENCE ===
    const val LICENSE = "Copyright 2024-2026 Marek Sima / DesignLeaf. " +
            "Vsechna prava vyhrazena. Proprietarni software, " +
            "neni urcen k distribuci bez pisemneho souhlasu autora."

    // === PRVNI VYDANI ===
    const val FIRST_RELEASE_DATE = "2026-03-30"
    const val FIRST_RELEASE_VERSION = "1.0a"

    // === STRUKTURA PROJEKTU ===
    val PROJECT_STRUCTURE = listOf(
        FileInfo("MainActivity.kt", "Hlavni aktivita, ViewModel, Compose UI obrazovky, navigace"),
        FileInfo("VoiceManager.kt", "STT/TTS, hotword detekce, stavovy automat IDLE/HOTWORD/COMMAND"),
        FileInfo("VoiceService.kt", "ForegroundService pro beh hlasoveho ovladani na pozadi"),
        FileInfo("CalendarManager.kt", "CRUD operace Google kalendare (add/update/delete/list)"),
        FileInfo("ContactManager.kt", "Vyhledavani v telefonnim adresari Android"),
        FileInfo("MailManager.kt", "Odesilani emailu pres Android intent"),
        FileInfo("SettingsManager.kt", "SharedPreferences, profily, prava, podpisy, import"),
        FileInfo("SettingsScreen.kt", "UI nastaveni: hlas, server, CRM, notifikace, profil, uzivatele, data, about"),
        FileInfo("SecretaryApi.kt", "Retrofit API rozhrani k FastAPI serveru"),
        FileInfo("Models.kt", "Datove tridy: CRM, chat, import, connection status"),
        FileInfo("Navigation.kt", "Navigacni trasy a menu polozky"),
        FileInfo("VersionInfo.kt", "Tento soubor: verzovani, changelog, metadata"),
        FileInfo("server/main.py", "FastAPI server: AI, CRM, import/export, connection pool"),
        FileInfo("server/schema.sql", "PostgreSQL databazove schema (91 tabulek)"),
        FileInfo("server/setup_db.py", "Inicializacni skript databaze"),
        FileInfo("VERSIONING.md", "Povinny dokument: pravidla verzovani"),
        FileInfo("CHANGELOG.md", "Kompletni auditni zaznam vsech verzi")
    )

    // ============================================================
    // CHANGELOG
    // Novy zaznam vzdy na ZACATEK seznamu.
    // Nikdy nemazat ani nemenit existujici zaznamy.
    // ============================================================

    val CHANGELOG: List<ChangelogEntry> = listOf(

        // --- AKTUALNI VERZE ---

        ChangelogEntry(
            version = "1.3a",
            versionCode = 4,
            date = "2026-05-04",
            author = "Marek Sima",
            coder = "Claude AI (Anthropic) pod vedenim Marka Simy",
            type = ChangeType.MINOR,
            summary = "Kontakty CRM, hlas. rozlisovani, unifikovana akcni architektura, server audit",
            changes = listOf(
                "MainActivity: hlas. rozlisovani (disambiguation UI), potvrzovaci dialog pro vice shod",
                "MainActivity: nova obrazovka ToolsHubScreen, dynamicke balicky nastroju",
                "MainActivity: odstraneni mrtveho kodu (callVoiceResolve, _voiceResolveFallback)",
                "MainActivity: oprava typu WhatsAppVoiceCommand v startVoiceWhatsApp",
                "MainActivity: ikony AutoMirrored (ExitToApp, Send) - oprava deprecated API",
                "VoiceManager: prepracovani hlasoveho resolveru, integrace /voice/resolve endpointu",
                "VoiceManager: cache effectiveVoiceAliases + knownVoiceNameSet (oprava GC storm)",
                "WakeWordEngine: konfigurovatelne aktivacni slovo (hej designleaf), per-user scope",
                "BargeInDetector.kt: novy soubor - detekce prerušeni hlasoveho vstupu",
                "ImportScreen.kt: novy soubor - UI pro CSV/JSON import kontaktu a dat",
                "ToolPackagesScreen.kt: novy soubor - UI pro instalaci a spravu tool balicku",
                "SecretaryApi.kt: nova API volani (voice/resolve, voice/context, actions/execute, import, tool-packages)",
                "SettingsScreen: LaunchedEffect pro nacteni tenant configu pri otevreni obrazovky",
                "SettingsScreen: ikona AutoMirrored.List v sekci verzi",
                "SettingsManager: rozsireni pro per-user scoped nastaveni",
                "Strings.kt: rozsireni prekladu (CS/EN/PL) pro vsechny nove funkce",
                "ContactsDirectoryTab: razeni kontaktu z crm.contacts, admin-only razeni",
                "Server: cache bypass pro GET /tenant/config (vzdy cerstva data)",
                "Server: opravy audit traceback a by_role dict pristupu",
                "Server: bezpecna migrace tenant konfigurace (017c)",
                "Server: explicitni runtime dependencies",
                "DB: 001_full_repair.sql aktualizovan pro migrace 011-017c"
            ),
            knownIssues = listOf(
                "PATH pro tool balicky neni nastaven systemove",
                "Offline fronta prikazu neni implementovana",
                "PDF export neni implementovan"
            )
        ),

        ChangelogEntry(
            version = "1.2a",
            versionCode = 3,
            date = "2026-04-15",
            author = "Marek Sima",
            coder = "Claude AI (Anthropic) pod vedenim Marka Simy",
            type = ChangeType.MINOR,
            summary = "Hlas. aliasy, WhatsApp Meta API, DB migrace 011-013, tool a import system",
            changes = listOf(
                "VoiceManager: hlas. aliasy jsou globalni (sdilene mezi uzivateli)",
                "VoiceManager: normalizace hlas. prikazu - oprava memory leaku a GC storm",
                "WhatsApp: odesilani zprav pres Meta Cloud API (server-side)",
                "WakeWordEngine: aktivacni slovo cte globalni hodnotu pokud je scoped = default",
                "SettingsManager: aktivacni slovo jako per-user scoped nastaveni",
                "Migration 011: tabulky tool package systemu (tool_registry, tool_packages)",
                "Migration 012: tool_registry - centralni registr nastroju",
                "Migration 013: language_catalog - katalog prekladu a jazyk. nastaveni",
                "Android barge-in: detekce prerušeni behem TTS prehravani",
                "Migration 014: import system (import_jobs, import_mappings)",
                "data_importer.py: import CSV/JSON kontaktu, skript pro server",
                "Endpointy /import/* na FastAPI serveru",
                "Migration 015: tenant_settings - univerzalizace (odstraneni hardcoded DesignLeaf)",
                "main.py: odstraneni vsech hardcoded landscaping/DesignLeaf referenci",
                "Migration 016: Unified Action Architecture (action_registry, action_log)",
                "action_executor.py: unifikovany handler pro hlas/UI/API akce",
                "Migration 017: UI Control Registry + Voice Bridge (ui_control_registry, voice_bridge_log)",
                "ai_control_bridge.py: voice resolver + AI synonym generator",
                "Migration 017c: contact_voice_aliases tabulka + entity resolver",
                "POST /actions/execute + /voice/resolve endpointy"
            ),
            knownIssues = listOf(
                "Inbox obrazovka je placeholder",
                "Tasks jen v lokalni pameti",
                "PDF export neni implementovan"
            )
        ),

        ChangelogEntry(
            version = "1.1a",
            versionCode = 2,
            date = "2026-03-31",
            author = "Marek Sima",
            coder = "Claude AI (Anthropic) pod vedenim Marka Simy",
            type = ChangeType.MINOR,
            summary = "Theme system, opravy Railway deploy, vylepšení UI",
            changes = listOf(
                "Theme.kt: DesignLeaf brand barvy (zelena/hneda/modra), svetly a tmavy motiv",
                "SettingsScreen: nova sekce Motiv aplikace (system/svetly/tmavy)",
                "SettingsManager: themeMode property",
                "MainActivity: theme cte z nastaveni, barvy z MaterialTheme misto hardcoded",
                "MainActivity: Retrofit cte URL z settingsManager.apiUrl",
                "MainActivity: historie chatu zvetsena z 10 na 30 zprav",
                "VoiceManager: pridano setPitch pro vysku hlasu, kontrola pracovnich hodin",
                "Models.kt: vsechny nullable pole maji default hodnoty (prevence crashu)",
                "SecretaryApi: pridany endpointy invoices, search, export, health",
                "build.gradle.kts: BASE_URL zmenena na Railway production URL",
                "Server deploy na Railway (PostgreSQL + FastAPI + GPT-4o)"
            ),
            knownIssues = listOf(
                "Inbox obrazovka je placeholder",
                "Tasks jen v lokalni pameti",
                "PDF export neni implementovan"
            )
        ),

        ChangelogEntry(
            version = "1.0a",
            versionCode = 1,
            date = "2026-03-30",
            author = "Marek Sima",
            coder = "Claude AI (Anthropic) pod vedenim Marka Simy",
            type = ChangeType.MAJOR,
            summary = "Prvni verzovane vydani Secretary DesignLeaf",
            changes = listOf(
                "Zavedeni verzovaci infrastruktury (VersionInfo.kt, VERSIONING.md, CHANGELOG.md)",
                "MainActivity: MVVM architektura, Compose UI, 5 hlavnich obrazovek + detail klienta",
                "VoiceManager: hotword detekce 'hej kundo', STT/TTS, stavovy automat, konfigurovatelna rychlost/vyska hlasu",
                "VoiceService: ForegroundService s persistent notifikaci",
                "CalendarManager: plny CRUD (add, update, delete, list, findByTitle)",
                "ContactManager: vyhledavani v telefonnim adresari",
                "MailManager: odesilani emailu s konfigurovatelnym podpisem",
                "SecretaryApi: kompletni Retrofit rozhrani (CRM, AI, import, export, health)",
                "SettingsScreen: 8 skladacich sekci (hlas, server, CRM, notifikace, profil, uzivatele, data, about)",
                "SettingsManager: uzivatelske profily s opravnenimi, heslo spravce SHA-256, vicero emailovych podpisu",
                "Server: FastAPI + GPT-4o + PostgreSQL connection pool, CRM search, import CSV/JSON, export CSV",
                "Server: datetime awareness (AI zna aktualni cas), calendar/contacts/email/task tools",
                "Models: nullable defaults pro bezpecnou deserializaci, ConnectionStatus enum"
            ),
            knownIssues = listOf(
                "Inbox obrazovka je placeholder",
                "Tasks se ukladaji jen lokalne v pameti",
                "Offline fronta prikazu neni implementovana",
                "PDF export neni soucasti tohoto vydani",
                "Auto-import pri spusteni neni aktivni"
            )
        )

        // --- BUDOUCI VERZE PRIDAT SEM NAHORU ---
    )

    // ============================================================
    // VERZOVACI PRAVIDLA (jako kod, pro programaticky pristup)
    // ============================================================

    val VERSIONING_RULES = listOf(
        VersionRule("PATCH", "1.0a -> 1.0b", "Pouze opravy chyb. Zadne nove funkce, zadne zmeny API."),
        VersionRule("MINOR", "1.0a -> 1.1a", "Nove funkce, rozsireni UI, nove endpointy, nove DB tabulky."),
        VersionRule("MAJOR", "1.0a -> 2.0a", "Zmena architektury, breaking changes, migrace DB, prepis komponent.")
    )

    val MANDATORY_STEPS = listOf(
        "1. Precist VERSIONING.md",
        "2. Precist CHANGELOG.md",
        "3. Precist VersionInfo.kt",
        "4. Urcit typ zmeny (patch/minor/major)",
        "5. Urcit nove cislo verze",
        "6. Provest zmeny v kodu",
        "7. Aktualizovat VersionInfo.kt (VERSION_NAME, VERSION_CODE, CHANGELOG)",
        "8. Aktualizovat CHANGELOG.md",
        "9. Aktualizovat build.gradle.kts"
    )

    // === POMOCNE FUNKCE ===

    fun getVersionDisplay(): String = "$APP_NAME v$VERSION_NAME (build $VERSION_CODE)"

    fun getLatestChanges(): ChangelogEntry? = CHANGELOG.firstOrNull()

    fun appDescription(): String = Strings.t(
        "Voice-controlled CRM assistant for gardening services",
        "Hlasem ovládaný CRM asistent pro zahradnické služby",
        "Sterowany głosem asystent CRM dla usług ogrodniczych"
    )

    fun platformValue(): String = PLATFORM

    fun backendValue(): String = BACKEND

    fun architectureValue(): String = ARCHITECTURE

    fun aiEngineValue(): String = Strings.t(
        "OpenAI GPT-4o via tool calling",
        "OpenAI GPT-4o přes tool calling",
        "OpenAI GPT-4o przez tool calling"
    )

    fun dbEngineValue(): String = Strings.t(
        "PostgreSQL with CRM schema",
        "PostgreSQL s CRM schématem",
        "PostgreSQL ze schematem CRM"
    )

    fun licenseText(): String = Strings.t(
        "Copyright 2024-2026 Marek Sima / DesignLeaf. All rights reserved. Proprietary software, not intended for distribution without the author's written consent.",
        "Copyright 2024-2026 Marek Sima / DesignLeaf. Všechna práva vyhrazena. Proprietární software, není určen k distribuci bez písemného souhlasu autora.",
        "Copyright 2024-2026 Marek Sima / DesignLeaf. Wszelkie prawa zastrzeżone. Oprogramowanie własnościowe, nieprzeznaczone do dystrybucji bez pisemnej zgody autora."
    )

    fun localizeProjectDescription(filename: String, fallback: String): String = when (filename) {
        "MainActivity.kt" -> Strings.t("Main activity, ViewModel, Compose UI screens, navigation", "Hlavní aktivita, ViewModel, Compose UI obrazovky, navigace", "Główna aktywność, ViewModel, ekrany Compose UI, nawigacja")
        "VoiceManager.kt" -> Strings.t("STT/TTS, wake-word detection, IDLE/HOTWORD/COMMAND state machine", "STT/TTS, detekce aktivačního slova, stavový automat IDLE/HOTWORD/COMMAND", "STT/TTS, wykrywanie słowa aktywującego, automat stanów IDLE/HOTWORD/COMMAND")
        "VoiceService.kt" -> Strings.t("ForegroundService for background voice control", "ForegroundService pro běh hlasového ovládání na pozadí", "ForegroundService do działania sterowania głosem w tle")
        "CalendarManager.kt" -> Strings.t("CRUD operations for Google Calendar (add/update/delete/list)", "CRUD operace Google kalendáře (add/update/delete/list)", "Operacje CRUD dla Kalendarza Google (add/update/delete/list)")
        "ContactManager.kt" -> Strings.t("Search in Android phone contacts", "Vyhledávání v telefonním adresáři Android", "Wyszukiwanie w kontaktach telefonu Android")
        "MailManager.kt" -> Strings.t("Send emails through Android intent", "Odesílání emailů přes Android intent", "Wysyłanie emaili przez Android intent")
        "SettingsManager.kt" -> Strings.t("SharedPreferences, profiles, rights, signatures, import", "SharedPreferences, profily, práva, podpisy, import", "SharedPreferences, profile, uprawnienia, podpisy, import")
        "SettingsScreen.kt" -> Strings.t("Settings UI: voice, server, CRM, notifications, profile, users, data, about", "UI nastavení: hlas, server, CRM, notifikace, profil, uživatelé, data, o aplikaci", "UI ustawień: głos, serwer, CRM, powiadomienia, profil, użytkownicy, dane, o aplikacji")
        "SecretaryApi.kt" -> Strings.t("Retrofit API interface for FastAPI server", "Retrofit API rozhraní k FastAPI serveru", "Interfejs API Retrofit do serwera FastAPI")
        "Models.kt" -> Strings.t("Data classes: CRM, chat, import, connection status", "Datové třídy: CRM, chat, import, stav připojení", "Klasy danych: CRM, chat, import, stan połączenia")
        "Navigation.kt" -> Strings.t("Navigation routes and menu items", "Navigační trasy a položky menu", "Trasy nawigacji i pozycje menu")
        "VersionInfo.kt" -> Strings.t("This file: versioning, changelog, metadata", "Tento soubor: verzování, changelog, metadata", "Ten plik: wersjonowanie, changelog, metadane")
        "server/main.py" -> Strings.t("FastAPI server: AI, CRM, import/export, connection pool", "FastAPI server: AI, CRM, import/export, connection pool", "Serwer FastAPI: AI, CRM, import/eksport, pula połączeń")
        "server/schema.sql" -> Strings.t("PostgreSQL database schema (91 tables)", "PostgreSQL databázové schéma (91 tabulek)", "Schemat bazy PostgreSQL (91 tabel)")
        "server/setup_db.py" -> Strings.t("Database initialization script", "Inicializační skript databáze", "Skrypt inicjalizacji bazy danych")
        "VERSIONING.md" -> Strings.t("Mandatory document: versioning rules", "Povinný dokument: pravidla verzování", "Obowiązkowy dokument: zasady wersjonowania")
        "CHANGELOG.md" -> Strings.t("Complete audit record of all versions", "Kompletní auditní záznam všech verzí", "Pełny zapis audytowy wszystkich wersji")
        else -> fallback
    }

    fun localizeVersionRuleDescription(type: String, fallback: String): String = when (type.uppercase()) {
        "PATCH" -> Strings.t("Bug fixes only. No new features and no API changes.", "Pouze opravy chyb. Žádné nové funkce a žádné změny API.", "Tylko poprawki błędów. Bez nowych funkcji i bez zmian API.")
        "MINOR" -> Strings.t("New features, UI extensions, new endpoints, new DB tables.", "Nové funkce, rozšíření UI, nové endpointy, nové DB tabulky.", "Nowe funkcje, rozszerzenia UI, nowe endpointy, nowe tabele DB.")
        "MAJOR" -> Strings.t("Architecture change, breaking changes, DB migration, component rewrite.", "Změna architektury, breaking changes, migrace DB, přepis komponent.", "Zmiana architektury, breaking changes, migracja DB, przepisanie komponentów.")
        else -> fallback
    }

    fun localizeMandatoryStep(step: String): String = when (step) {
        "1. Precist VERSIONING.md" -> Strings.t("1. Read VERSIONING.md", "1. Přečíst VERSIONING.md", "1. Przeczytać VERSIONING.md")
        "2. Precist CHANGELOG.md" -> Strings.t("2. Read CHANGELOG.md", "2. Přečíst CHANGELOG.md", "2. Przeczytać CHANGELOG.md")
        "3. Precist VersionInfo.kt" -> Strings.t("3. Read VersionInfo.kt", "3. Přečíst VersionInfo.kt", "3. Przeczytać VersionInfo.kt")
        "4. Urcit typ zmeny (patch/minor/major)" -> Strings.t("4. Determine the change type (patch/minor/major)", "4. Určit typ změny (patch/minor/major)", "4. Określić typ zmiany (patch/minor/major)")
        "5. Urcit nove cislo verze" -> Strings.t("5. Determine the new version number", "5. Určit nové číslo verze", "5. Określić nowy numer wersji")
        "6. Provest zmeny v kodu" -> Strings.t("6. Implement the code changes", "6. Provést změny v kódu", "6. Wprowadzić zmiany w kodzie")
        "7. Aktualizovat VersionInfo.kt (VERSION_NAME, VERSION_CODE, CHANGELOG)" -> Strings.t("7. Update VersionInfo.kt (VERSION_NAME, VERSION_CODE, CHANGELOG)", "7. Aktualizovat VersionInfo.kt (VERSION_NAME, VERSION_CODE, CHANGELOG)", "7. Zaktualizować VersionInfo.kt (VERSION_NAME, VERSION_CODE, CHANGELOG)")
        "8. Aktualizovat CHANGELOG.md" -> Strings.t("8. Update CHANGELOG.md", "8. Aktualizovat CHANGELOG.md", "8. Zaktualizować CHANGELOG.md")
        "9. Aktualizovat build.gradle.kts" -> Strings.t("9. Update build.gradle.kts", "9. Aktualizovat build.gradle.kts", "9. Zaktualizować build.gradle.kts")
        else -> step
    }

    fun localizeCoder(coder: String): String = when (coder) {
        "Claude AI (Anthropic) pod vedenim Marka Simy" -> Strings.t(
            "Claude AI (Anthropic) under Marek Sima's direction",
            "Claude AI (Anthropic) pod vedením Marka Simy",
            "Claude AI (Anthropic) pod kierunkiem Marka Simy"
        )
        else -> coder
    }

    fun localizeChangelogSummary(summary: String): String = when (summary) {
        "Kontakty CRM, hlas. rozlisovani, unifikovana akcni architektura, server audit" -> Strings.t("CRM contacts, voice disambiguation, unified action architecture, server audit", "Kontakty CRM, hlasové rozlišování, unifikovaná akční architektura, server audit", "Kontakty CRM, rozróżnianie głosowe, zunifikowana architektura akcji, audyt serwera")
        "Hlas. aliasy, WhatsApp Meta API, DB migrace 011-013, tool a import system" -> Strings.t("Voice aliases, WhatsApp Meta API, DB migrations 011-013, tool and import system", "Hlasové aliasy, WhatsApp Meta API, DB migrace 011-013, tool a import systém", "Aliasy głosowe, WhatsApp Meta API, migracje DB 011-013, system narzędzi i importu")
        "Theme system, opravy Railway deploy, vylepšení UI" -> Strings.t("Theme system, Railway deploy fixes, UI improvements", "Theme systém, opravy Railway deploye, vylepšení UI", "System motywów, poprawki wdrożenia Railway, ulepszenia UI")
        "Prvni verzovane vydani Secretary DesignLeaf" -> Strings.t("First versioned release of Secretary DesignLeaf", "První verzované vydání Secretary DesignLeaf", "Pierwsze wersjonowane wydanie Secretary DesignLeaf")
        else -> summary
    }

    fun localizeChangelogChange(change: String): String = when (change) {
        "Theme.kt: DesignLeaf brand barvy (zelena/hneda/modra), svetly a tmavy motiv" -> Strings.t("Theme.kt: DesignLeaf brand colors (green/brown/blue), light and dark theme", "Theme.kt: DesignLeaf brand barvy (zelená/hnědá/modrá), světlý a tmavý motiv", "Theme.kt: kolory marki DesignLeaf (zielony/brązowy/niebieski), jasny i ciemny motyw")
        "SettingsScreen: nova sekce Motiv aplikace (system/svetly/tmavy)" -> Strings.t("SettingsScreen: new App Theme section (system/light/dark)", "SettingsScreen: nová sekce Motiv aplikace (systém/světlý/tmavý)", "SettingsScreen: nowa sekcja Motyw aplikacji (system/jasny/ciemny)")
        "SettingsManager: themeMode property" -> Strings.t("SettingsManager: themeMode property", "SettingsManager: vlastnost themeMode", "SettingsManager: właściwość themeMode")
        "MainActivity: theme cte z nastaveni, barvy z MaterialTheme misto hardcoded" -> Strings.t("MainActivity: theme read from settings, colors taken from MaterialTheme instead of hardcoded values", "MainActivity: motiv se čte z nastavení, barvy z MaterialTheme místo hardcoded hodnot", "MainActivity: motyw odczytywany z ustawień, kolory z MaterialTheme zamiast hardcoded wartości")
        "MainActivity: Retrofit cte URL z settingsManager.apiUrl" -> Strings.t("MainActivity: Retrofit reads URL from settingsManager.apiUrl", "MainActivity: Retrofit čte URL z settingsManager.apiUrl", "MainActivity: Retrofit odczytuje URL z settingsManager.apiUrl")
        "MainActivity: historie chatu zvetsena z 10 na 30 zprav" -> Strings.t("MainActivity: chat history increased from 10 to 30 messages", "MainActivity: historie chatu zvětšena z 10 na 30 zpráv", "MainActivity: historia czatu zwiększona z 10 do 30 wiadomości")
        "VoiceManager: pridano setPitch pro vysku hlasu, kontrola pracovnich hodin" -> Strings.t("VoiceManager: added setPitch for voice pitch and work-hours checks", "VoiceManager: přidáno setPitch pro výšku hlasu a kontrolu pracovních hodin", "VoiceManager: dodano setPitch dla wysokości głosu i kontrolę godzin pracy")
        "Models.kt: vsechny nullable pole maji default hodnoty (prevence crashu)" -> Strings.t("Models.kt: all nullable fields have default values (crash prevention)", "Models.kt: všechna nullable pole mají default hodnoty (prevence crashů)", "Models.kt: wszystkie pola nullable mają wartości domyślne (zapobieganie crashom)")
        "SecretaryApi: pridany endpointy invoices, search, export, health" -> Strings.t("SecretaryApi: added invoices, search, export and health endpoints", "SecretaryApi: přidány endpointy invoices, search, export, health", "SecretaryApi: dodano endpointy invoices, search, export, health")
        "build.gradle.kts: BASE_URL zmenena na Railway production URL" -> Strings.t("build.gradle.kts: BASE_URL changed to Railway production URL", "build.gradle.kts: BASE_URL změněna na Railway production URL", "build.gradle.kts: BASE_URL zmieniony na produkcyjny URL Railway")
        "Server deploy na Railway (PostgreSQL + FastAPI + GPT-4o)" -> Strings.t("Server deployed to Railway (PostgreSQL + FastAPI + GPT-4o)", "Server nasazen na Railway (PostgreSQL + FastAPI + GPT-4o)", "Serwer wdrożony na Railway (PostgreSQL + FastAPI + GPT-4o)")
        "Zavedeni verzovaci infrastruktury (VersionInfo.kt, VERSIONING.md, CHANGELOG.md)" -> Strings.t("Introduced versioning infrastructure (VersionInfo.kt, VERSIONING.md, CHANGELOG.md)", "Zavedena verzovací infrastruktura (VersionInfo.kt, VERSIONING.md, CHANGELOG.md)", "Wprowadzono infrastrukturę wersjonowania (VersionInfo.kt, VERSIONING.md, CHANGELOG.md)")
        "MainActivity: MVVM architektura, Compose UI, 5 hlavnich obrazovek + detail klienta" -> Strings.t("MainActivity: MVVM architecture, Compose UI, 5 main screens + client detail", "MainActivity: MVVM architektura, Compose UI, 5 hlavních obrazovek + detail klienta", "MainActivity: architektura MVVM, Compose UI, 5 głównych ekranów + szczegóły klienta")
        "VoiceManager: hotword detekce 'hej kundo', STT/TTS, stavovy automat, konfigurovatelna rychlost/vyska hlasu" -> Strings.t("VoiceManager: wake-word detection, STT/TTS, state machine, configurable speech rate/pitch", "VoiceManager: detekce hotwordu, STT/TTS, stavový automat, konfigurovatelná rychlost/výška hlasu", "VoiceManager: wykrywanie słowa aktywującego, STT/TTS, automat stanów, konfigurowalna szybkość/wysokość głosu")
        "VoiceService: ForegroundService s persistent notifikaci" -> Strings.t("VoiceService: ForegroundService with persistent notification", "VoiceService: ForegroundService s persistent notifikací", "VoiceService: ForegroundService ze stałym powiadomieniem")
        "CalendarManager: plny CRUD (add, update, delete, list, findByTitle)" -> Strings.t("CalendarManager: full CRUD (add, update, delete, list, findByTitle)", "CalendarManager: plný CRUD (add, update, delete, list, findByTitle)", "CalendarManager: pełny CRUD (add, update, delete, list, findByTitle)")
        "ContactManager: vyhledavani v telefonnim adresari" -> Strings.t("ContactManager: search in phone contacts", "ContactManager: vyhledávání v telefonním adresáři", "ContactManager: wyszukiwanie w kontaktach telefonu")
        "MailManager: odesilani emailu s konfigurovatelnym podpisem" -> Strings.t("MailManager: sending emails with configurable signature", "MailManager: odesílání emailů s konfigurovatelným podpisem", "MailManager: wysyłanie emaili z konfigurowalnym podpisem")
        "SecretaryApi: kompletni Retrofit rozhrani (CRM, AI, import, export, health)" -> Strings.t("SecretaryApi: complete Retrofit interface (CRM, AI, import, export, health)", "SecretaryApi: kompletní Retrofit rozhraní (CRM, AI, import, export, health)", "SecretaryApi: kompletne API Retrofit (CRM, AI, import, eksport, health)")
        "SettingsScreen: 8 skladacich sekci (hlas, server, CRM, notifikace, profil, uzivatele, data, about)" -> Strings.t("SettingsScreen: 8 collapsible sections (voice, server, CRM, notifications, profile, users, data, about)", "SettingsScreen: 8 skládacích sekcí (hlas, server, CRM, notifikace, profil, uživatelé, data, about)", "SettingsScreen: 8 sekcji rozwijanych (głos, serwer, CRM, powiadomienia, profil, użytkownicy, dane, o aplikacji)")
        "SettingsManager: uzivatelske profily s opravnenimi, heslo spravce SHA-256, vicero emailovych podpisu" -> Strings.t("SettingsManager: user profiles with permissions, SHA-256 admin password, multiple email signatures", "SettingsManager: uživatelské profily s oprávněními, heslo správce SHA-256, vícero emailových podpisů", "SettingsManager: profile użytkowników z uprawnieniami, hasło administratora SHA-256, wiele podpisów email")
        "Server: FastAPI + GPT-4o + PostgreSQL connection pool, CRM search, import CSV/JSON, export CSV" -> Strings.t("Server: FastAPI + GPT-4o + PostgreSQL connection pool, CRM search, CSV/JSON import, CSV export", "Server: FastAPI + GPT-4o + PostgreSQL connection pool, CRM search, import CSV/JSON, export CSV", "Serwer: FastAPI + GPT-4o + pula połączeń PostgreSQL, wyszukiwanie CRM, import CSV/JSON, eksport CSV")
        "Server: datetime awareness (AI zna aktualni cas), calendar/contacts/email/task tools" -> Strings.t("Server: datetime awareness (AI knows current time), calendar/contacts/email/task tools", "Server: datetime awareness (AI zná aktuální čas), calendar/contacts/email/task tools", "Serwer: datetime awareness (AI zna aktualny czas), narzędzia calendar/contacts/email/task")
        "Models: nullable defaults pro bezpecnou deserializaci, ConnectionStatus enum" -> Strings.t("Models: nullable defaults for safe deserialization, ConnectionStatus enum", "Models: nullable defaults pro bezpečnou deserializaci, enum ConnectionStatus", "Models: wartości domyślne dla nullable do bezpiecznej deserializacji, enum ConnectionStatus")
        else -> change
    }

    fun localizeKnownIssue(issue: String): String = when (issue) {
        "Inbox obrazovka je placeholder" -> Strings.t("Inbox screen is still a placeholder", "Inbox obrazovka je placeholder", "Ekran Inbox jest placeholderem")
        "Tasks jen v lokalni pameti" -> Strings.t("Tasks are only in local memory", "Tasks jsou jen v lokální paměti", "Zadania są tylko w pamięci lokalnej")
        "PDF export neni implementovan" -> Strings.t("PDF export is not implemented", "PDF export není implementován", "Eksport PDF nie jest zaimplementowany")
        "Tasks se ukladaji jen lokalne v pameti" -> Strings.t("Tasks are stored only locally in memory", "Tasks se ukládají jen lokálně v paměti", "Zadania są zapisywane tylko lokalnie w pamięci")
        "Offline fronta prikazu neni implementovana" -> Strings.t("Offline command queue is not implemented", "Offline fronta příkazů není implementována", "Offline kolejka poleceń nie jest zaimplementowana")
        "PDF export neni soucasti tohoto vydani" -> Strings.t("PDF export is not part of this release", "PDF export není součástí tohoto vydání", "Eksport PDF nie jest częścią tego wydania")
        "Auto-import pri spusteni neni aktivni" -> Strings.t("Auto-import on startup is not active", "Auto-import při spuštění není aktivní", "Auto-import przy uruchomieniu nie jest aktywny")
        else -> issue
    }

    fun getChangelogForDisplay(): List<String> {
        return CHANGELOG.map { entry ->
            val typeLabel = when (entry.type) {
                ChangeType.PATCH -> "OPRAVA"
                ChangeType.MINOR -> "FUNKCE"
                ChangeType.MAJOR -> "ARCHITEKTURA"
            }
            "v${entry.version} [$typeLabel] ${entry.date} (${entry.author})\n${entry.summary}"
        }
    }
}

// === DATOVE TRIDY PRO VERZOVANI ===

enum class ChangeType {
    PATCH,  // bugfix (pismeno)
    MINOR,  // nova funkce (cislo)
    MAJOR   // architektura (cislo)
}

data class ChangelogEntry(
    val version: String,
    val versionCode: Int,
    val date: String,
    val author: String,
    val coder: String = "",
    val type: ChangeType,
    val summary: String,
    val changes: List<String>,
    val knownIssues: List<String> = emptyList()
)

data class FileInfo(
    val filename: String,
    val description: String
)

data class VersionRule(
    val type: String,
    val example: String,
    val description: String
)
