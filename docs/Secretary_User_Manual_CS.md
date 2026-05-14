# Secretary - Kompletní uživatelský návod

## 1. Co Secretary řeší
Secretary je mobilní CRM a provozní asistent pro menší firmu. Spojuje klienty, zakázky, úkoly, komunikaci, nabídky, faktury, výkazy práce, kalendář, hlasové ovládání a rozpoznávání rostlin do jedné aplikace.

Hlavní princip:
- server drží sdílená firemní data
- přihlášený uživatel pracuje se svým účtem a rolemi
- systémový jazyk je lokální pro konkrétního uživatele
- zákaznický jazyk je firemní nastavení a mění ho jen administrátor

## 2. První spuštění

### Přihlášení
- Přihlášení probíhá emailem a heslem.
- Pokud jsou v zařízení uložené přihlašovací údaje a telefon podporuje biometriku, je možné přihlášení otiskem prstu.
- Po prvním úspěšném loginu se uloží přihlašovací údaje pro další biometrické přihlášení.

### Onboarding firmy
Při prvním nastavení aplikace se vyplňuje:
1. název firmy a právní forma
2. obor a specializace
3. interní jazykový režim a zákaznický jazykový režim
4. výchozí interní a zákaznický jazyk
5. režim práce firmy

Výsledek onboardingu se ukládá na server a používá se pro firemní konfiguraci.

## 3. Hlavní navigace
Aplikace má 5 hlavních sekcí:
- `Domů`
- `CRM`
- `Úkoly`
- `Kalendář`
- `Nastavení`

## 4. Domů
Domovská obrazovka slouží jako rychlý přehled. Typicky zde uvidíš:
- urgentní úkoly
- aktivní úkoly
- zakázky v běhu
- nové leady
- klienty v CRM
- čekání na klienta, materiál nebo platbu

Používej ji jako denní rozcestník. Detailní práce se pak dělá hlavně v CRM, Úkolech a Kalendáři.

## 5. CRM - přehled modulů
CRM obsahuje tyto záložky:
- `Dnes`
- `Klienti`
- `Zakázky`
- `Úkoly`
- `Leady`
- `Nabídky`
- `Faktury`
- `Výkazy práce`
- `Kontakty`
- `Rostliny`
- `Komunikace`

### Jak jsou moduly provázané
- kontakt z telefonu může být označen jako klient a přenesen do serverové databáze klientů
- klient může mít zakázky, komunikaci, poznámky, nemovitosti a individuální sazby
- lead lze převést na klienta nebo rovnou na zakázku
- schválená nabídka může vytvořit zakázku
- výkaz práce lze převést na fakturu
- plánované zakázky a úkoly se propisují do kalendáře
- komunikace, poznámky, audit a fotky se vážou ke konkrétním entitám

## 6. Klienti

### Vyhledání klienta
Ve vyhledávání klientů lze hledat podle:
- jména
- kódu klienta
- emailu
- telefonu

### Vytvoření klienta ručně
V CRM otevři záložku `Klienti` a použij `+`.

Vyplnit lze mimo jiné:
- jméno kontaktu
- firma
- telefon a druhý telefon
- email a druhý email
- IČO, DIČ
- web
- fakturační adresa
- město, PSČ, země
- typ klienta
- preferovaný kontakt

### Nastavit klienty z telefonu
Tlačítko `Nastavit klienty` otevře režim synchronizace kontaktů:
- načtou se kontakty z telefonu
- zaškrtnutý kontakt se stane klientem na serveru
- odškrtnutý kontakt se jako synchronizovaný klient na serveru neponechá
- server se snaží párovat duplicity podle telefonu a emailu
- pokud více uživatelů označí podobné kontakty, data se slučují do jednoho klienta

To je doporučený způsob, jak postupně postavit jednotnou klientskou databázi z více telefonů.

### Individuální sazby klienta
Každý klient může mít vlastní sazby služeb. Tyto sazby mají prioritu před globálními firemními sazbami z nastavení. Používej je tam, kde má konkrétní klient speciální hodinové ceny nebo odlišný ceník.

## 7. Zakázky

### Založení zakázky
Zakázka se vytváří ručně z CRM nebo vznikne z jiného toku:
- převod schválené nabídky
- převod leadu

