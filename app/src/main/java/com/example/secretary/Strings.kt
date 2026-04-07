package com.example.secretary

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

    // === HELPER ===
    private fun t(en: String, cs: String, pl: String): String = when (current) {
        Lang.EN -> en; Lang.CS -> cs; Lang.PL -> pl
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
