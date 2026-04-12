package com.example.secretary

import java.util.Locale

object Strings {
    enum class Lang { EN, CS, PL }

    private var current: Lang = Lang.EN

    fun setLanguage(lang: Lang) { current = lang }
    fun setLanguage(code: String) { current = fromCode(code) }
    fun getCurrent(): Lang = current
    fun getLangCode(): String = when (current) { Lang.EN -> "en"; Lang.CS -> "cs"; Lang.PL -> "pl" }
    fun getRecognitionLocale(): String = when (current) { Lang.EN -> "en-GB"; Lang.CS -> "cs-CZ"; Lang.PL -> "pl-PL" }
    fun fromCode(code: String): Lang = when { code.lowercase().startsWith("cs") -> Lang.CS; code.lowercase().startsWith("pl") -> Lang.PL; else -> Lang.EN }

    // === NAVIGATION ===
    val home get() = t("Home", "Domů", "Strona główna")
    val crm get() = t("CRM", "CRM", "CRM")
    val tasks get() = t("Tasks", "Úkoly", "Zadania")
    val calendar get() = t("Calendar", "Kalendář", "Kalendarz")
    val settings get() = t("Settings", "Nastavení", "Ustawienia")

    // === DASHBOARD ===
    val urgentTasks get() = t("Urgent tasks", "Urgentní úkoly", "Pilne zadania")
    val activeTasks get() = t("Active tasks", "Aktivní úkoly", "Aktywne zadania")
    val waitingForClient get() = t("Waiting for client", "Čeká na klienta", "Czeka na klienta")
    val waitingForMaterial get() = t("Waiting for material", "Čeká na materiál", "Czeka na materiał")
    val waitingForPayment get() = t("Waiting for payment", "Čeká na platbu", "Czeka na płatność")
    val activeJobs get() = t("Active jobs", "Aktivní zakázky", "Aktywne zlecenia")
    val newLeads get() = t("New leads", "Nové leady", "Nowe leady")
    val clientsInCrm get() = t("Clients in CRM", "Klienti v CRM", "Klienci w CRM")
    val noActiveTasks get() = t("No active tasks", "Žádné aktivní úkoly", "Brak aktywnych zadań")

    // === CRM TABS ===
    val today get() = t("Today", "Dnes", "Dziś")
    val clients get() = t("Clients", "Klienti", "Klienci")
    val jobs get() = t("Jobs", "Zakázky", "Zlecenia")
    val leads get() = t("Leads", "Leady", "Leady")
    val invoices get() = t("Invoices", "Faktury", "Faktury")
    val communications get() = t("Communications", "Komunikace", "Komunikacja")
    val workReports get() = t("Work Reports", "Výkazy práce", "Raporty pracy")
    val noWorkReports get() = t("No work reports yet", "Žádné výkazy práce", "Brak raportów pracy")
    val quotes get() = t("Quotes", "Nabídky", "Oferty")
    val noQuotes get() = t("No quotes yet", "Žádné nabídky", "Brak ofert")
    val notes get() = t("Notes", "Poznámky", "Notatki")
    val overview get() = t("Overview", "Přehled", "Przegląd")

    // === ACTIONS ===
    val save get() = t("Save", "Uložit", "Zapisz")
    val cancel get() = t("Cancel", "Zrušit", "Anuluj")
    val create get() = t("Create", "Vytvořit", "Utwórz")
    val edit get() = t("Edit", "Upravit", "Edytuj")
    val delete get() = t("Delete", "Smazat", "Usuń")
    val close get() = t("Close", "Zavřít", "Zamknij")
    val confirm get() = t("Confirm", "Potvrdit", "Potwierdź")
    val convert get() = t("Convert", "Převést", "Konwertuj")
    val complete get() = t("Complete", "Dokončit", "Zakończ")
    val addNote get() = t("Add note", "Přidat poznámku", "Dodaj notatkę")
    val logCommunication get() = t("Log communication", "Zalogovat komunikaci", "Dodaj komunikację")

