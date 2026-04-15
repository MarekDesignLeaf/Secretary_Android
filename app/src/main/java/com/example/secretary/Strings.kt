package com.example.secretary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Strings {
    enum class Lang { EN, CS, PL }

    private var activeLang: Lang by mutableStateOf(Lang.EN)

    fun setLanguage(lang: Lang) { activeLang = lang }
    fun setLanguage(code: String) { activeLang = fromCode(code) }
    fun getCurrent(): Lang = activeLang
    fun getLangCode(): String = when (activeLang) { Lang.EN -> "en"; Lang.CS -> "cs"; Lang.PL -> "pl" }
    fun getRecognitionLocale(): String = when (activeLang) { Lang.EN -> "en-GB"; Lang.CS -> "cs-CZ"; Lang.PL -> "pl-PL" }
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
    val contactsDirectory get() = t("Contacts", "Kontakty", "Kontakty")
    val plants get() = t("Plants", "Rostliny", "Rośliny")
    val employeesSection get() = t("Employees", "Zaměstnanci", "Pracownicy")
    val subcontractorsSection get() = t("Subcontractors", "Subkontraktoři", "Podwykonawcy")
    val materialSuppliersSection get() = t("Material suppliers", "Dodavatelé materiálu", "Dostawcy materiałów")
    val rentalsSection get() = t("Tool and vehicle rentals", "Půjčovny nářadí a aut", "Wypożyczalnie narzędzi i aut")
    val customSection get() = t("Custom section", "Vlastní oddíl", "Własna sekcja")
    val addSection get() = t("Add section", "Přidat oddíl", "Dodaj sekcję")
    val createSection get() = t("Create section", "Vytvořit oddíl", "Utwórz sekcję")
    val sectionName get() = t("Section name", "Název oddílu", "Nazwa sekcji")
    val addContact get() = t("Add contact", "Přidat kontakt", "Dodaj kontakt")
    val editContact get() = t("Edit contact", "Upravit kontakt", "Edytuj kontakt")
    val importToContacts get() = t("Import contacts", "Importovat kontakty", "Importuj kontakty")
    val saveImportedContacts get() = t("Save imported contacts", "Uložit importované kontakty", "Zapisz importowane kontakty")
    val noSharedContacts get() = t("No shared contacts yet", "Zatím žádné sdílené kontakty", "Brak wspólnych kontaktów")
    val companyNameLabel get() = t("Company", "Firma", "Firma")
    val sectionLabel get() = t("Section", "Oddíl", "Sekcja")
    val notesLabel get() = t("Notes", "Poznámky", "Notatki")
    val importContactsHint get() = t(
        "Select contacts from your phone and assign them to a shared section.",
        "Vyber kontakty z telefonu a přiřaď je do sdíleného oddílu.",
        "Wybierz kontakty z telefonu i przypisz je do wspólnej sekcji."
    )
    val sharedContactsHint get() = t(
        "These contacts are shared on the server and every user can use or extend them.",
        "Tyto kontakty jsou sdílené na serveru a každý uživatel je může používat a doplňovat.",
        "Te kontakty są współdzielone na serwerze i każdy użytkownik może ich używać oraz je uzupełniać."
    )
    val noSectionsAvailable get() = t("No contact sections available", "Nejsou dostupné žádné oddíly", "Brak dostępnych sekcji")
    val setClients get() = t("Set clients", "Nastavit klienty", "Ustaw klientów")
    val closeClientSetup get() = t("Close setup", "Zavřít nastavení", "Zamknij ustawianie")
    val saveClientSelection get() = t("Save client selection", "Uložit výběr klientů", "Zapisz wybór klientów")
    val clientSetupHint get() = t(
        "Contacts checked here become shared server clients. Unchecked contacts are not kept as synced clients on the server.",
        "Zaškrtnuté kontakty se stanou sdílenými klienty na serveru. Nezaškrtnuté kontakty se jako synchronizovaní klienti na serveru neudrží.",
        "Zaznaczone kontakty stają się współdzielonymi klientami na serwerze. Niezaznaczone kontakty nie są utrzymywane jako zsynchronizowani klienci na serwerze."
    )
    val clientSetupLoading get() = t("Loading contacts from phone...", "Načítám kontakty z telefonu...", "Wczytuję kontakty z telefonu...")
    val noPhoneContacts get() = t("No phone contacts found", "V telefonu nejsou žádné kontakty", "Brak kontaktów w telefonie")
    val selectedAsClient get() = t("Client", "Klient", "Klient")
    val syncedToClient get() = t("Synced to", "Synchronizováno pod", "Zsynchronizowano do")
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

    // === PLANT RECOGNITION ===
    val plantRecognitionTitle get() = t("Plant recognition", "Rozpoznávání rostlin", "Rozpoznawanie roślin")
    val plantHealthTitle get() = t("Plant diseases", "Choroby rostlin", "Choroby roślin")
    val plantModeRecognition get() = t("Identify plant", "Rozpoznat rostlinu", "Rozpoznaj roślinę")
    val plantModeHealth get() = t("Check disease", "Zjistit chorobu", "Sprawdź chorobę")
    val plantRecognitionHint get() = t(
        "Take at least 1 photo. For better results, add the whole plant, a leaf detail, and flower or fruit if available. Optional shots can be skipped.",
        "Pořiď alespoň 1 fotografii. Pro lepší výsledek přidej celou rostlinu, detail listu a pokud je k dispozici, květ nebo plod. Volitelné snímky lze přeskočit.",
        "Zrób co najmniej 1 zdjęcie. Dla lepszego wyniku dodaj całą roślinę, zbliżenie liścia oraz kwiat lub owoc, jeśli są dostępne. Zdjęcia opcjonalne można pominąć."
    )
    val plantHealthHint get() = t(
        "Take at least 1 clear photo of the damaged part. For better diagnosis, add the whole plant, a close leaf detail, and any spots, mold, pests or stem damage.",
        "Pořiď alespoň 1 jasnou fotografii poškozené části. Pro lepší diagnózu přidej celou rostlinu, detail listu a případné skvrny, plíseň, škůdce nebo poškození stonku.",
        "Zrób co najmniej 1 wyraźne zdjęcie uszkodzonej części. Dla lepszej diagnozy dodaj całą roślinę, zbliżenie liścia oraz plamy, pleśń, szkodniki lub uszkodzenia łodygi."
    )
    val plantRecognitionVoiceGuide get() = t(
        "Take at least 1 photo. Whole plant, leaf detail, and flower or fruit are recommended, but you can skip optional shots.",
        "Pořiď alespoň 1 fotografii. Celá rostlina, detail listu a květ nebo plod jsou doporučené, ale volitelné snímky můžeš přeskočit.",
        "Zrób co najmniej 1 zdjęcie. Cała roślina, zbliżenie liścia oraz kwiat lub owoc są zalecane, ale zdjęcia opcjonalne możesz pominąć."
    )
    val plantHealthVoiceGuide get() = t(
        "Take at least 1 photo of the diseased part. Whole plant, damaged leaf detail, and any visible mold, insects or stem damage are recommended. Optional shots can be skipped.",
        "Pořiď alespoň 1 fotografii nemocné části. Doporučená je celá rostlina, detail poškozeného listu a viditelná plíseň, hmyz nebo poškození stonku. Volitelné snímky můžeš přeskočit.",
        "Zrób co najmniej 1 zdjęcie chorej części. Zalecane są: cała roślina, zbliżenie uszkodzonego liścia oraz widoczna pleśń, owady lub uszkodzenia łodygi. Zdjęcia opcjonalne możesz pominąć."
    )
    val plantWholePlant get() = t("Whole plant", "Celá rostlina", "Cała roślina")
    val plantLeafDetail get() = t("Leaf detail", "Detail listu", "Detal liścia")
    val plantFlowerOrFruit get() = t("Flower, fruit or bark", "Květ, plod nebo kůra", "Kwiat, owoc lub kora")
    val plantDiseaseCloseup get() = t("Damaged part", "Poškozená část", "Uszkodzona część")
    val plantDiseaseLeafDetail get() = t("Damaged leaf detail", "Detail poškozeného listu", "Detal uszkodzonego liścia")
    val plantDiseaseContext get() = t("Whole plant or stem context", "Celá rostlina nebo stonek", "Cała roślina lub łodyga")
    val identifyPlantAction get() = t("Identify plant", "Rozpoznat rostlinu", "Rozpoznaj roślinę")
    val assessPlantHealthAction get() = t("Check disease", "Zjistit chorobu", "Sprawdź chorobę")
    val plantRecognitionLoading get() = t("Identifying plant...", "Rozpoznávám rostlinu...", "Rozpoznaję roślinę...")
    val plantHealthLoading get() = t("Checking plant disease...", "Zjišťuji chorobu rostliny...", "Sprawdzam chorobę rośliny...")
    val plantNoResultYet get() = t("No identification yet", "Zatím bez rozpoznání", "Brak identyfikacji")
    val plantHealthNoResultYet get() = t("No disease assessment yet", "Zatím bez posouzení choroby", "Brak oceny choroby")
    val plantBestMatch get() = t("Best match", "Nejlepší shoda", "Najlepsze dopasowanie")
    val plantGuidance get() = t("Description and care", "Popis a nároky", "Opis i wymagania")
    val plantAlternatives get() = t("Other likely matches", "Další pravděpodobné shody", "Inne prawdopodobne dopasowania")
    val plantHealthBestMatch get() = t("Most likely issue", "Nejpravděpodobnější problém", "Najbardziej prawdopodobny problem")
    val plantHealthSummary get() = t("Diagnosis and treatment", "Diagnóza a léčba", "Diagnoza i leczenie")
    val plantHealthAlternatives get() = t("Other possible issues", "Další možné problémy", "Inne możliwe problemy")
    val plantTreatmentLabel get() = t("Recommended treatment", "Doporučená léčba", "Zalecane leczenie")
    val plantPreventionLabel get() = t("Prevention", "Prevence", "Zapobieganie")
    val plantBiologicalLabel get() = t("Biological steps", "Biologické kroky", "Działania biologiczne")
    val plantChemicalLabel get() = t("Chemical steps", "Chemické kroky", "Działania chemiczne")
    val plantHealthyLabel get() = t("Plant looks healthy", "Rostlina vypadá zdravě", "Roślina wygląda zdrowo")
    val plantHealthyGuidance get() = t("No clear disease signal was found in the photos.", "Na fotografiích nebyl nalezen jasný signál choroby.", "Na zdjęciach nie znaleziono wyraźnego sygnału choroby.")
    val confidence get() = t("Confidence", "Pravděpodobnost", "Pewność")
    val plantNeedsPhoto get() = t(
        "You need at least 1 photo before identification.",
        "Před rozpoznáním je potřeba alespoň 1 fotografie.",
        "Przed rozpoznaniem potrzebne jest co najmniej 1 zdjęcie."
    )
    val plantVoiceBanner get() = t("Voice-guided capture is active.", "Je aktivní hlasově vedené focení.", "Aktywne jest prowadzenie głosowe podczas robienia zdjęć.")
    val plantCaptureReady get() = t("Camera is ready for the next plant photo.", "Fotoaparát je připraven pro další fotografii rostliny.", "Aparat jest gotowy na kolejne zdjęcie rośliny.")
    val plantRecognitionUnavailable get() = t("Plant recognition service is not configured yet.", "Služba pro rozpoznávání rostlin ještě není nastavená.", "Usługa rozpoznawania roślin nie jest jeszcze skonfigurowana.")
    val plantHealthUnavailable get() = t("Plant disease service is not configured yet.", "Služba pro choroby rostlin ještě není nastavená.", "Usługa chorób roślin nie jest jeszcze skonfigurowana.")
    val plantRecognitionFailed get() = t("Plant recognition failed. Try another photo.", "Rozpoznání rostliny se nepodařilo. Zkus jinou fotografii.", "Rozpoznanie rośliny nie powiodło się. Spróbuj innego zdjęcia.")
    val plantHealthFailed get() = t("Plant disease assessment failed. Try another photo.", "Posouzení choroby rostliny se nepodařilo. Zkus jinou fotografii.", "Ocena choroby rośliny nie powiodła się. Spróbuj innego zdjęcia.")
    val plantRecognitionNetworkError get() = t("Plant recognition network error.", "Síťová chyba při rozpoznání rostliny.", "Błąd sieci podczas rozpoznawania rośliny.")
    val plantHealthNetworkError get() = t("Plant disease network error.", "Síťová chyba při zjišťování choroby rostliny.", "Błąd sieci podczas sprawdzania choroby rośliny.")
    val plantTooManyPhotos get() = t("Use at most 5 photos.", "Použij maximálně 5 fotografií.", "Użyj maksymalnie 5 zdjęć.")
    val plantEmptyPhoto get() = t("One of the photos is empty.", "Jedna z fotografií je prázdná.", "Jedno ze zdjęć jest puste.")
    val skipPhoto get() = t("Skip", "Přeskočit", "Pomiń")
    val removePhoto get() = t("Remove photo", "Odebrat fotku", "Usuń zdjęcie")
    val optionalPhoto get() = t("Optional", "Volitelné", "Opcjonalne")
    val requiredPhoto get() = t("Required", "Povinné", "Wymagane")
    val familyLabel get() = t("Family", "Čeleď", "Rodzina")

    // === ACTIONS ===
    val save get() = t("Save", "Uložit", "Zapisz")
    val cancel get() = t("Cancel", "Zrušit", "Anuluj")
    val create get() = t("Create", "Vytvořit", "Utwórz")
    val edit get() = t("Edit", "Upravit", "Edytuj")
    val delete get() = t("Delete", "Smazat", "Usuń")
    val close get() = t("Close", "Zavřít", "Zamknij")
    val confirm get() = t("Confirm", "Potvrdit", "Potwierdź")
    val useCamera get() = t("Use camera", "Použít fotoaparát", "Użyj aparatu")
    val chooseFromGallery get() = t("Choose from gallery", "Vybrat z galerie", "Wybierz z galerii")
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
    val plannedEnd get() = t("Planned end", "Plánovaný konec", "Planowany koniec")
    val plan get() = t("Plan", "Plán", "Plan")
    val planningNote get() = t("Planning note", "Plánovací poznámka", "Notatka planowania")
    val handoverNote get() = t("Handover note", "Poznámka k předání", "Notatka przekazania")
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
    val systemLanguage get() = t("System language", "Jazyk systému", "Język systemu")
    val systemLanguageHint get() = t("Saved locally for the currently signed-in user.", "Ukládá se lokálně pro právě přihlášeného uživatele.", "Zapisywany lokalnie dla aktualnie zalogowanego użytkownika.")
    val customerLanguageHint get() = t("Default language used for customers and outgoing communication.", "Výchozí jazyk pro zákazníky a odchozí komunikaci.", "Domyślny język dla klientów i komunikacji wychodzącej.")
    val adminOnlyLanguageHint get() = t("Only an administrator can change customer language.", "Zákaznický jazyk může změnit jen administrátor.", "Język klienta może zmienić tylko administrator.")
    val changeLanguage get() = t("Change language", "Změnit jazyk", "Zmień język")
    val languageChangeConfirm get() = t("Change app language to", "Změnit jazyk aplikace na", "Zmienić język aplikacji na")
    val logout get() = t("Log out", "Odhlásit se", "Wyloguj się")
    val login get() = t("Sign in", "Přihlášení", "Logowanie")
    val password get() = t("Password", "Heslo", "Hasło")
    val showPassword get() = t("Show password", "Zobrazit heslo", "Pokaż hasło")
    val signInWithFingerprint get() = t("Sign in with fingerprint", "Přihlaste se otiskem prstu", "Zaloguj się odciskiem palca")
    val fingerprint get() = t("Fingerprint", "Otisk prstu", "Odcisk palca")
    val loginWithPassword get() = t("Sign in with password", "Přihlásit se heslem", "Zaloguj się hasłem")
    val backToFingerprint get() = t("Back to fingerprint", "Zpět na otisk prstu", "Powrót do odcisku palca")
    val biometricUnavailable get() = t("Biometrics are not available on this device", "Biometrie není k dispozici na tomto zařízení", "Biometria nie jest dostępna na tym urządzeniu")
    val loginFirstWithPassword get() = t("First sign in with password", "Nejdříve se přihlaste heslem", "Najpierw zaloguj się hasłem")
    val fillEmailPassword get() = t("Enter email and password", "Vyplňte email a heslo", "Wpisz email i hasło")
    val appTheme get() = t("App theme", "Motiv aplikace", "Motyw aplikacji")
    val accordingToSystem get() = t("Follow system", "Podle systému", "Zgodnie z systemem")
    val lightTheme get() = t("Light", "Světlý", "Jasny")
    val darkTheme get() = t("Dark", "Tmavý", "Ciemny")
    val themeRestartHint get() = t("Change takes effect after app restart", "Změna se projeví po restartu aplikace", "Zmiana zacznie działać po restarcie aplikacji")
    val voiceControl get() = t("Voice control", "Hlasové ovládání", "Sterowanie głosem")
    val hotwordDetection get() = t("Wake word detection", "Detekce aktivačního slova", "Wykrywanie słowa aktywującego")
    val hotwordListeningHint get() = t("Listens for the hotword", "Naslouchá na hot word", "Nasłuchuje słowa aktywującego")
    val activationWord get() = t("Wake word", "Aktivační slovo", "Słowo aktywujące")
    val speechRate get() = t("Speech rate", "Rychlost řeči", "Szybkość mowy")
    val voicePitch get() = t("Voice pitch", "Výška hlasu", "Wysokość głosu")
    val silenceLengthLabel get() = t("Silence length", "Délka ticha", "Długość ciszy")
    val serverConnection get() = t("Server and connection", "Server a připojení", "Serwer i połączenie")
    val crmSettingsLabel get() = t("CRM", "CRM", "CRM")
    val autoRefreshLabel get() = t("Auto refresh", "Automatické obnovení", "Automatyczne odświeżanie")
    val apiServerUrl get() = t("API server URL", "URL API serveru", "URL serwera API")
    val connected get() = t("Connected", "Připojeno", "Połączono")
    val disconnected get() = t("Disconnected", "Odpojeno", "Rozłączono")
    val testing get() = t("Testing...", "Testuji...", "Testuję...")
    val unknown get() = t("Unknown", "Neznám", "Nieznany")
    val serverUnavailable get() = t("Server unavailable", "Server nedostupný", "Serwer niedostępny")
    val checkUrlAndServer get() = t("Check the URL and make sure the server is running", "Zkontrolujte URL a že server běží", "Sprawdź adres URL i upewnij się, że serwer działa")
    val testConnection get() = t("Test connection", "Testovat spojení", "Testuj połączenie")
    val offlineMode get() = t("Offline mode", "Offline mód", "Tryb offline")
    val offlineQueueHint get() = t("Queue commands until connection returns", "Fronta příkazů až do připojení", "Kolejkuj polecenia do czasu przywrócenia połączenia")
    val usersAndPermissions get() = t("Users and permissions", "Uživatelé a práva", "Użytkownicy i uprawnienia")
    val role get() = t("Role", "Role", "Rola")
    val adminPassword get() = t("Admin password", "Heslo správce", "Hasło administratora")
    val enterAdminPassword get() = t("Enter admin password", "Zadejte heslo správce", "Wpisz hasło administratora")
    val verify get() = t("Verify", "Ověřit", "Zweryfikuj")
    val setAdminPassword get() = t("Set admin password", "Nastavit heslo správce", "Ustaw hasło administratora")
    val changePassword get() = t("Change password", "Změnit heslo", "Zmień hasło")
    val active get() = t("Active", "Aktivní", "Aktywny")
    val switchUser get() = t("Switch", "Přepnout", "Przełącz")
    val createBackendUser get() = t("Create user", "Vytvořit uživatele", "Utwórz użytkownika")
    val addLocalProfile get() = t("Add local profile", "Přidat lokální profil", "Dodaj profil lokalny")
    val backendUserCreated get() = t("Backend user created", "Backendový uživatel byl vytvořen", "Użytkownik backendu został utworzony")
    val backendUserUpdated get() = t("Backend user updated", "Backendový uživatel byl upraven", "Użytkownik backendu został zaktualizowany")
    val backendUserDeleted get() = t("Backend user deleted", "Backendový uživatel byl smazán", "Użytkownik backendu został usunięty")
    val backendUserHint get() = t("These are real server accounts used for login and permissions.", "Tohle jsou skutečné serverové účty pro přihlášení a oprávnění.", "To są prawdziwe konta serwerowe używane do logowania i uprawnień.")
    val backendUsersLabel get() = t("Backend users", "Backendoví uživatelé", "Użytkownicy backendu")
    val backendUsersEmpty get() = t("No backend users found", "Nebyli nalezeni žádní backendoví uživatelé", "Nie znaleziono użytkowników backendu")
    val backendUsersLoadFailed get() = t("Failed to load backend users", "Nepodařilo se načíst backendové uživatele", "Nie udało się wczytać użytkowników backendu")
    val presetProfilesLabel get() = t("Preset role profiles", "Přednastavené profily rolí", "Gotowe profile ról")
    val presetProfilesHint get() = t("These presets define the default rights for each role.", "Tyto profily určují výchozí práva pro každou roli.", "Te profile określają domyślne uprawnienia dla każdej roli.")
    val localProfilesLabel get() = t("Local profiles on this device", "Lokální profily v tomto zařízení", "Profile lokalne na tym urządzeniu")
    val localProfilesHint get() = t("These profiles only affect this phone and do not change server permissions.", "Tyto profily ovlivňují jen tento telefon a nemění serverová oprávnění.", "Te profile wpływają tylko na ten telefon i nie zmieniają uprawnień na serwerze.")
    val roleControlsPermissionsHint get() = t("Pick a role, then you can fine-tune permissions for this user below.", "Vyberte roli a níže můžete práva tohoto uživatele doladit individuálně.", "Wybierz rolę, a poniżej możesz indywidualnie dopracować uprawnienia użytkownika.")
    val currentPassword get() = t("Current password", "Současné heslo", "Obecne hasło")
    val newPassword get() = t("New password", "Nové heslo", "Nowe hasło")
    val confirmPassword get() = t("Confirm password", "Potvrdit heslo", "Potwierdź hasło")
    val passwordsDoNotMatch get() = t("Passwords do not match", "Neshodují se", "Hasła nie są zgodne")
    val permissions get() = t("Permissions", "Oprávnění", "Uprawnienia")
    val includedPermissions get() = t("Included permissions", "Obsažená práva", "Zawarte uprawnienia")
    val permissionOverrides get() = t("Individual overrides", "Individuální override", "Indywidualne nadpisania")
    val resetToRoleDefaults get() = t("Reset to role defaults", "Vrátit na výchozí práva role", "Przywróć domyślne uprawnienia roli")
    val noPermissionsAssigned get() = t("No permissions assigned", "Žádná práva nepřiřazena", "Brak przypisanych uprawnień")
    val customPermissionsActive get() = t("Custom rights active", "Aktivní vlastní práva", "Aktywne własne uprawnienia")
    val roleDescription get() = t("Role description", "Popis role", "Opis roli")
    val nameField get() = t("Name", "Jméno", "Nazwa")
    val users get() = t("Users", "Uživatelé", "Użytkownicy")
    val createUserFailed get() = t("User creation failed", "Vytvoření uživatele selhalo", "Tworzenie użytkownika nie powiodło się")
    val updateUserFailed get() = t("User update failed", "Úprava uživatele selhala", "Aktualizacja użytkownika nie powiodła się")
    val deleteUserFailed get() = t("User deletion failed", "Smazání uživatele selhalo", "Usunięcie użytkownika nie powiodło się")
    val reload get() = t("Reload", "Načíst znovu", "Wczytaj ponownie")
    val createLocalProfile get() = t("Create local profile", "Vytvořit lokální profil", "Utwórz profil lokalny")
    val editLocalProfile get() = t("Edit local profile", "Upravit lokální profil", "Edytuj profil lokalny")
    val serverAccount get() = t("Server account", "Serverový účet", "Konto serwerowe")
    val localProfile get() = t("Local profile", "Lokální profil", "Profil lokalny")
    val dataStorage get() = t("Data and storage", "Data a úložiště", "Dane i pamięć")
    val clearHistory get() = t("Clear history", "Vymazat historii", "Wyczyść historię")
    val restoreDefaults get() = t("Restore defaults", "Obnovit výchozí", "Przywróć domyślne")
    val clearHistoryQuestion get() = t("Clear history?", "Vymazat historii?", "Wyczyścić historię?")
    val restoreDefaultsQuestion get() = t("Restore default settings?", "Obnovit výchozí nastavení?", "Przywrócić ustawienia domyślne?")
    val exportCrmCsv get() = t("Export CRM (CSV)", "Exportovat CRM (CSV)", "Eksportuj CRM (CSV)")
    val importContactsFromPhone get() = t("Import contacts from phone", "Import kontaktů z telefonu", "Importuj kontaty z telefonu")
    val onlyUkNumbers get() = t("UK numbers only (+44, 07, 01, 02)", "Pouze UK čísla (+44, 07, 01, 02)", "Tylko numery UK (+44, 07, 01, 02)")
    val allNumbers get() = t("All numbers", "Všechna čísla", "Wszystkie numery")
    val importContacts get() = t("Import contacts", "Importovat kontakty", "Importuj kontakty")
    val syncStarted get() = t("Synchronization started...", "Synchronizace spuštěna...", "Synchronizacja uruchomiona...")
    val importDatabase get() = t("Database import", "Import databáze", "Import bazy danych")
    val csvPath get() = t("CSV path", "Cesta k CSV", "Ścieżka do CSV")
    val table get() = t("Table", "Tabulka", "Tabela")
    val autoImportOnStartup get() = t("Auto import on startup", "Automatický import při spuštění", "Automatyczny import przy uruchomieniu")
    val startImport get() = t("Start import", "Spustit import", "Uruchom import")
    val voiceImportHint get() = t("Voice: 'import client database'", "Hlasem: 'importuj databázi klientů'", "Głosowo: 'importuj bazę klientów'")
    val companyProfile get() = t("Company profile", "Profil firmy", "Profil firmy")
    val serviceRates get() = t("Service rates", "Sazby služeb", "Stawki usług")
    val individualServiceRates get() = t("Individual service rates", "Individuální sazby služeb", "Indywidualne stawki usług")
    val individualServiceRatesHint get() = t(
        "These rates belong only to this client and override company service rates.",
        "Tyto sazby patří jen tomuto klientovi a mají prioritu před firemními sazbami služeb.",
        "Te stawki dotyczą tylko tego klienta i mają priorytet przed firmowymi stawkami usług."
    )
    val individualServiceRatesNotSet get() = t(
        "No client-specific rates yet. Company service rates are used.",
        "Klient zatím nemá vlastní sazby. Používají se firemní sazby služeb.",
        "Klient nie ma jeszcze własnych stawek. Używane są firmowe stawki usług."
    )
    val individualServiceRatesActive get() = t(
        "Client-specific service rates are active.",
        "Aktivní jsou individuální sazby služeb tohoto klienta.",
        "Aktywne są indywidualne stawki usług dla tego klienta."
    )
    val resetToCompanyRates get() = t("Reset to company rates", "Obnovit firemní sazby", "Przywróć stawki firmowe")
    val hourlyRatesByType get() = t("Hourly rates by work type", "Hodinové sazby podle typu práce", "Stawki godzinowe według typu pracy")
    val otherRates get() = t("Other rates", "Ostatní sazby", "Pozostałe stawki")
    val gardenMaintenanceRate get() = t("Garden Maintenance (£/h)", "Garden Maintenance (£/h)", "Garden Maintenance (£/h)")
    val gardenMaintenanceHint get() = t("Cleaning, weeding, planting, grass strimming", "Úklid, pletí, sázení, strunová sekačka", "Czyszczenie, pielenie, sadzenie, podkaszanie")
    val hedgeTrimmingRate get() = t("Hedge Trimming & Pruning (£/h)", "Stříhání živých plotů a prořez (£/h)", "Przycinanie żywopłotów i cięcie (£/h)")
    val arboristWorksRate get() = t("Arboristic Works / Tree Surgeon (£/h)", "Arboristické práce / Tree Surgeon (£/h)", "Prace arborystyczne / Tree Surgeon (£/h)")
    val wasteRemovalRate get() = t("Waste removal per bulk bag (£)", "Odvoz odpadu za bulk bag (£)", "Wywóz odpadów za bulk bag (£)")
    val minimumJobPrice get() = t("Minimum job price (£)", "Minimální cena zakázky (£)", "Minimalna cena zlecenia (£)")
    val notificationsLabel get() = t("Notifications", "Notifikace", "Powiadomienia")
    val workProfileLabel get() = t("Work profile", "Pracovní profil", "Profil pracy")
    val emailSignatures get() = t("Email signatures", "Emailové podpisy", "Podpisy email")
    val activeSignature get() = t("Active signature", "Aktivní podpis", "Aktywny podpis")
    val signatureName get() = t("Name", "Název", "Nazwa")
    val signatureContent get() = t("Signature content", "Obsah podpisu", "Treść podpisu")
    val newSignature get() = t("New", "Nový", "Nowy")
    val crmTabLabel get() = t("Default tab", "Výchozí tab", "Domyślna karta")
    val sorting get() = t("Sorting", "Řazení", "Sortowanie")
    val persistentNotificationLabel get() = t("Persistent notification", "Trvalá notifikace", "Stałe powiadomienie")
    val taskReminder get() = t("Task reminder", "Připomenutí úkolu", "Przypomnienie o zadaniu")
    val workHours get() = t("Work hours", "Pracovní hodiny", "Godziny pracy")
    val hotwordDuringWorkHours get() = t("Wake word only during work hours", "Hotword jen v pracovní době", "Słowo aktywujące tylko w godzinach pracy")
    val startLabel get() = t("Start", "Začátek", "Początek")
    val progressLabel get() = t("Progress", "Průběh", "Przebieg")
    val endLabel get() = t("End", "Konec", "Koniec")
    val complicationsLabel get() = t("Complications", "Komplikace", "Komplikacje")
    val timestampLabel get() = t("Timestamp", "Čas", "Czas")
    val userLabel get() = t("User", "Uživatel", "Użytkownik")
    val actionLabel get() = t("Action", "Akce", "Akcja")
    val auditLog get() = t("Audit Log", "Historie změn", "Log zmian")
    val defaultPriorityLabel get() = t("Default priority", "Výchozí priorita", "Domyślny priorytet")
    val workspaceMode get() = t("Workspace mode", "Režim", "Tryb pracy")
    val internalLanguage get() = t("Internal language", "Interní jazyk", "Język wewnętrzny")
    val customerLanguage get() = t("Customer language", "Zákaznický jazyk", "Język klienta")
    val internalLanguageMode get() = t("Internal language mode", "Int. jazykový mód", "Tryb języka wewnętrznego")
    val customerLanguageMode get() = t("Customer language mode", "Zák. jazykový mód", "Tryb języka klienta")
    val limits get() = t("Limits", "Limity", "Limity")
    val maxUsers get() = t("Max users", "Max uživatelů", "Maks. użytkowników")
    val maxClients get() = t("Max clients", "Max klientů", "Maks. klientów")
    val maxJobsPerMonth get() = t("Max jobs/month", "Max zakázek/měsíc", "Maks. zleceń/miesiąc")
    val voiceMinutes get() = t("Voice minutes", "Hlasové minuty", "Minuty głosowe")
    val aboutApp get() = t("About app", "O aplikaci", "O aplikacji")
    val versionLabel get() = t("Version", "Verze", "Wersja")
    val releaseDate get() = t("Release date", "Datum vydání", "Data vydania")
    val packageLabel get() = t("Package", "Balíček", "Pakiet")
    val backendLabel get() = t("Backend", "Backend", "Backend")
    val aiEngineLabel get() = t("AI engine", "AI engine", "Silnik AI")
    val minSdkLabel get() = t("Min SDK", "Min SDK", "Min SDK")
    val targetSdkLabel get() = t("Target SDK", "Target SDK", "Target SDK")
    val ready get() = t("Ready", "Připravena", "Gotowe")
    val waitingForYourCommand get() = t("Waiting for your command...", "Čekám na váš povel...", "Czekam na Twoje polecenie...")
    val speechRecognitionUnavailable get() = t("Speech recognition not available", "Rozpoznávání řeči není k dispozici", "Rozpoznawanie mowy nie jest dostępne")
    val connectionError get() = t("Connection error to server.", "Chyba spojení se serverem.", "Błąd połączenia z serwerem.")
    val cantReachServer get() = t("I can't connect to the server.", "Nemůžu se spojit se serverem.", "Nie mogę połączyć się z serwerem.")
    val settingsRestored get() = t("Settings restored", "Nastavení obnovena", "Ustawienia przywrócone")
    val exportUnavailable get() = t("Export is not available in this version", "Export není v této verzi", "Eksport nie jest dostępny w tej wersji")
    val pathNotSet get() = t("Path is not set", "Cesta není nastavena", "Ścieżka nie jest ustawiona")
    val importStarted get() = t("Import started", "Import spuštěn", "Import uruchomiony")
    val backgroundDisabled get() = t("Background disabled", "Pozadí vypnuto", "Działanie w tle wyłączone")
    val backgroundListening get() = t("Listening in background", "Poslouchám na pozadí", "Nasłuchuję w tle")
    val hotwordDisabledStatus get() = t("Wake word detection is disabled", "Detekce aktivačního slova je vypnutá", "Wykrywanie słowa aktywującego jest wyłączone")
    val outsideWorkHoursStatus get() = t("Wake word is paused outside work hours", "Aktivační slovo je mimo pracovní dobu pozastavené", "Słowo aktywujące jest poza godzinami pracy wstrzymane")
    val restarting get() = t("Restarting...", "Restartuji...", "Uruchamiam ponownie...")
    val foundContacts get() = t("FOUND CONTACTS", "NALEZENÉ KONTAKTY", "ZNALEZIONE KONTAKTY")
    val importTitle get() = t("Import contacts", "Import kontaktů", "Import kontaktów")
    val skipWithoutPhone get() = t("Skip without phone", "Přeskočit bez telefonu", "Pomiń bez telefonu")
    val skipWithoutName get() = t("Skip without name", "Přeskočit bez jména", "Pomiń bez imienia")
    val removeDuplicates get() = t("Remove duplicates", "Odstranit duplicity", "Usuń duplikaty")
    val includeEmail get() = t("Include email", "Zahrnout e-mail", "Uwzględnij email")
    val workDate get() = t("Date", "Datum", "Data")
    val financeLabel get() = t("Finance", "Finance", "Finanse")
    val wasteLabel get() = t("Waste", "Odpady", "Odpady")
    val activityLabel get() = t("Activity", "Aktivita", "Aktywność")
    val totalHours get() = t("Total hours", "Celkem hodin", "Łącznie godzin")
    val totalPrice get() = t("Total price", "Celkem", "Cena łączna")
    val quote get() = t("Quote", "Nabídka", "Oferta")
    val newQuote get() = t("New quote", "Nová nabídka", "Nowa oferta")
    val addItem get() = t("Add item", "Přidat položku", "Dodaj pozycję")
    val approve get() = t("Approve", "Schválit", "Zatwierdź")
    val approveQuoteQuestion get() = t("Approve quote?", "Schválit nabídku?", "Zatwierdzić ofertę?")
    val approveCreatesJob get() = t("Approval will create a new job.", "Schválení vytvoří novou zakázku.", "Zatwierdzenie utworzy nowe zlecenie.")
    val approveAndCreateJob get() = t("Approve + create job", "Schválit + vytvořit zakázku", "Zatwierdź + utwórz zlecenie")
    val quantity get() = t("Quantity", "Množství", "Ilość")
    val unitPrice get() = t("Unit price", "Cena za jednotku", "Cena za jednostkę")
    val displayName get() = t("Display name", "Zobrazované jméno", "Nazwa wyświetlana")
    val messageLabel get() = t("Message", "Zpráva", "Wiadomość")
    val directionLabel get() = t("Direction", "Směr", "Kierunek")
    val typeLabel get() = t("Type", "Typ", "Typ")
    val newCommunication get() = t("New communication", "Nová komunikace", "Nowa komunikacja")
    val editJob get() = t("Edit job", "Upravit zakázku", "Edytuj zlecenie")
    val addJobNote get() = t("Add job note", "Přidat poznámku k zakázce", "Dodaj notatkę do zlecenia")
    val editLead get() = t("Edit lead", "Upravit lead", "Edytuj lead")
    val newInvoice get() = t("New invoice", "Nová faktura", "Nowa faktura")
    val changeInvoiceStatus get() = t("Change invoice status", "Změnit stav faktury", "Zmień status faktury")
    val newWorkReport get() = t("New work report", "Nový výkaz práce", "Nowy raport pracy")
    val loadingCalendar get() = t("Loading calendar...", "Načítám kalendář...", "Wczytuję kalendarz...")
    val scheduledTasksLabel get() = t("Scheduled tasks", "Naplánované úkoly", "Zaplanowane zadania")
    val sharedPlanningLabel get() = t("Shared planning", "Sdílené plánování", "Plan współdzielony")
    val calendarEventsLabel get() = t("Calendar events", "Události z kalendáře", "Wydarzenia z kalendarza")
    val noCalendarEntries get() = t("No planned items yet", "Zatím nejsou žádné plánované položky", "Brak zaplanowanych pozycji")
    val syncCalendar get() = t("Sync calendar", "Synchronizovat kalendář", "Synchronizuj kalendarz")
    val reminderEntry get() = t("Reminder", "Připomínka", "Przypomnienie")
    val infoEntry get() = t("Info", "Informace", "Informacja")
    val sharedEntry get() = t("Shared", "Sdílené", "Wspólne")
    val backgroundEnabledShort get() = t("Background ON", "Pozadí ZAP", "Tło WŁ")
    val backgroundDisabledShort get() = t("Background OFF", "Pozadí VYP", "Tło WYŁ")
    val restartShort get() = t("Restart", "Restart", "Restart")
    val closeApp get() = t("Close", "Zavřít", "Zamknij")
    val you get() = t("You", "Vy", "Ty")
    val assistant get() = t("Assistant", "Sekretářka", "Asystent")
    val noCrmData get() = t("No data in CRM", "Žádná data v CRM", "Brak danych w CRM")
    val noTaskFound get() = t("Task not found", "Úkol nenalezen", "Nie znaleziono zadania")
    val photoAction get() = t("Photo", "Foto", "Zdjęcie")
    val addToCalendarAction get() = t("Add to calendar", "Do kalendáře", "Dodaj do kalendarza")
    val ratesTitle get() = t("Rates", "Sazby", "Stawki")
    val hourlyRateLabel get() = t("Hourly rate (£/h)", "Hodinová sazba (£/h)", "Stawka godzinowa (£/h)")
    val noCommunications get() = t("No communications", "Žádná komunikace", "Brak komunikacji")
    val logCommunicationAction get() = t("Log communication", "Zalogovat komunikaci", "Dodaj komunikację")
    val noSubject get() = t("No subject", "Bez předmětu", "Bez tematu")
    val selectAll get() = t("Select all", "Vybrat vše", "Zaznacz wszystko")
    val leadFallback get() = t("Lead", "Lead", "Lead")
    val versionHistory get() = t("Version history", "Historie verzí", "Historia wersji")
    val authorAndCode get() = t("Author and code", "Autor a kód", "Autor i kod")
    val structureAndCode get() = t("Structure and code", "Struktura a kód", "Struktura i kod")
    val webLabel get() = t("Web", "Web", "WWW")
    val companyLabel get() = t("Company", "Firma", "Firma")
    val codingLabel get() = t("Coding", "Kódování", "Kodowanie")
    val technologies get() = t("Technologies", "Technologie", "Technologie")
    val platformLabel get() = t("Platform", "Platforma", "Platforma")
    val architectureLabel get() = t("Architecture", "Architektura", "Architektura")
    val databaseLabel get() = t("Database", "Databáze", "Baza danych")
    val projectStructureLabel get() = t("Project structure", "Struktura projektu", "Struktura projektu")
    val licenseTitle get() = t("License", "Licence", "Licencja")
    val versioningRules get() = t("Versioning rules", "Pravidla verzování", "Zasady wersjonowania")
    val mandatoryStepsOnChange get() = t("Mandatory steps when changing", "Povinné kroky při změně", "Obowiązkowe kroki przy zmianie")
    val changelogTitle get() = t("Changelog", "Changelog", "Changelog")
    val authorLabel get() = t("Author", "Autor", "Autor")
    val knownIssuesLabel get() = t("Known issues:", "Známé problémy:", "Znane problemy:")
    val back get() = t("Back", "Zpět", "Wstecz")
    val next get() = t("Next", "Další", "Dalej")
    val finish get() = t("Finish", "Dokončit", "Zakończ")
    val companyNameRequired get() = t("Company name *", "Název firmy *", "Nazwa firmy *")
    val legalForm get() = t("Legal form", "Právní forma", "Forma prawna")
    val chooseIndustry get() = t("Choose business field *", "Vyberte obor podnikání *", "Wybierz branżę *")
    val specialization get() = t("Specialization", "Specializace", "Specjalizacja")
    val internalLanguageCompany get() = t("Internal language (inside company)", "Interní jazyk (ve firmě)", "Język wewnętrzny (w firmie)")
    val internalLanguageQuestion get() = t("How do you communicate inside the company?", "Jak komunikujete uvnitř firmy?", "Jak komunikujecie się wewnątrz firmy?")
    val singleLanguage get() = t("Single language", "Jeden jazyk", "Jeden język")
    val multipleLanguages get() = t("Multiple languages", "Více jazyků", "Wiele języków")
    val customerLanguageQuestion get() = t("What language do you use with customers?", "V jakém jazyce komunikujete se zákazníky?", "W jakim języku komunikujecie się z klientami?")
    val primaryInternalLanguage get() = t("Primary internal language", "Hlavní interní jazyk", "Główny język wewnętrzny")
    val primaryCustomerLanguage get() = t("Primary customer language", "Hlavní jazyk pro zákazníky", "Główny język dla klientów")
    val companySize get() = t("Company size", "Velikost firmy", "Wielkość firmy")
    val soleTrader get() = t("Sole trader", "Živnostník", "Jednoosobowa działalność")
    val ltdCompany get() = t("Ltd (Company)", "Ltd (společnost)", "Spółka Ltd")
    val partnership get() = t("Partnership", "Partnerství", "Partnerstwo")
    val otherOption get() = t("Other", "Jiné", "Inne")
    val workspaceSoloDesc get() = t("One user, ideal for sole traders", "Jeden uživatel, ideální pro živnostníky", "Jeden użytkownik, idealne dla jednoosobowej działalności")
    val workspaceTeamDesc get() = t("2-5 users, small team", "2-5 uživatelů, malý tým", "2-5 użytkowników, mały zespół")
    val workspaceBusinessDesc get() = t("6-30 users, larger company", "6-30 uživatelů, větší firma", "6-30 użytkowników, większa firma")
    val enterCompanyNameError get() = t("Enter the company name", "Zadejte název firmy", "Wpisz nazwę firmy")
    val selectIndustryError get() = t("Select an industry", "Vyberte obor", "Wybierz branżę")

    // === VOICE ===
    val listening get() = t("Listening", "Poslouchám", "Słucham")
    val waitingForCommand get() = t("Waiting for command...", "Čekám na povel...", "Czekam na polecenie...")
    val processing get() = t("Processing...", "Zpracovávám...", "Przetwarzam...")
    val history get() = t("HISTORY", "HISTORIE", "HISTORIA")
    val backgroundActive get() = t("Background active", "Na pozadí aktivní", "Aktywne w tle")
    val backgroundInactive get() = t("Background inactive", "Na pozadí neaktivní", "Nieaktywne w tle")

    // === HELPER ===
    fun t(en: String, cs: String, pl: String): String = when (activeLang) {
        Lang.EN -> en; Lang.CS -> cs; Lang.PL -> pl
    }

    fun languageDisplayName(code: String): String = when (fromCode(code)) {
        Lang.EN -> "English"
        Lang.CS -> "Čeština"
        Lang.PL -> "Polski"
    }

    fun biometricError(err: String): String = t("Biometric error: $err", "Chyba biometrie: $err", "Błąd biometrii: $err")
    fun serverVersion(version: String): String = t("Server: $version", "Server: $version", "Serwer: $version")
    fun addedToCalendar(taskTitle: String): String = t("Added to calendar: $taskTitle", "Přidáno do kalendáře: $taskTitle", "Dodano do kalendarza: $taskTitle")
    fun planningCalendarSynced(count: Int): String = t("Planning synced: $count entries", "Plánování synchronizováno: $count položek", "Plan zsynchronizowany: $count pozycji")
    val planningCalendarSyncFailed get() = t("Planning calendar sync failed", "Synchronizace plánovacího kalendáře selhala", "Synchronizacja kalendarza planowania nie powiodła się")
    fun serverError(code: Int): String = t("Server error $code", "Chyba serveru $code", "Błąd serwera $code")
    fun noContactFound(query: String): String = t("I couldn't find anyone in contacts for '$query'.", "V kontaktech jsem nikoho pro '$query' nenašla.", "Nie znalazłam nikogo w kontaktach dla '$query'.")
    fun backendPermissionDenied(): String = t(
        "The current account is not allowed to manage users.",
        "Aktuální účet nemá oprávnění spravovat uživatele.",
        "Bieżące konto nie ma uprawnień do zarządzania użytkownikami."
    )
    fun backendUserAlreadyExists(): String = t(
        "A user with this email already exists.",
        "Uživatel s tímto e-mailem už existuje.",
        "Użytkownik z tym adresem e-mail już istnieje."
    )
    fun backendCannotDeleteSelf(): String = t(
        "You cannot delete the account you are currently using.",
        "Nemůžete smazat účet, pod kterým jste právě přihlášený.",
        "Nie możesz usunąć konta, z którego obecnie korzystasz."
    )
    fun backendUserNotFound(): String = t(
        "The selected user was not found.",
        "Vybraný uživatel nebyl nalezen.",
        "Nie znaleziono wybranego użytkownika."
    )
    fun backendUnknownRole(): String = t(
        "The selected role is not available on the server.",
        "Vybraná role není na serveru dostupná.",
        "Wybrana rola nie jest dostępna na serwerze."
    )
    fun backendNothingToUpdate(): String = t(
        "There are no changes to save.",
        "Nejsou žádné změny k uložení.",
        "Brak zmian do zapisania."
    )
    fun onboardingTitle(step: Int, total: Int): String = t(
        "Company setup — step $step/$total",
        "Nastavení firmy — krok $step/$total",
        "Konfiguracja firmy — krok $step/$total"
    )
    fun onboardingStepTitle(index: Int): String = when (index) {
        0 -> t("Company", "Firma", "Firma")
        1 -> t("Industry", "Obor", "Branża")
        2 -> t("Languages", "Jazyky", "Języki")
        3 -> t("Default languages", "Výchozí jazyky", "Domyślne języki")
        4 -> t("Workspace", "Režim", "Tryb pracy")
        else -> ""
    }
    fun historySpeaker(role: String): String = if (role == "user") you else assistant
    fun backendActionFailed(actionLabel: String, code: Int): String = "$actionLabel (HTTP $code)"
    fun clientServiceRatesSummary(active: Int): String = t(
        "$active client-specific rates set",
        "Nastaveno $active individuálních sazeb",
        "Ustawiono $active indywidualnych stawek"
    )
    fun invoiceDueDate(value: String?): String = t("Due: ${value ?: "?"}", "Splatnost: ${value ?: "?"}", "Termin: ${value ?: "?"}")
    fun batchInvoiceLabel(count: Int): String = t("Invoice ${count}x", "Fakturovat ${count}×", "Fakturuj ${count}×")
    fun invoicesCreatedSummary(created: Int, errors: Int): String = t(
        "Created $created invoices" + if (errors > 0) ", $errors errors" else "",
        "Vytvořeno $created faktur" + if (errors > 0) ", $errors chyb" else "",
        "Utworzono $created faktur" + if (errors > 0) ", $errors błędów" else ""
    )
    fun workReportUnknownClient(clientId: Long?): String = t(
        "Client #${clientId ?: "?"}",
        "Klient #${clientId ?: "?"}",
        "Klient #${clientId ?: "?"}"
    )
    fun wasteSummary(quantity: Int, total: String): String = t(
        "Waste: $quantity bags · £$total",
        "Odpad: $quantity pytlů · £$total",
        "Odpad: $quantity worków · £$total"
    )
    fun invoiceCreatedSuccess(invoiceNumber: Any?, grandTotal: Any?, profit: Double, margin: Double): String = t(
        "Invoice ${invoiceNumber ?: "-"}: £${"%.0f".format((grandTotal as? Number)?.toDouble() ?: 0.0)}, profit £${"%.0f".format(profit)} ($margin%)",
        "Faktura ${invoiceNumber ?: "-"}: £${"%.0f".format((grandTotal as? Number)?.toDouble() ?: 0.0)}, zisk £${"%.0f".format(profit)} ($margin%)",
        "Faktura ${invoiceNumber ?: "-"}: £${"%.0f".format((grandTotal as? Number)?.toDouble() ?: 0.0)}, zysk £${"%.0f".format(profit)} ($margin%)"
    )
    fun invoiceCreateError(): String = t(
        "Error — has this work report already been invoiced?",
        "Chyba — výkaz už byl fakturován?",
        "Błąd — ten raport został już zafakturowany?"
    )
    fun showChanges(count: Int): String = t("Show changes ($count)", "Zobrazit změny ($count)", "Pokaż zmiany ($count)")
    fun hideChanges(): String = t("Hide changes", "Skrýt změny", "Ukryj zmiany")
    fun localizeVersionRuleType(type: String): String = when (type.uppercase()) {
        "PATCH" -> t("FIX", "OPRAVA", "POPRAWKA")
        "MINOR" -> t("FEATURE", "FUNKCE", "FUNKCJA")
        "MAJOR" -> t("ARCHITECTURE", "ARCHITEKTURA", "ARCHITEKTURA")
        else -> type
    }
    fun versionTypeLabel(type: ChangeType): String = when (type) {
        ChangeType.PATCH -> t("FIX", "OPRAVA", "POPRAWKA")
        ChangeType.MINOR -> t("FEATURE", "FUNKCE", "FUNKCJA")
        ChangeType.MAJOR -> t("ARCHITECTURE", "ARCHITEKTURA", "ARCHITEKTURA")
    }
    fun localizeRole(role: String): String = when (role.lowercase()) {
        "admin" -> t("Admin", "Správce", "Administrator")
        "manager" -> t("Manager", "Manažer", "Menedżer")
        "worker" -> t("Worker", "Pracovník", "Pracownik")
        "assistant" -> t("Assistant", "Asistent", "Asystent")
        else -> t("Viewer", "Náhled", "Podgląd")
    }
    fun localizeRoleDescription(role: String, fallback: String? = null): String = when (role.lowercase()) {
        "admin" -> t("Full access to users, settings and all CRM operations.", "Plný přístup k uživatelům, nastavení a všem CRM operacím.", "Pełny dostęp do użytkowników, ustawień i wszystkich operacji CRM.")
        "manager" -> t("Manages clients, jobs, planning and team work.", "Spravuje klienty, zakázky, plánování a týmovou práci.", "Zarządza klientami, zleceniami, planowaniem i pracą zespołu.")
        "worker" -> t("Works with own tasks, reports, calendar and photos.", "Pracuje se svými úkoly, výkazy, kalendářem a fotkami.", "Pracuje z własnymi zadaniami, raportami, kalendarzem i zdjęciami.")
        "assistant" -> t("Broad operational access with limited destructive actions.", "Široký provozní přístup s omezenými destruktivními akcemi.", "Szeroki dostęp operacyjny z ograniczonymi akcjami destrukcyjnymi.")
        "viewer" -> t("Read-only access to shared CRM data.", "Pouze čtení sdílených CRM dat.", "Dostęp tylko do odczytu współdzielonych danych CRM.")
        else -> fallback ?: ""
    }
    fun localizeThemeMode(mode: String): String = when (mode) {
        "system" -> accordingToSystem
        "light" -> lightTheme
        "dark" -> darkTheme
        else -> mode
    }
    fun localizeDirection(direction: String): String = when (direction.lowercase()) {
        "outbound", "outgoing" -> outgoing
        "inbound", "incoming" -> incoming
        else -> direction
    }
    fun localizeCommType(type: String): String = when (type.lowercase()) {
        "telefon", "phone" -> call
        "email" -> "Email"
        "sms" -> "SMS"
        "whatsapp" -> "WhatsApp"
        "checkatrade" -> "Checkatrade"
        "osobne", "in_person" -> visit
        else -> type
    }
    fun localizeLeadSource(source: String): String = when (source.lowercase()) {
        "telefon", "phone" -> call
        "web" -> "Web"
        "checkatrade" -> "Checkatrade"
        "doporuceni", "referral" -> t("Referral", "Doporučení", "Polecenie")
        "jiny", "other" -> t("Other", "Jiný", "Inne")
        else -> source
    }
    fun localizeWorkspaceMode(mode: String): String = when (mode.lowercase()) {
        "solo" -> t("Solo (1 user)", "Solo (1 uživatel)", "Solo (1 użytkownik)")
        "team" -> t("Team (2-5)", "Tým (2-5)", "Zespół (2-5)")
        "business" -> t("Business (6-30)", "Firma (6-30)", "Firma (6-30)")
        else -> mode
    }
    fun localizeLanguageMode(mode: String): String = when (mode.lowercase()) {
        "single" -> t("Single language", "Jeden jazyk", "Jeden język")
        "multi" -> t("Multiple languages", "Více jazyků", "Wiele języków")
        else -> mode
    }
    fun localizeCrmTab(tab: String): String = when (tab.lowercase()) {
        "clients" -> clients
        "properties" -> properties
        "jobs" -> jobs
        "waste" -> wasteLabel
        "finance" -> financeLabel
        else -> tab
    }
    fun localizeClientSortOrder(order: String): String = when (order.lowercase()) {
        "name" -> nameField
        "created" -> created
        "activity" -> activityLabel
        else -> order
    }
    fun localizeAutoRefreshInterval(minutes: Int): String = when {
        minutes <= 0 -> t("Manual", "Ručně", "Ręcznie")
        else -> t("$minutes min", "$minutes min", "$minutes min")
    }
    fun localizeReminderInterval(minutes: Int): String = when {
        minutes <= 0 -> t("Off", "Vypnuto", "Wyłączone")
        minutes % 60 == 0 -> t("${minutes / 60} h", "${minutes / 60} h", "${minutes / 60} h")
        else -> t("$minutes min", "$minutes min", "$minutes min")
    }
    fun localizePriority(priority: String): String = when (priority.lowercase()) {
        "low" -> low
        "normal" -> normal
        "high" -> high
        "urgent" -> urgent
        else -> priority
    }
    fun signatureDefaultName(index: Int = 1): String = t("Signature $index", "Podpis $index", "Podpis $index")
    fun localizeServiceRateKey(key: String): String = when (key.lowercase()) {
        "garden_maintenance", "hourly_rate" -> gardenMaintenanceRate
        "hedge_trimming" -> hedgeTrimmingRate
        "arborist_works" -> arboristWorksRate
        "garden_waste_bulkbag" -> wasteRemovalRate
        "minimum_charge" -> minimumJobPrice
        else -> key
    }
    fun localizeContactSection(sectionCode: String, fallback: String? = null): String = when (sectionCode.lowercase()) {
        "employee" -> employeesSection
        "subcontractor" -> subcontractorsSection
        "material_supplier" -> materialSuppliersSection
        "equipment_vehicle_rental" -> rentalsSection
        else -> fallback ?: sectionCode
    }
    fun localizePlantOrgan(organ: String): String = when (organ.lowercase()) {
        "auto" -> t("Auto", "Automaticky", "Automatycznie")
        "leaf" -> t("Leaf", "List", "Liść")
        "flower" -> t("Flower", "Květ", "Kwiat")
        "fruit" -> t("Fruit", "Plod", "Owoc")
        "bark" -> t("Bark", "Kůra", "Kora")
        else -> organ
    }
    fun localizePermission(code: String, fallback: String = code): String = when (code.lowercase()) {
        "crm_read" -> t("View CRM", "Čtení CRM", "Podgląd CRM")
        "crm_write" -> t("Edit CRM", "Úpravy CRM", "Edycja CRM")
        "crm_delete" -> t("Delete CRM", "Mazání CRM", "Usuwanie CRM")
        "calendar_read" -> t("View calendar", "Čtení kalendáře", "Podgląd kalendarza")
        "calendar_write" -> t("Edit calendar", "Úpravy kalendáře", "Edycja kalendarza")
        "contacts_read" -> t("View contacts", "Čtení kontaktů", "Podgląd kontaktów")
        "contacts_write" -> t("Edit contacts", "Úpravy kontaktů", "Edycja kontaktów")
        "voice_commands" -> t("Voice commands", "Hlasové příkazy", "Polecenia głosowe")
        "settings_access" -> t("Settings access", "Přístup do nastavení", "Dostęp do ustawień")
        "import_data" -> t("Import data", "Import dat", "Import danych")
        "export_data" -> t("Export data", "Export dat", "Eksport danych")
        "manage_users" -> t("Manage users", "Správa uživatelů", "Zarządzanie użytkownikami")
        else -> fallback
    }
    fun localizePermissionDescription(code: String, fallback: String? = null): String = when (code.lowercase()) {
        "crm_read" -> t("Read clients, jobs, leads and invoices.", "Zobrazí klienty, zakázky, leady i faktury.", "Pozwala czytać klientów, zlecenia, leady i faktury.")
        "crm_write" -> t("Create and update CRM records.", "Umožní vytvářet a upravovat CRM záznamy.", "Pozwala tworzyć i edytować rekordy CRM.")
        "crm_delete" -> t("Delete CRM records.", "Umožní mazat CRM záznamy.", "Pozwala usuwać rekordy CRM.")
        "calendar_read" -> t("Read calendar data and availability.", "Umožní číst kalendář a dostupnost.", "Pozwala czytać kalendarz i dostępność.")
        "calendar_write" -> t("Create and update calendar entries.", "Umožní vytvářet a upravovat události v kalendáři.", "Pozwala tworzyć i edytować wpisy kalendarza.")
        "contacts_read" -> t("Read synced contacts and client contact details.", "Umožní číst synchronizované kontakty a kontaktní údaje klientů.", "Pozwala czytać zsynchronizowane kontakty i dane klientów.")
        "contacts_write" -> t("Create and update contact records.", "Umožní vytvářet a upravovat kontakty.", "Pozwala tworzyć i edytować kontakty.")
        "voice_commands" -> t("Use voice commands and guided voice workflows.", "Umožní používat hlasové příkazy a hlasové workflow.", "Pozwala używać poleceń głosowych i procesów głosowych.")
        "settings_access" -> t("Open and change application settings.", "Umožní otevřít a měnit nastavení aplikace.", "Pozwala otwierać i zmieniać ustawienia aplikacji.")
        "import_data" -> t("Run imports and ingest external data.", "Umožní spouštět importy a nahrávat externí data.", "Pozwala uruchamiać import i wczytywać dane zewnętrzne.")
        "export_data" -> t("Export CRM and operational data.", "Umožní exportovat CRM a provozní data.", "Pozwala eksportować CRM i dane operacyjne.")
        "manage_users" -> t("Create users, edit rights and remove users.", "Umožní vytvářet uživatele, měnit jejich práva a mazat je.", "Pozwala tworzyć użytkowników, zmieniać prawa i usuwać ich.")
        else -> fallback ?: ""
    }
    fun matchesLogoutCommand(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return normalized == "logout" ||
            normalized == "log out" ||
            normalized.contains("odhlasit") ||
            normalized.contains("odhlásit") ||
            normalized.contains("wyloguj")
    }
    fun matchesPlantRecognitionCommand(text: String): Boolean {
        val normalized = text.lowercase().trim()
        val phrases = listOf(
            "co je to za rostlinu",
            "rozpoznej rostlinu",
            "poznej rostlinu",
            "identifikuj rostlinu",
            "what plant is this",
            "identify this plant",
            "identify plant",
            "jaka to roslina",
            "rozpoznaj roślinę",
            "rozpoznaj rosline"
        )
        return phrases.any { normalized.contains(it) }
    }
    fun matchesPlantHealthCommand(text: String): Boolean {
        val normalized = text.lowercase().trim()
        val phrases = listOf(
            "co je to za chorobu",
            "co je rostline",
            "co je rostlině",
            "co ji je",
            "co jí je",
            "jaka je to choroba",
            "jaká je to choroba",
            "nemoc rostliny",
            "choroba rostliny",
            "jak lecit tuto rostlinu",
            "jak léčit tuto rostlinu",
            "jak lecit tuhle rostlinu",
            "jak léčit tuhle rostlinu",
            "jak vylecit tuto rostlinu",
            "jak vyléčit tuto rostlinu",
            "jak zachranit tuto rostlinu",
            "jak zachránit tuto rostlinu",
            "jak osetrit tuto rostlinu",
            "jak ošetřit tuto rostlinu",
            "plant disease",
            "what disease is this",
            "what is wrong with this plant",
            "how do i treat this plant",
            "how to treat this plant",
            "how to save this plant",
            "check plant disease",
            "jaka to choroba",
            "co dolega roslinie",
            "co dolega roślinie",
            "choroba rosliny",
            "choroba rośliny",
            "jak leczyc te rosline",
            "jak leczyć tę roślinę",
            "jak uratowac te rosline",
            "jak uratować tę roślinę"
        )
        return phrases.any { normalized.contains(it) }
    }
    fun loggingOutMessage(): String = t("Logging you out. Goodbye!", "Odhlašuji vás. Na shledanou!", "Wylogowuję Cię. Do widzenia!")

    // === STATUS LOCALIZATION ===
    fun localizeStatus(raw: String?): String = when (raw?.lowercase()?.replace(" ","_")) {
        "novy", "nova", "new" -> t("New", "Nový", "Nowy")
        "active" -> t("Active", "Aktivní", "Aktywny")
        "inactive" -> t("Inactive", "Neaktivní", "Nieaktywny")
        "archived" -> t("Archived", "Archivovaný", "Zarchiwizowany")
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
        "schvaleno", "approved" -> t("Approved", "Schváleno", "Zatwierdzono")
        "stornovana", "stornována", "voided" -> t("Voided", "Stornována", "Anulowana")
        "preveden_na_klienta" -> t("Converted to client", "Převeden na klienta", "Przekonwertowany")
        "preveden_na_zakazku" -> t("Converted to job", "Převeden na zakázku", "Przekonwertowany")
        "zamitnuto", "rejected" -> t("Rejected", "Zamítnuto", "Odrzucone")
        else -> raw ?: ""
    }
}
