# Secretary - Kompletny podręcznik użytkownika

## 1. Do czego służy Secretary
Secretary to mobilny CRM i asystent operacyjny dla małej firmy. Łączy klientów, zlecenia, zadania, komunikację, oferty, faktury, raporty pracy, kalendarz, sterowanie głosowe oraz analizę roślin w jednej aplikacji.

Główna zasada działania:
- współdzielone dane firmy są przechowywane na serwerze
- każdy zalogowany pracuje na swoim koncie backendowym i swojej roli
- język systemu jest lokalny dla konkretnego użytkownika
- język klienta jest ustawieniem firmowym i może go zmieniać tylko administrator

## 2. Pierwsze uruchomienie

### Logowanie
- Logowanie odbywa się przez email i hasło.
- Jeśli dane są zapisane i urządzenie obsługuje biometrię, można logować się odciskiem palca.
- Po pierwszym poprawnym logowaniu aplikacja może zapisać dane do logowania biometrycznego.

### Onboarding firmy
Podczas pierwszej konfiguracji podajesz:
1. nazwę firmy i formę prawną
2. branżę i specjalizację
3. tryb języka wewnętrznego i języka klienta
4. domyślny język wewnętrzny i język klienta
5. tryb pracy firmy

Wynik onboardingu zapisuje się na serwerze jako konfiguracja firmy.

## 3. Główna nawigacja
Aplikacja ma 5 głównych sekcji:
- `Strona główna`
- `CRM`
- `Zadania`
- `Kalendarz`
- `Ustawienia`

## 4. Strona główna
Strona główna działa jak dashboard dzienny. Zwykle pokazuje:
- pilne zadania
- aktywne zadania
- aktywne zlecenia
- nowe leady
- liczbę klientów w CRM
- pozycje oczekujące na klienta, materiał lub płatność

To najlepszy punkt startowy na początek dnia.

## 5. CRM - przegląd modułów
CRM zawiera następujące zakładki:
- `Dziś`
- `Klienci`
- `Zlecenia`
- `Zadania`
- `Leady`
- `Oferty`
- `Faktury`
- `Raporty pracy`
- `Kontakty`
- `Rośliny`
- `Komunikacja`

### Jak moduły są ze sobą połączone
- kontakt z telefonu może zostać oznaczony jako klient i zapisany do współdzielonej bazy CRM
- klient może mieć zlecenia, notatki, komunikację, nieruchomości i indywidualne stawki
- lead można przekształcić w klienta albo od razu w zlecenie
- zaakceptowana oferta może utworzyć zlecenie
- raport pracy może utworzyć fakturę
- zaplanowane zlecenia i zadania trafiają do kalendarza
- komunikacja, notatki, zdjęcia i log audytowy są przypięte do konkretnych rekordów

## 6. Klienci

### Wyszukiwanie klienta
Wyszukiwanie działa po:
- nazwie
- kodzie klienta
- emailu
- telefonie

### Ręczne tworzenie klienta
Otwórz CRM -> `Klienci` i użyj `+`.

Dostępne pola:
- nazwa kontaktu
- firma
- telefon i drugi telefon
- email i drugi email
- numer rejestracyjny i VAT
- strona www
- adres rozliczeniowy
- miasto, kod pocztowy, kraj
- typ klienta
- preferowany kanał kontaktu

### Ustaw klientów z kontaktów telefonu
Tryb `Ustaw klientów` ładuje kontakty z telefonu:
- zaznaczony kontakt staje się klientem w CRM na serwerze
- odznaczony kontakt nie jest utrzymywany jako zsynchronizowany klient
- serwer łączy duplikaty po telefonie i emailu
- jeśli wielu użytkowników synchronizuje podobne kontakty, dane są scalane do jednego klienta

To zalecany sposób budowania wspólnej bazy klientów z kilku urządzeń.

### Indywidualne stawki klienta
Każdy klient może mieć własne stawki usług. Mają one priorytet nad globalnymi stawkami firmy z Ustawień.

## 7. Zlecenia

### Tworzenie zlecenia
Zlecenie można założyć ręcznie lub utworzyć z innego procesu:
- z konwersji leadu
- z zaakceptowanej oferty

Zlecenie obsługuje:
- klienta
- terminy
- status
- notatkę planowania
- notatkę przekazania
- przypisanie do osób

### Planowanie i przekazywanie
Planowanie opiera się na:
- planowanym starcie
- planowanym zakończeniu
- notatce przekazania

To służy do delegowania pracy i widoczności w kalendarzu.