Zakázka umí nést:
- klienta
- termíny
- stav
- plánovací poznámku
- poznámku k předání
- přiřazení odpovědným lidem

### Plánování a předání
Zakázku lze plánovat přes:
- plánovaný začátek
- plánovaný konec
- handover note

To se používá pro předávání práce mezi lidmi a pro promítnutí do firemního kalendáře.

### Fáze zakázky
Každá zakázka má evidenci průběhu:
- `Před zahájením`
- `Průběh`
- `Zakonceni`
- `Komplikace`
- `Audit log`

V každé fázi lze ukládat:
- textové poznámky
- fotografie

Tlačítko pro fotku otevře fotoaparát nebo galerii a fotky se ukládají k dané zakázce na server.

### Audit log
Audit ukazuje, kdo a kdy provedl změny, například:
- změnu stavu
- přidání poznámky
- další důležité operace

## 8. Úkoly
Úkoly existují samostatně i ve vazbě na klienta nebo zakázku.

Umí mít:
- název
- typ
- popis
- prioritu
- termín
- plánovaný začátek a konec
- přiřazení člověku
- výsledek
- stav dokončení

Typické workflow:
1. vytvořit úkol
2. přiřadit uživateli
3. nastavit prioritu a termín
4. doplnit výsledek a případně fotku
5. úkol dokončit

## 9. Leady
Lead je předobchodní záznam.

Obsahuje:
- jméno kontaktu
- zdroj
- email
- telefon
- popis poptávky

Lead lze převést:
- na klienta
- na zakázku

## 10. Nabídky
Nabídky slouží jako mezikrok mezi poptávkou a realizací.

Umí:
- vytvořit nabídku ke klientovi
- přidávat položky nabídky
- počítat celkovou hodnotu
- schválit nabídku

Po schválení může nabídka automaticky vytvořit zakázku.

## 11. Faktury
Faktury lze:
- založit ručně
- vytvořit z jednoho výkazu práce
- vytvořit dávkově z více výkazů práce

U faktur lze sledovat stav a odesílání. Odeslání je připravené přes:
- SMS
- WhatsApp
- email

## 12. Výkazy práce
Výkaz práce slouží pro záznam provedené práce a podklad k fakturaci.

Obsahuje:
- klienta
- datum
- odpracované hodiny
- cenu
- poznámky

Z výkazu lze vytvořit fakturu jednotlivě nebo hromadně.

## 13. Komunikace
Komunikace se loguje ručně do CRM a váže se ke klientovi nebo zakázce.

Podporované typy:
- telefon
- email
- SMS
- WhatsApp
- Checkatrade
- osobně

U každého záznamu je předmět, zpráva a směr:
- příchozí
- odchozí

## 14. Sdílené kontakty
Záložka `Kontakty` slouží pro firemní adresář mimo klienty.

Pevné oddíly:
- zaměstnanci
- subkontraktoři
- dodavatelé materiálu
- půjčovny nářadí a aut

Navíc je možné vytvořit vlastní oddíl. Kontakty lze:
- zakládat ručně
- importovat z telefonu
- ukládat na server
- sdílet mezi uživateli

## 15. Rostliny
Záložka `Rostliny` má dva režimy:
- `Rozpoznat rostlinu`
- `Zjistit chorobu`

### Rozpoznání rostliny
Použij alespoň 1 fotku. Doporučené snímky:
- celá rostlina
- detail listu
- květ, plod nebo kůra

Výstup:
- nejlepší shoda
- pravděpodobnost
- případné další shody
- stručný popis a nároky

### Diagnostika choroby
Použij alespoň 1 jasnou fotku poškozené části. Doporučené snímky:
- poškozené místo
- detail listu
- celek nebo stonek

Výstup:
- nejpravděpodobnější problém
- pravděpodobnost
- stručná diagnóza
- doporučená léčba
- prevence
- případné alternativní problémy

## 16. Kalendář
Kalendář pracuje se sdíleným plánem ze serveru a se synchronizací do zařízení.

Zobrazuje:
- plánované zakázky
- plánované úkoly
- položky přiřazené konkrétním lidem

Logika viditelnosti:
- přiřazený člověk je vidí jako vlastní připomínky
- ostatní je vidí jako plánované záznamy s informací, komu jsou přidělené

## 17. Hlasová asistentka

### Základ
Hlasové ovládání pracuje s aktivačním slovem nastaveným v Nastavení.