    // === CLIENT ===
    val newClientTitle get() = t("New client", "Nový klient", "Nowy klient")
    val editClient get() = t("Edit client", "Upravit klienta", "Edytuj klienta")
    val clientName get() = t("Name", "Jméno", "Imię")
    val email get() = t("Email", "Email", "Email")
    val phone get() = t("Phone", "Telefon", "Telefon")
    val noClients get() = t("No clients", "Žádní klienti", "Brak klientów")
    val contact get() = t("Contact", "Kontakt", "Kontakt")
    val status get() = t("Status", "Stav", "Status")
    val commercial get() = t("Commercial", "Komerční", "Komercyjny")
    val properties get() = t("Properties", "Nemovitosti", "Nieruchomości")
    val codeLabel get() = t("Code", "Kód", "Kod")
    val notProvided get() = t("Not provided", "Neuveden", "Nie podano")
    val yes get() = t("Yes", "Ano", "Tak")
    val no get() = t("No", "Ne", "Nie")
    val client get() = t("Client", "Klient", "Klient")
    val noClient get() = t("No client", "Bez klienta", "Bez klienta")
    val firstName get() = t("First name", "Jméno", "Imię")
    val lastName get() = t("Last name", "Příjmení", "Nazwisko")
    val titleField get() = t("Title", "Titul", "Tytuł")
    val companyName get() = t("Company", "Firma", "Firma")
    val companyRegNo get() = t("Reg. number", "IČO", "NIP")
    val vatNo get() = t("VAT number", "DIČ", "VAT")
    val emailSecondary get() = t("Email 2", "Email 2", "Email 2")
    val phoneSecondary get() = t("Phone 2", "Telefon 2", "Telefon 2")
    val website get() = t("Website", "Web", "Strona www")
    val billingAddress get() = t("Billing address", "Fakturační adresa", "Adres rozliczeniowy")
    val city get() = t("City", "Město", "Miasto")
    val postcode get() = t("Postcode", "PSČ", "Kod pocztowy")
    val country get() = t("Country", "Země", "Kraj")
    val preferredContact get() = t("Preferred contact", "Preferovaný kontakt", "Preferowany kontakt")
    val clientType get() = t("Client type", "Typ klienta", "Typ klienta")
    val residential get() = t("Residential", "Rezidentialní", "Rezydencjalny")
    val searchClients get() = t("Search clients...", "Hledat klienty...", "Szukaj klientów...")

    // === JOB ===
    val newJob get() = t("New job", "Nová zakázka", "Nowe zlecenie")
    val jobTitle get() = t("Job title", "Název zakázky", "Tytuł zlecenia")
    val noJobs get() = t("No jobs", "Žádné zakázky", "Brak zleceń")
    val jobNumber get() = t("Job number", "Číslo zakázky", "Numer zlecenia")
    val job get() = t("Job", "Zakázka", "Zlecenie")
    val plannedStart get() = t("Planned start", "Plánovaný začátek", "Planowany start")
    val plan get() = t("Plan", "Plán", "Plan")
    val noTermin get() = t("no date", "bez termínu", "bez terminu")
    val changeStatus get() = t("Change status", "Změnit stav", "Zmień status")

    // === TASK ===
    val newTask get() = t("New task", "Nový úkol", "Nowe zadanie")
    val taskTitle get() = t("Task title", "Název úkolu", "Tytuł zadania")
    val taskType get() = t("Task type", "Typ úkolu", "Typ zadania")
    val taskResult get() = t("Task result", "Výsledek úkolu", "Wynik zadania")
    val whatWasDone get() = t("What was done?", "Co bylo uděláno?", "Co zostało zrobione?")
    val saveResult get() = t("Save result", "Uložit výsledek", "Zapisz wynik")
    val completeTask get() = t("Complete task", "Dokončit úkol", "Zakończ zadanie")
    val taskCompleted get() = t("Task completed", "Úkol dokončen", "Zadanie zakończone")
    val priority get() = t("Priority", "Priorita", "Priorytet")
    val changePriority get() = t("Change priority", "Změnit prioritu", "Zmień priorytet")
    val deadline get() = t("Deadline", "Termín", "Termin")
    val assigned get() = t("Assigned", "Přiřazeno", "Przypisane")
    val description get() = t("Description", "Popis", "Opis")
    val type get() = t("Type", "Typ", "Typ")
    val noTasks get() = t("No tasks", "Žádné úkoly", "Brak zadań")
    val noTasksForClient get() = t("No tasks for this client", "Žádné úkoly pro klienta", "Brak zadań dla klienta")
    val allTasks get() = t("All", "Vše", "Wszystkie")
    val newTasks get() = t("New", "Nové", "Nowe")
    val inProgress get() = t("In progress", "Řeší se", "W trakcie")
    val waiting get() = t("Waiting", "Čeká", "Czeka")
    val done get() = t("Done", "Hotové", "Gotowe")
    val addVoiceOrButton get() = t("Add by voice or + button", "Přidej hlasem nebo +", "Dodaj głosem lub +")
    val created get() = t("Created", "Vytvořeno", "Utworzono")
    val createdBy get() = t("Created by", "Vytvořil", "Utworzył")
    val sourceLabel get() = t("Source", "Zdroj", "Źródło")
    val selectClient get() = t("Select client", "Vyberte klienta", "Wybierz klienta")

    // === TASK TYPES (for AddTaskDialog) ===
    val call get() = t("Call", "Zavolat", "Zadzwonić")
    val meeting get() = t("Meeting", "Schůzka", "Spotkanie")
    val orderMaterial get() = t("Order material", "Objednat materiál", "Zamówić materiał")
    val visit get() = t("Visit", "Návštěva", "Wizyta")
    val workExecution get() = t("Work", "Realizace", "Realizacja")
    val inspection get() = t("Inspection", "Kontrola", "Kontrola")
    val calculation get() = t("Calculation", "Kalkulace", "Kalkulacja")
    val remind get() = t("Remind", "Připomenout", "Przypomnieć")
    val noteLabel get() = t("Note", "Poznámka", "Notatka")