### Etapy zlecenia
Każde zlecenie ma uporządkowane etapy:
- `Przed rozpoczęciem`
- `Przebieg`
- `Zakończenie`
- `Komplikacje`
- `Log audytowy`

W każdym etapie można zapisywać:
- notatki
- zdjęcia

Przycisk zdjęcia otwiera aparat lub galerię, a obraz trafia na serwer pod konkretne zlecenie i etap.

### Log audytowy
Audyt zapisuje ważne działania, np.:
- zmianę statusu
- dodanie notatki
- inne operacje wykonane na zleceniu

## 8. Zadania
Zadania mogą istnieć samodzielnie albo być powiązane z klientem lub zleceniem.

Mogą zawierać:
- tytuł
- typ
- opis
- priorytet
- termin
- planowany start i koniec
- przypisaną osobę
- wynik
- stan zakończenia

Typowy przepływ:
1. utworzyć zadanie
2. przypisać osobę
3. ustawić priorytet i termin
4. zapisać wynik i ewentualne zdjęcie
5. zakończyć zadanie

## 9. Leady
Lead to rekord przed sprzedażą.

Zawiera:
- nazwę kontaktu
- źródło
- email
- telefon
- opis zapytania

Można go przekształcić:
- w klienta
- w zlecenie

## 10. Oferty
Oferty łączą zapytanie z realizacją.

Obsługują:
- tworzenie oferty dla klienta
- dodawanie pozycji oferty
- liczenie sum
- akceptację

Akceptacja oferty może automatycznie utworzyć zlecenie.

## 11. Faktury
Faktury można:
- utworzyć ręcznie
- utworzyć z jednego raportu pracy
- utworzyć zbiorczo z wielu raportów

Wysyłka faktury jest przygotowana przez:
- SMS
- WhatsApp
- email

## 12. Raporty pracy
Raport pracy rejestruje wykonaną pracę i służy jako podstawa do fakturowania.

Raport zawiera:
- klienta
- datę
- przepracowane godziny
- cenę
- notatki

Na podstawie raportu można utworzyć fakturę pojedynczo albo zbiorczo.

## 13. Komunikacja
Komunikacja jest ręcznie rejestrowana w CRM i przypinana do klienta lub zlecenia.

Obsługiwane typy:
- telefon
- email
- SMS
- WhatsApp
- Checkatrade
- osobiście

Każdy wpis ma temat, wiadomość i kierunek:
- przychodzący
- wychodzący

## 14. Kontakty współdzielone
Zakładka `Kontakty` to wspólny katalog firmowy poza listą klientów.

Sekcje wbudowane:
- pracownicy
- podwykonawcy
- dostawcy materiałów
- wypożyczalnie narzędzi i aut

Można też tworzyć własne sekcje. Kontakty można:
- dodawać ręcznie
- importować z telefonu
- zapisywać na serwerze
- współdzielić i uzupełniać między użytkownikami

## 15. Rośliny
Zakładka `Rośliny` ma dwa tryby:
- `Rozpoznaj roślinę`
- `Sprawdź chorobę`

### Rozpoznawanie rośliny
Wymagane jest co najmniej 1 zdjęcie. Zalecane ujęcia:
- cała roślina
- detal liścia
- kwiat, owoc lub kora

Wynik:
- najlepsze dopasowanie
- pewność
- alternatywne dopasowania
- krótki opis i wymagania

### Diagnostyka choroby
Wymagane jest co najmniej 1 wyraźne zdjęcie uszkodzonego miejsca. Zalecane ujęcia:
- uszkodzony fragment
- detal liścia
- cała roślina lub łodyga

Wynik:
- najbardziej prawdopodobny problem
- pewność
- krótka diagnoza
- zalecane leczenie
- profilaktyka
- możliwe alternatywne problemy

## 16. Kalendarz
Kalendarz łączy współdzielone planowanie z serwera i synchronizację z kalendarzem urządzenia.

Pokazuje:
- zaplanowane zlecenia
- zaplanowane zadania
- elementy przypisane konkretnym osobom

Logika widoczności:
- osoba przypisana widzi wpis jako własne przypomnienie
- inni widzą wpis jako plan z informacją, komu został przypisany

## 17. Asystent głosowy

### Podstawy
Sterowanie głosem używa słowa aktywującego ustawionego w Ustawieniach.

Można ustawić:
- włączenie lub wyłączenie hotwordu
- własne słowo aktywujące
- szybkość mowy
- wysokość głosu
- długość ciszy
- ograniczenie działania tylko do godzin pracy