Lze nastavit:
- zapnutí detekce aktivačního slova
- vlastní aktivační slovo
- rychlost řeči
- výšku hlasu
- délku ticha
- omezení jen na pracovní dobu

### Typy hlasových toků
1. přímý příkaz
2. vícekroková hlasová session
3. hlasově vedené focení

### Příklady přímých hlasových dotazů
- `odhlásit`
- `co je to za rostlinu`
- `co je to za chorobu`
- `jak léčit tuto rostlinu`

### Co hlas typicky zvládá
- práce s CRM položkami
- spouštění výkazu práce
- dotazy na počasí
- pomoc s komunikací
- hlasově vedené focení rostlin

## 18. Nastavení
Nastavení obsahuje tyto sekce:
- Profil firmy
- Sazby služeb
- Jazyk
- Motiv aplikace
- Hlasové ovládání
- Server a připojení
- CRM
- Notifikace
- Pracovní profil
- Uživatelé a práva
- Data
- O aplikaci
- Historie verzí

### Profil firmy
Ukazuje:
- interní jazyk
- zákaznický jazyk
- jazykové režimy
- limity firmy

### Sazby služeb
Obsahují:
- hodinové sazby podle typu práce
- další sazby jako odvoz odpadu nebo minimální cena zakázky

### Jazyk
- `Jazyk systému` je lokální pro přihlášeného uživatele
- `Zákaznický jazyk` je firemní a mění ho pouze administrátor

### Uživatelé a práva
Tady se spravují backendové účty, ne lokální profily zařízení.

Lze:
- vytvořit nového uživatele
- měnit roli
- měnit stav účtu
- mazat uživatele
- upravovat individuální oprávnění

Přednastavené role:
- admin
- manager
- worker
- assistant
- viewer

Každá role má výchozí sadu práv a u konkrétního uživatele lze udělat individuální override.

### Data
Slouží pro:
- export CRM do CSV
- import kontaktů z telefonu
- import databáze
- smazání historie
- obnovu výchozích nastavení

## 19. Typické firemní postupy

### Nový kontakt z telefonu -> klient -> zakázka -> faktura
1. v `Klienti` otevřít `Nastavit klienty`
2. označit kontakt jako klienta
3. uložit synchronizaci
4. vytvořit zakázku
5. provést práci a vytvořit výkaz
6. převést výkaz na fakturu

### Lead -> nabídka -> zakázka
1. založit lead
2. podle potřeby jej převést nebo založit klienta
3. vytvořit nabídku
4. doplnit položky
5. nabídku schválit
6. vznikne zakázka

### Zakázka -> fotodokumentace -> audit
1. otevřít detail zakázky
2. zapisovat poznámky do správné fáze
3. přidávat fotky před zahájením, v průběhu, při ukončení nebo v komplikacích
4. sledovat audit log

### Rostlina -> diagnóza -> léčba
1. otevřít `Rostliny`
2. přepnout na `Zjistit chorobu`
3. pořídit aspoň 1 jasnou fotku
4. vyhodnotit diagnózu a doporučenou léčbu

## 20. Doporučené používání rolí
- `admin`: správa firmy, uživatelů, jazyků, práv a klíčových konfigurací
- `manager`: plánování, přidělování práce, CRM a obchod
- `worker`: práce s vlastními úkoly, kalendářem, výkazy a fotkami
- `assistant`: podpora komunikace, CRM administrace, evidence
- `viewer`: pouze čtení

## 21. Nejčastější problémy
- Nejde změnit zákaznický jazyk: uživatel není administrátor.
- Hlas nereaguje: zkontroluj aktivační slovo, mikrofon a zapnutí hotwordu.
- Choroby rostlin hlásí nenastavenou službu: na serveru chybí API klíč pro plant health.
- Kontakt není v CRM: v `Nastavit klienty` nebyl uložen jako klient.
- Klient má špatné ceny: zkontroluj individuální sazby klienta, mají přednost před globálními sazbami.

## 22. Krátké příklady použití
- `Přidej klienta Novák, email novak@example.com, telefon 07000 000000`
- `Naplánuj zakázku na pátek 9:00`
- `Založ výkaz práce pro klienta Green Garden`
- `Co je to za rostlinu`
- `Jak léčit tuto rostlinu`
- `Odhlásit`