    // === PRIORITY LEVELS ===
    val low get() = t("Low", "Nízká", "Niski")
    val normal get() = t("Normal", "Běžná", "Normalny")
    val high get() = t("High", "Vysoká", "Wysoki")
    val urgent get() = t("Urgent", "Urgentní", "Pilny")
    val critical get() = t("Critical", "Kritická", "Krytyczny")

    // === LEAD ===
    val newLead get() = t("New lead", "Nový lead", "Nowy lead")
    val contactName get() = t("Contact name", "Jméno kontaktu", "Nazwa kontaktu")
    val source get() = t("Source", "Zdroj", "Źródło")
    val inquiryDesc get() = t("Inquiry description", "Popis poptávky", "Opis zapytania")
    val convertLead get() = t("Convert lead", "Převést lead", "Konwertuj lead")
    val toClient get() = t("→ Client", "→ Klient", "→ Klient")
    val toJob get() = t("→ Job", "→ Zakázka", "→ Zlecenie")
    val noLeads get() = t("No leads — add with + button", "Žádné leady — přidejte +", "Brak leadów — dodaj +")

    // === INVOICES ===
    val noInvoices get() = t("No invoices", "Žádné faktury", "Brak faktur")
    val dueDate get() = t("Due date", "Splatnost", "Termin płatności")
    val amount get() = t("Amount", "Částka", "Kwota")

    // === COMMUNICATION ===
    val noNotes get() = t("No notes — add with + button", "Žádné poznámky — přidejte +", "Brak notatek — dodaj +")
    val noComms get() = t("No communications", "Žádná komunikace", "Brak komunikacji")
    val subject get() = t("Subject", "Předmět", "Temat")
    val summary get() = t("Summary", "Souhrn", "Podsumowanie")
    val outgoing get() = t("Outgoing", "Odchozí", "Wychodzące")
    val incoming get() = t("Incoming", "Příchozí", "Przychodzące")

    // === SETTINGS ===
    val language get() = t("Language", "Jazyk", "Język")
    val changeLanguage get() = t("Change language", "Změnit jazyk", "Zmień język")
    val languageChangeConfirm get() = t("Change app language to", "Změnit jazyk aplikace na", "Zmienić język aplikacji na")

    // === VOICE ===
    val listening get() = t("Listening", "Poslouchám", "Słucham")
    val waitingForCommand get() = t("Waiting for command...", "Čekám na povel...", "Czekam na polecenie...")
    val processing get() = t("Processing...", "Zpracovávám...", "Przetwarzam...")
    val history get() = t("HISTORY", "HISTORIE", "HISTORIA")
    val backgroundActive get() = t("Background active", "Na pozadí aktivní", "Aktywne w tle")
    val backgroundInactive get() = t("Background inactive", "Na pozadí neaktivní", "Nieaktywne w tle")

    // === VOICE ERROR MESSAGES (spoken via TTS) ===
    val serverConnectionError get() = t("Connection error.", "Chyba spojení se serverem.", "Błąd połączenia z serwerem.")
    val networkError get() = t("I can't reach the server.", "Nemůžu se spojit se serverem.", "Nie mogę połączyć się z serwerem.")
    val loggingOut get() = t("Logging out. Goodbye!", "Odhlašuji vás. Na shledanou!", "Wylogowuję. Do widzenia!")
    fun contactNotFound(query: String) = t(
        "I didn't find anyone in contacts for '$query'.",
        "V kontaktech jsem nikoho pro '$query' nenašla.",
        "Nie znalazłam nikogo w kontaktach dla '$query'."
    )

    // === SERVICE NOTIFICATION ===
    val serviceTitle get() = t("Secretary", "Sekretářka", "Sekretarka")
    val serviceReady get() = t("Secretary is ready", "Sekretářka je připravena", "Sekretarka jest gotowa")
    val serviceChannelName get() = t("Voice control", "Hlasové ovládání", "Sterowanie głosowe")
    val serviceChannelDesc get() = t("Secretary listens for commands", "Sekretářka naslouchá příkazům", "Sekretarka słucha poleceń")

    // === CALENDAR VIEW ===
    val calViewDay get() = t("Day", "Den", "Dzień")
    val calViewWeek get() = t("Week", "Týden", "Tydzień")
    val calViewMonth get() = t("Month", "Měsíc", "Miesiąc")
    val calAllDay get() = t("All day", "Celý den", "Cały dzień")
    val calDayLabels get() = when (getCurrent()) {
        Lang.EN -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        Lang.CS -> listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
        Lang.PL -> listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
    }
    fun calLocale() = when (getCurrent()) {
        Lang.EN -> Locale.ENGLISH
        Lang.CS -> Locale("cs", "CZ")
        Lang.PL -> Locale("pl", "PL")
    }

