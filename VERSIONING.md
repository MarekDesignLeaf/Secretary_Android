# VERSIONING RULES / PRAVIDLA VERZOVANI
# Secretary DesignLeaf
# ========================================
# TOTO JE POVINNY DOKUMENT. KAZDY VYVOJAR MUSI PRED JAKOUKOLI ZMENOU
# KODU PROJIT TIMTO DOKUMENTEM A DODRZET VSECHNY KROKY.
# ========================================

## Schema verzovani

Format: MAJOR.MINOR.PATCH_LETTER

Priklady: 1.0a, 1.0b, 1.1a, 2.0a

### Patch (pismeno): 1.0a -> 1.0b
- Pouze opravy chyb (bugfix)
- Zadne nove funkce
- Zadne zmeny API
- Zadne zmeny databazoveho schematu

### Minor (cislo): 1.0a -> 1.1a
- Pridani novych funkci
- Rozsireni existujicich funkci
- Nove UI obrazovky
- Nove API endpointy
- Nove DB tabulky (pokud nemeni existujici)

### Major (cislo): 1.0a -> 2.0a
- Zmena architektury
- Breaking changes v API
- Zmena databazoveho schematu (migrace)
- Zmena frameworku nebo zakladnich knihoven
- Prepis hlavnich komponent

## Povinny postup pri kazde zmene

1. PRED ZAHAJENIM PRACE:
   a) Precti VERSIONING.md (tento dokument)
   b) Precti CHANGELOG.md pro kontext poslednich zmen
   c) Precti VersionInfo.kt pro aktualni stav
   d) Urcit typ zmeny (patch/minor/major)
   e) Urcit nove cislo verze

2. PO DOKONCENI PRACE:
   a) Aktualizuj VersionInfo.kt:
      - Zmen VERSION_NAME na novou verzi
      - Inkrementuj VERSION_CODE o 1
      - Pridej novy ChangelogEntry na ZACATEK seznamu CHANGELOG
      - Vyplnit: verze, datum, autor, typ, popis, seznam zmen
   b) Aktualizuj CHANGELOG.md:
      - Pridej novou sekci na ZACATEK souboru
      - Stejny format jako v ChangelogEntry
   c) Aktualizuj build.gradle.kts:
      - versionCode = VersionInfo.VERSION_CODE
      - versionName = VersionInfo.VERSION_NAME
   d) NIKDY nemazat predchozi verze z changelogu
   e) NIKDY nemenit historicke zaznamy

3. PRAVIDLA PRO KOD:
   - Pracuj jen v reverziblnich krocich
   - Zastav se pred jakoukoli nevratnou akci
   - Potvrdit co se zmeni, co se NESMI zmenit, a jak poznat spravny vysledek
   - Nepridavej vylepseni bez explicitniho souhlasu
   - Jedna minimalni zmena najednou

## Struktura VersionInfo.kt

```kotlin
object VersionInfo {
    const val VERSION_NAME = "1.0a"      // Aktualni verze
    const val VERSION_CODE = 1            // Build cislo (vzdy +1)
    const val FIRST_RELEASE = "2026-03-30"
    val CHANGELOG = listOf(
        ChangelogEntry(...)
    )
}
```

## Kontakt

Autor: Marek Sima
Email: marek@designleaf.co.uk
Tel: +447395813008
