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
    const val VERSION_NAME = "1.0a"
    const val VERSION_CODE = 1
    const val BUILD_DATE = "2026-03-30"

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