    // === LOGOUT ===
    val logout get() = t("Log out", "Odhlásit", "Wyloguj")

    // === HOME ===
    val foundContacts get() = t("FOUND CONTACTS", "NALEZENÉ KONTAKTY", "ZNALEZIONE KONTAKTY")
    val restart get() = t("Restart", "Restart", "Restart")
    val backgroundOn get() = t("BG ON", "Pozadí ZAP", "TŁO WŁ")
    val backgroundOff get() = t("BG OFF", "Pozadí VYP", "TŁO WYŁ")
    val secretaryFemName get() = t("Secretary", "Sekretářka", "Sekretarka")
    val noDataInCrm get() = t("No CRM data", "Žádná data v CRM", "Brak danych w CRM")
    val dueDatePrefix get() = t("Due: ", "Splatnost: ", "Termin: ")
    val selectAll get() = t("Select all", "Vybrat vše", "Zaznacz wszystkie")
    val noNumber get() = t("No number", "Bez čísla", "Bez numeru")
    val planPrefix get() = t("Plan: ", "Plán: ", "Plan: ")
    val taskNotFound get() = t("Task not found", "Úkol nenalezen", "Nie znaleziono zadania")
    val editTask get() = t("Edit task", "Upravit úkol", "Edytuj zadanie")
    val noteResult get() = t("Note / result", "Poznámka / výsledek", "Notatka / wynik")

    // === RATES ===
    val serviceRates get() = t("Service rates", "Sazby služeb", "Stawki usług")
    val hourlyRatesByType get() = t("Hourly rates by work type", "Hodinové sazby podle typu práce", "Stawki godzinowe wg rodzaju pracy")
    val otherRates get() = t("Other rates", "Ostatní sazby", "Inne stawki")
    val wasteRemovalRate get() = t("Waste removal per bulk bag (£)", "Odvoz odpadu za bulk bag (£)", "Wywóz odpadów za worek zbiorczy (£)")
    val minJobPrice get() = t("Minimum job price (£)", "Minimální cena zakázky (£)", "Minimalna cena zlecenia (£)")
    val hourlyRate get() = t("Hourly rate (£/h)", "Hodinová sazba (£/h)", "Stawka godzinowa (£/h)")

    // === THEME ===
    val appTheme get() = t("App theme", "Motiv aplikace", "Motyw aplikacji")
    val systemTheme get() = t("System", "Podle systému", "Systemowy")
    val lightTheme get() = t("Light", "Světlý", "Jasny")
    val darkTheme get() = t("Dark", "Tmavý", "Ciemny")
    val changeAfterRestart get() = t("Change takes effect after restart", "Změna se projeví po restartu aplikace", "Zmiana wejdzie w życie po restarcie")

    // === VOICE SETTINGS ===
    val voiceControl get() = t("Voice control", "Hlasové ovládání", "Sterowanie głosowe")
    val hotwordDetection get() = t("Hotword detection", "Detekce aktivačního slova", "Wykrywanie słowa aktywacyjnego")
    val listeningForHotword get() = t("Listening for hotword", "Naslouchá na hot word", "Nasłuchuje hot word")
    val activationWord get() = t("Activation word", "Aktivační slovo", "Słowo aktywacyjne")
    val speechRate get() = t("Speech rate", "Rychlost řeči", "Szybkość mowy")
    val voicePitch get() = t("Voice pitch", "Výška hlasu", "Wysokość głosu")
    val silenceLength get() = t("Silence length", "Délka ticha", "Długość ciszy")

    // === SERVER ===
    val serverConnection get() = t("Server & connection", "Server a připojení", "Serwer i połączenie")
    val apiServerUrl get() = t("API server URL", "URL API serveru", "URL serwera API")
    val connectedStatus get() = t("Connected", "Připojeno", "Połączono")
    val testingStatus get() = t("Testing...", "Testuji...", "Testuję...")
    val serverUnavailable get() = t("Server unavailable", "Server nedostupný", "Serwer niedostępny")
    val unknownStatus get() = t("Unknown", "Neznámý", "Nieznany")
    val checkUrlAndServer get() = t("Check URL and ensure server is running", "Zkontrolujte URL a že server běží", "Sprawdź URL i czy serwer działa")
    val testConnection get() = t("Test connection", "Testovat spojení", "Testuj połączenie")
    val offlineMode get() = t("Offline mode", "Offline mód", "Tryb offline")
    val commandQueueDesc get() = t("Queue commands until connected", "Fronta příkazů až do připojení", "Kolejkuj polecenia do połączenia")