### Typy przepływów głosowych
1. bezpośrednie polecenie
2. wieloetapowa sesja głosowa
3. prowadzone głosem wykonywanie zdjęć

### Przykładowe polecenia
- `wyloguj`
- `co to za roślina`
- `jaka to choroba`
- `jak leczyć tę roślinę`

### Do czego używany jest głos
- operacje CRM
- prowadzone tworzenie raportu pracy
- pytania o pogodę
- wsparcie komunikacji
- rozpoznawanie roślin i chorób roślin

## 18. Ustawienia
Ustawienia zawierają:
- Profil firmy
- Stawki usług
- Język
- Motyw aplikacji
- Sterowanie głosem
- Serwer i połączenie
- CRM
- Powiadomienia
- Profil pracy
- Użytkownicy i uprawnienia
- Dane
- O aplikacji
- Historia wersji

### Profil firmy
Pokazuje:
- język wewnętrzny
- język klienta
- tryby językowe
- limity firmy

### Stawki usług
Zawierają:
- stawki godzinowe według typu pracy
- inne stawki, np. wywóz odpadów lub minimalna cena zlecenia

### Język
- `Język systemu` jest zapisywany lokalnie dla zalogowanego użytkownika
- `Język klienta` jest ustawieniem firmowym i zmienia go tylko administrator

### Użytkownicy i uprawnienia
Ta sekcja zarządza kontami backendowymi, a nie lokalnymi profilami urządzenia.

Pozwala:
- tworzyć użytkowników
- zmieniać role
- zmieniać status konta
- usuwać użytkowników
- ustawiać indywidualne wyjątki w uprawnieniach

Role wbudowane:
- admin
- manager
- worker
- assistant
- viewer

Każda rola ma domyślny zestaw uprawnień, a u konkretnej osoby można zrobić override.

### Dane
Służą do:
- eksportu CRM do CSV
- importu kontaktów z telefonu
- importu bazy danych
- czyszczenia historii
- przywracania ustawień domyślnych

## 19. Typowe procedury firmowe

### Kontakt z telefonu -> klient -> zlecenie -> faktura
1. otwórz `Ustaw klientów`
2. zaznacz kontakt jako klienta
3. zapisz wybór
4. utwórz zlecenie
5. wykonaj pracę i zapisz raport
6. utwórz fakturę z raportu

### Lead -> oferta -> zlecenie
1. utwórz lead
2. przygotuj klienta
3. utwórz ofertę
4. dodaj pozycje
5. zaakceptuj ofertę
6. pozwól systemowi utworzyć zlecenie

### Zlecenie -> dokumentacja zdjęciowa -> audyt
1. otwórz szczegóły zlecenia
2. dodawaj notatki do właściwego etapu
3. dodawaj zdjęcia przed startem, w trakcie, po zakończeniu lub przy komplikacjach
4. sprawdzaj log audytowy

### Roślina -> diagnoza -> leczenie
1. otwórz `Rośliny`
2. przełącz na `Sprawdź chorobę`
3. zrób przynajmniej 1 wyraźne zdjęcie
4. sprawdź diagnozę i leczenie

## 20. Zalecane użycie ról
- `admin`: konfiguracja firmy, użytkownicy, uprawnienia, język klienta, kluczowe ustawienia
- `manager`: planowanie, delegowanie, CRM, sprzedaż, nadzór
- `worker`: własne zadania, własny kalendarz, raporty pracy, zdjęcia
- `assistant`: wsparcie komunikacji, administracja CRM, ewidencja
- `viewer`: tylko odczyt

## 21. Najczęstsze problemy
- Nie można zmienić języka klienta: użytkownik nie jest administratorem.
- Głos nie reaguje: sprawdź słowo aktywujące, mikrofon i ustawienia hotwordu.
- Choroby roślin zgłaszają brak konfiguracji usługi: na serwerze brakuje klucza API dla plant health.
- Kontaktu nie ma w CRM: nie został zapisany jako klient w `Ustaw klientów`.
- Ceny klienta są nieprawidłowe: sprawdź indywidualne stawki klienta, bo mają priorytet nad domyślnymi.

## 22. Krótkie przykłady użycia
- `Dodaj klienta Novak, email novak@example.com, telefon 07000 000000`
- `Zaplanuj zlecenie na piątek 9:00`
- `Utwórz raport pracy dla Green Garden`
- `Co to za roślina`
- `Jak leczyć tę roślinę`
- `Wyloguj`