    // === CRM SETTINGS ===
    val autoRefresh get() = t("Auto refresh", "Auto refresh", "Auto odświeżanie")
    val manualRefresh get() = t("Manual", "Manuálně", "Ręcznie")
    val defaultTab get() = t("Default tab", "Výchozí tab", "Domyślna zakładka")
    val sorting get() = t("Sort by", "Řazení", "Sortuj wg")
    val sortByName get() = t("Name", "Jméno", "Imię")
    val sortByDate get() = t("Date", "Datum", "Data")
    val sortByActivity get() = t("Activity", "Aktivita", "Aktywność")
    val waste get() = t("Waste", "Odpady", "Odpady")
    val finance get() = t("Finance", "Finance", "Finanse")

    // === NOTIFICATIONS ===
    val notificationsTitle get() = t("Notifications", "Notifikace", "Powiadomienia")
    val persistentNotif get() = t("Persistent notification", "Trvalá notifikace", "Trwałe powiadomienie")
    val taskReminder get() = t("Task reminder", "Připomenutí úkolů", "Przypomnienie zadań")
    val offOption get() = t("Off", "Vyp", "Wyłączone")

    // === WORK PROFILE ===
    val workProfile get() = t("Work profile", "Pracovní profil", "Profil pracy")
    val workHours get() = t("Working hours", "Pracovní hodiny", "Godziny pracy")
    val hotwordWorkHoursOnly get() = t("Hotword only during working hours", "Hotword jen v pracovní době", "Hotword tylko w godzinach pracy")
    val startTime get() = t("Start", "Začátek", "Start")
    val endTime get() = t("End", "Konec", "Koniec")
    val defaultPriority get() = t("Default priority", "Výchozí priorita", "Domyślny priorytet")
    val syncJobsToCalendar get() = t("Sync jobs to calendar", "Synchronizace zakázek do kalendáře", "Synchronizuj zlecenia z kalendarzem")
    val autoCalendarWrite get() = t("Auto write to calendar", "Automatický zápis do kalendáře", "Automatyczny zapis do kalendarza")
    val autoCalendarWriteDesc get() = t("Every new/updated job is written to Google Calendar", "Každá nová/upravená zakázka se zapíše do Google Kalendáře", "Każde nowe/zaktualizowane zlecenie jest zapisywane w Kalendarzu Google")
    val attendeesHint get() = t("Attendees (emails separated by comma)", "Účastníci (e-maily oddělené čárkou)", "Uczestnicy (e-maile oddzielone przecinkiem)")
    val attendeesDesc get() = t("Enter managers, accountants and others who should see jobs in calendar.", "Zadejte správce, účetní a ostatní, kteří mají vidět zakázky v kalendáři.", "Wpisz menedżerów, księgowych i innych, którzy mają widzieć zlecenia w kalendarzu.")
    val defaultJobDuration get() = t("Default job duration", "Výchozí délka zakázky", "Domyślny czas trwania zlecenia")
    val emailSignaturesTitle get() = t("Email signatures", "E-mailové podpisy", "Podpisy e-mail")
    val activeSignature get() = t("Active signature", "Aktivní podpis", "Aktywny podpis")
    val signatureNameLabel get() = t("Name", "Název", "Nazwa")
    val signatureContent get() = t("Signature content", "Obsah podpisu", "Treść podpisu")
    val addNew get() = t("+ New", "+ Nový", "+ Nowy")
    val signature get() = t("Signature", "Podpis", "Podpis")

    // === USERS ===
    val usersAndPermissions get() = t("Users & permissions", "Uživatelé a práva", "Użytkownicy i uprawnienia")
    val enterAdminPassword get() = t("Enter admin password", "Zadejte heslo správce", "Podaj hasło administratora")
    val passwordLabel get() = t("Password", "Heslo", "Hasło")
    val verify get() = t("Verify", "Ověřit", "Weryfikuj")
    val setAdminPassword get() = t("Set admin password", "Nastavit heslo správce", "Ustaw hasło administratora")
    val changePassword get() = t("Change password", "Změnit heslo", "Zmień hasło")
    val setPassword get() = t("Set password", "Nastavit heslo", "Ustaw hasło")
    val usersLabel get() = t("Users", "Uživatelé", "Użytkownicy")
    val adminRole get() = t("Admin", "Správce", "Administrator")
    val managerRole get() = t("Manager", "Manažer", "Menedżer")
    val workerRole get() = t("Worker", "Pracovník", "Pracownik")
    val viewerRole get() = t("Viewer", "Náhled", "Podgląd")
    val activeLabel get() = t("Active", "Aktivní", "Aktywny")
    val switchUser get() = t("Switch", "Přepnout", "Przełącz")
    val addUser get() = t("+ Add user", "+ Přidat uživatele", "+ Dodaj użytkownika")
    val currentPassword get() = t("Current", "Současné", "Aktualne")
    val newPassword get() = t("New password", "Nové heslo", "Nowe hasło")
    val passwordsMismatch get() = t("Passwords don't match", "Neshodují se", "Hasła nie pasują")
    val newUser get() = t("New user", "Nový uživatel", "Nowy użytkownik")
    val roleLabel get() = t("Role", "Role", "Rola")
    val permissionsLabel get() = t("Permissions", "Oprávnění", "Uprawnienia")
    val nameLabel get() = t("Name", "Název", "Nazwa")

    // === DATA ===
    val clearHistoryConfirm get() = t("Clear history?", "Vymazat historii?", "Wyczyścić historię?")
    val resetDefaultsConfirm get() = t("Reset to defaults?", "Obnovit výchozí?", "Przywrócić domyślne?")
    val restore get() = t("Restore", "Obnovit", "Przywróć")
    val dataAndStorage get() = t("Data & storage", "Data a úložiště", "Dane i pamięć")
    val exportCrmCsv get() = t("Export CRM (CSV)", "Exportovat CRM (CSV)", "Eksportuj CRM (CSV)")
    val importContactsTitle get() = t("Import contacts from phone", "Import kontaktů z telefonu", "Importuj kontakty z telefonu")
    val ukNumbersOnly get() = t("UK numbers only (+44, 07, 01, 02)", "Pouze UK čísla (+44, 07, 01, 02)", "Tylko numery UK (+44, 07, 01, 02)")
    val allNumbers get() = t("All numbers", "Všechna čísla", "Wszystkie numery")
    val importContactsBtn get() = t("Import contacts", "Importovat kontakty", "Importuj kontakty")
    val syncStarted get() = t("Sync started...", "Synchronizace spuštěna...", "Synchronizacja uruchomiona...")
    val importDatabase get() = t("Database import", "Import databáze", "Import bazy danych")
    val csvPath get() = t("CSV path", "Cesta k CSV", "Ścieżka CSV")
    val tableLabel get() = t("Table", "Tabulka", "Tabela")
    val autoImportOnStart get() = t("Auto import on start", "Auto import při spuštění", "Auto import przy starcie")
    val startImport get() = t("Start import", "Spustit import", "Uruchom import")
    val clearHistory get() = t("Clear history", "Vymazat historii", "Wyczyść historię")
    val resetDefaults get() = t("Reset to defaults", "Obnovit výchozí nastavení", "Przywróć domyślne ustawienia")

    // === COMPANY PROFILE ===
    val companyProfile get() = t("Company profile", "Profil firmy", "Profil firmy")
    val workspaceMode get() = t("Mode", "Režim", "Tryb")
    val workspaceSolo get() = t("Solo (1 user)", "Solo (1 uživatel)", "Solo (1 użytkownik)")
    val workspaceTeam get() = t("Team (2-5)", "Tým (2-5)", "Zespół (2-5)")
    val workspaceBusiness get() = t("Business (6-30)", "Firma (6-30)", "Firma (6-30)")
    val internalLanguage get() = t("Internal language", "Interní jazyk", "Język wewnętrzny")
    val customerLanguage get() = t("Customer language", "Zákaznický jazyk", "Język klienta")
    val internalLangMode get() = t("Int. language mode", "Int. jazykový mód", "Wewn. tryb językowy")
    val customerLangMode get() = t("Cust. language mode", "Zák. jazykový mód", "Tryb języka klienta")
    val limitsLabel get() = t("Limits", "Limity", "Limity")
    val maxUsers get() = t("Max users", "Max uživatelů", "Max użytkowników")
    val maxClients get() = t("Max clients", "Max klientů", "Max klientów")
    val maxJobsPerMonth get() = t("Max jobs/month", "Max zakázek/měsíc", "Max zleceń/miesiąc")
    val voiceMinutes get() = t("Voice minutes", "Hlasové minuty", "Minuty głosowe")

    // === ABOUT ===
    val aboutApp get() = t("About app", "O aplikaci", "O aplikacji")
    val versionLabel get() = t("Version", "Verze", "Wersja")
    val releaseDateLabel get() = t("Release date", "Datum vydání", "Data wydania")
    val packageLabel get() = t("Package", "Balíček", "Pakiet")
    val authorAndCode get() = t("Author & code", "Autor a kód", "Autor i kod")
    val structureAndCode get() = t("Structure & code", "Struktura a kód", "Struktura i kod")
    val webLabel get() = t("Web", "Web", "WWW")
    val companyLabel get() = t("Company", "Firma", "Firma")
    val codedByLabel get() = t("Code by", "Kódování", "Kodowanie")
    val technologyLabel get() = t("Technology", "Technologie", "Technologia")
    val platformLabel get() = t("Platform", "Platforma", "Platforma")
    val backendLabel get() = t("Backend", "Backend", "Backend")
    val architectureLabel get() = t("Architecture", "Architektura", "Architektura")
    val databaseLabel get() = t("Database", "Databáze", "Baza danych")
    val projectStructure get() = t("Project structure", "Struktura projektu", "Struktura projektu")
    val licenseLabel get() = t("License", "Licence", "Licencja")
    val versionHistory get() = t("Version history", "Historie verzí", "Historia wersji")
    val versioningRules get() = t("Versioning rules", "Pravidla verzování", "Reguły wersjonowania")
    val mandatoryStepsLabel get() = t("Mandatory steps for changes", "Povinné kroky při změně", "Obowiązkowe kroki przy zmianie")
    val changelogLabel get() = t("Changelog", "Changelog", "Changelog")
    val authorPrefix get() = t("Author: ", "Autor: ", "Autor: ")
    val codeByPrefix get() = t("Code: ", "Kód: ", "Kod: ")
    val hideChanges get() = t("Hide changes", "Skrýt změny", "Ukryj zmiany")
    fun showChanges(count: Int) = t("Show changes ($count)", "Zobrazit změny ($count)", "Pokaż zmiany ($count)")
    val knownIssuesLabel get() = t("Known issues:", "Známé problémy:", "Znane problemy:")
    val patchLabel get() = t("FIX", "OPRAVA", "POPRAWKA")
    val featureLabel get() = t("FEATURE", "FUNKCE", "FUNKCJA")
    val archLabel get() = t("ARCH", "ARCHITEKTURA", "ARCHITEKTURA")

    // === CRM DIALOGS ===
    val editJob get() = t("Edit job", "Upravit zakázku", "Edytuj zlecenie")
    val plannedStartLabel get() = t("Planned start (YYYY-MM-DD)", "Plánované zahájení (YYYY-MM-DD)", "Planowany start (YYYY-MM-DD)")
    val addJobNote get() = t("Add note to job", "Přidat poznámku k zakázce", "Dodaj notatkę do zlecenia")
    val editLead get() = t("Edit lead", "Upravit lead", "Edytuj lead")
    val newInvoice get() = t("New invoice", "Nová faktura", "Nowa faktura")
    val amountLabel get() = t("Amount (£)", "Částka (£)", "Kwota (£)")
    val dueDateInputLabel get() = t("Due date (YYYY-MM-DD)", "Splatnost (YYYY-MM-DD)", "Termin płatności (YYYY-MM-DD)")
    val changeInvoiceStatus get() = t("Change invoice status", "Změnit stav faktury", "Zmień status faktury")
    val newWorkReport get() = t("New work report", "Nový výkaz práce", "Nowy raport pracy")
    val dateLabel get() = t("Date (YYYY-MM-DD)", "Datum (YYYY-MM-DD)", "Data (YYYY-MM-DD)")
    val totalHoursLabel get() = t("Total hours", "Celkem hodin", "Łączna liczba godzin")
    val totalAmountLabel get() = t("Total £", "Celkem £", "Łącznie £")
    val approveQuoteTitle get() = t("Approve quote?", "Schválit nabídku?", "Zatwierdzić ofertę?")
    val approveAndCreateJob get() = t("Approve + create job", "Schválit + vytvořit zakázku", "Zatwierdź + utwórz zlecenie")
    val newQuote get() = t("New quote", "Nová nabídka", "Nowa oferta")
    val quoteTitleLabel get() = t("Quote title", "Název nabídky", "Tytuł oferty")
    val addItem get() = t("Add item", "Přidat položku", "Dodaj pozycję")
    val quantityLabel get() = t("Quantity", "Množství", "Ilość")
    val unitPrice get() = t("Unit price (£)", "Cena za jednotku (£)", "Cena jednostkowa (£)")
    val displayName get() = t("Display name *", "Zobrazované jméno *", "Wyświetlana nazwa *")
    val statusColonLabel get() = t("Status:", "Status:", "Status:")
    val newCommunication get() = t("New communication", "Nová komunikace", "Nowa komunikacja")
    val directionLabel get() = t("Direction:", "Směr:", "Kierunek:")
    val messageLabel get() = t("Message", "Zpráva", "Wiadomość")
    val descriptionRequired get() = t("Description *", "Popis *", "Opis *")
    val notesPlural get() = t("Notes", "Poznámky", "Notatki")
    val typeColonLabel get() = t("Type:", "Typ:", "Typ:")
    val clientRequired get() = t("Client *", "Klient *", "Klient *")
    val convertArrow get() = t("Convert →", "Převést →", "Konwertuj →")
    fun invoiceN(n: Int) = t("Invoice ${n}×", "Fakturovat ${n}×", "Fakturuj ${n}×")
    fun createdInvoices(n: Int, errors: Int) = if (errors > 0) t("Created $n invoices, $errors errors", "Vytvořeno $n faktur, $errors chyb", "Utworzono $n faktur, $errors błędów") else t("Created $n invoices", "Vytvořeno $n faktur", "Utworzono $n faktur")

    // === HELPER ===
    private fun t(en: String, cs: String, pl: String): String = when (current) {
        Lang.EN -> en; Lang.CS -> cs; Lang.PL -> pl
    }

    /**
     * Expands numeric unit symbols (°C, °F, °) to their full spoken form in the current language,
     * so that TTS engines read them correctly regardless of the voice locale.
     */
    fun formatForSpeech(text: String): String {
        var result = text
        // Temperature – Celsius: "20°C" or "20 °C" (case-insensitive)
        result = result.replace(Regex("(-?\\d+(?:[.,]\\d+)?)\\s*°[Cc]")) { m ->
            val n = m.groupValues[1]
            when (current) {
                Lang.CS -> "$n stupňů Celsia"
                Lang.PL -> "$n stopni Celsjusza"
                Lang.EN -> "$n degrees Celsius"
            }
        }
        // Temperature – Fahrenheit: "68°F" (case-insensitive)
        result = result.replace(Regex("(-?\\d+(?:[.,]\\d+)?)\\s*°[Ff]")) { m ->
            val n = m.groupValues[1]
            when (current) {
                Lang.CS -> "$n stupňů Fahrenheita"
                Lang.PL -> "$n stopni Fahrenheita"
                Lang.EN -> "$n degrees Fahrenheit"
            }
        }
        // Bare degree symbol: "45°" (not followed by C/c or F/f)
        result = result.replace(Regex("(-?\\d+(?:[.,]\\d+)?)°(?![CFcf])")) { m ->
            val n = m.groupValues[1]
            when (current) {
                Lang.CS -> "$n stupňů"
                Lang.PL -> "$n stopni"
                Lang.EN -> "$n degrees"
            }
        }
        return result
    }

    // === STATUS LOCALIZATION ===
    fun localizeStatus(raw: String?): String = when (raw?.lowercase()?.replace(" ","_")) {
        "novy", "nova", "new" -> t("New", "Nový", "Nowy")
        "v_reseni", "in_progress" -> t("In progress", "V řešení", "W trakcie")
        "ceka_na_klienta", "waiting_client" -> t("Waiting for client", "Čeká na klienta", "Czeka na klienta")
        "ceka_na_material", "waiting_material" -> t("Waiting for material", "Čeká na materiál", "Czeka na materiał")
        "ceka_na_platbu", "waiting_payment" -> t("Waiting for payment", "Čeká na platbu", "Czeka na płatność")
        "naplanovano", "naplanovany", "scheduled" -> t("Scheduled", "Naplánovaný", "Zaplanowane")
        "v_realizaci", "active" -> t("In progress", "V realizaci", "W realizacji")
        "dokonceno", "completed" -> t("Completed", "Dokončeno", "Zakończone")
        "hotovo", "done" -> t("Done", "Hotovo", "Gotowe")
        "vyfakturovano", "invoiced" -> t("Invoiced", "Vyfakturováno", "Zafakturowane")
        "uzavreno", "closed" -> t("Closed", "Uzavřeno", "Zamknięte")
        "zruseno", "cancelled" -> t("Cancelled", "Zrušeno", "Anulowane")
        "pozastaveno", "paused" -> t("Paused", "Pozastaveno", "Wstrzymane")
        "predano_dal" -> t("Delegated", "Předáno dál", "Przekazane")
        "draft" -> t("Draft", "Koncept", "Szkic")
        "odeslana", "sent" -> t("Sent", "Odesláno", "Wysłane")
        "uhrazena", "paid" -> t("Paid", "Uhrazeno", "Zapłacone")
        "castecne_uhrazena", "partially_paid" -> t("Partially paid", "Částečně uhrazeno", "Częściowo zapłacone")
        "po_splatnosti", "overdue" -> t("Overdue", "Po splatnosti", "Po terminie")
        "confirmed" -> t("Confirmed", "Potvrzeno", "Potwierdzone")
        // Priority
        "kriticka", "critical" -> t("Critical", "Kritická", "Krytyczny")
        "urgentni", "urgent" -> t("Urgent", "Urgentní", "Pilny")
        "vysoka", "high" -> t("High", "Vysoká", "Wysoki")
        "bezna", "normal" -> t("Normal", "Běžná", "Normalny")
        "stredni", "medium" -> t("Medium", "Střední", "Średni")
        "nizka", "low" -> t("Low", "Nízká", "Niski")
        // Lead status
        "kvalifikovany", "qualified" -> t("Qualified", "Kvalifikovaný", "Kwalifikowany")
        "nabidka_odeslana", "quote_sent" -> t("Quote sent", "Nabídka odeslána", "Oferta wysłana")
        "preveden_na_klienta" -> t("Converted to client", "Převeden na klienta", "Przekonwertowany")
        "preveden_na_zakazku" -> t("Converted to job", "Převeden na zakázku", "Przekonwertowany")
        "zamitnuto", "rejected" -> t("Rejected", "Zamítnuto", "Odrzucone")
        else -> raw ?: ""
    }
}
