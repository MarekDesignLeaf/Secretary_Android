# Secretary — AI Agent Instructions (AGENTS.md)

Tento soubor čtou AI agenti (Codex, Claude, Copilot) před zahájením práce na projektu.

## Přehled projektu

**Secretary** je hlasem ovládaný CRM asistent pro terénní pracovníky.
- **Android app**: Kotlin + Jetpack Compose, MVVM architektura, verze `1.4a` (versionCode 5)
- **Backend server**: FastAPI + PostgreSQL na Railway, `https://web-production-4b451.up.railway.app`
- **AI engine**: OpenAI GPT-4o přes tool calling

## Repozitáře

- **Android**: `https://github.com/MarekDesignLeaf/Secretary_Android` (tento repozitář)
- **Server**: `https://github.com/MarekDesignLeaf/Secretary_Server`
- **Railway deploy**: automaticky z `Secretary_Server/main`

## Git setup (POVINNÉ před push)

Pokud v sandboxu chybí remote, spusť:

```bash
git config user.email "marek@designleaf.co.uk"
git config user.name "Marek Sima"
git remote add origin https://MarekDesignLeaf:${GITHUB_TOKEN}@github.com/MarekDesignLeaf/Secretary_Android.git
```

Proměnná `GITHUB_TOKEN` musí být nastavena v Codex secrets (Settings → Environment).

## Větvení

- `main` — produkční větev, vždy funkční kód
- Nové funkce: vytvořit větev `feature/nazev` nebo `codex/nazev`, pak PR do `main`
- **NIKDY netlačit přímo do `main`** bez review, pokud nejde o kritický bugfix

## Struktura Android projektu

```
app/src/main/java/com/example/secretary/
├── MainActivity.kt          (9594 řádků) — hlavní UI, ViewModel, všechny CRM obrazovky
├── SettingsScreen.kt        (1586)       — nastavení: hlas, server, profil, jazyky
├── Strings.kt               (1482)       — překlady CS/EN/PL
├── VoiceManager.kt          (838)        — STT/TTS, wake word, stavový automat
├── Models.kt                (721)        — datové třídy: Client, Job, Task, Invoice...
├── SecretaryApi.kt          (663)        — Retrofit API rozhraní
├── ActivityPricingScreen.kt (604)        — katalog prací s cenami
├── ImportScreen.kt          (864)        — import kontaktů a dat
├── OnboardingScreen.kt      (377)        — výběr odvětví při prvním spuštění
├── LoginScreen.kt           (481)        — přihlášení + biometrie
├── SettingsManager.kt       (480)        — SharedPreferences, profily, oprávnění
├── VersionInfo.kt           (464)        — verzování, changelog (VŽDY aktualizovat!)
├── WakeWordEngine.kt        (425)        — detekce aktivačního slova
├── CalendarManager.kt       (373)        — Google Calendar CRUD
├── Navigation.kt            (32)         — navigační trasy
└── ui/theme/Theme.kt                    — brand barvy, světlý/tmavý motiv
```

## Backend endpointy (aktuálně dostupné na Railway)

Server `https://web-production-4b451.up.railway.app` má tyto endpointy:

**Auth:** `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me`, `GET /auth/users`, `PUT /auth/users/{id}`, `DELETE /auth/users/{id}`, `POST /auth/register`, `PUT /auth/change-password`

**CRM:** `GET/POST /crm/clients`, `GET/PUT/DELETE /crm/clients/{id}`, `GET/POST /crm/jobs`, `GET/PUT /crm/jobs/{id}`, `GET/POST /crm/tasks`, `GET/POST /crm/leads`, `GET/POST /crm/invoices`, `GET/POST /crm/quotes`, `GET/POST /crm/communications`, `GET/POST /crm/notifications`, `POST /work-reports`, `GET /work-reports`

**Onboarding:** `GET /onboarding/industry-groups`, `GET /onboarding/industry-subtypes/{groupId}`, `POST /onboarding/company-setup`, `GET /onboarding/status/{tenantId}`

**Voice:** `POST /voice/session/start`, `POST /voice/session/input`, `POST /voice/session/resume`

**Ostatní:** `GET /health`, `GET /bootstrap/status`, `POST /bootstrap/first-admin`, `GET /system/settings`, `GET /tenant/config/{tenantId}`, `POST /process`

**CHYBĚJÍCÍ endpointy** (jsou v SecretaryApi.kt ale ještě neexistují na serveru):
- `GET /tenant/profile`, `GET /tenant/languages`
- `GET /activities/templates`, `GET /activities/tenant/{tenantId}`
- `POST /voice/resolve`, `POST /voice/context`
- `GET /crm/contacts`, `GET /import/sessions/*`
- `GET /tools/packages`, `GET /assistant/memory`
- `GET /admin/activity-log`

## Versioning — POVINNÉ KROKY

Před každou změnou kódu:
1. Přečíst `VersionInfo.kt` — aktuální verze: `1.4a` (versionCode 5)
2. Po dokončení aktualizovat `VersionInfo.kt`: `VERSION_NAME`, `VERSION_CODE`, changelog
3. Aktualizovat `app/build.gradle.kts`: `versionCode`, `versionName`
4. Commitovat jako zvláštní commit

## Klíčové technické detaily

- **Kotlin + Jetpack Compose** — veškeré UI je composable funkce
- **MVVM** — `SecretaryViewModel` v `MainActivity.kt` je hlavní state holder
- **Retrofit** — `SecretaryApi` interface, BASE_URL z BuildConfig
- **SharedPreferences** — přes `SettingsManager`, uložení lokálních nastavení
- **JWT Auth** — Bearer token, refresh token, 24h expirace access tokenu
- **Voice** — Android STT (SpeechRecognizer), TTS (TextToSpeech), wake word "hej secretary"
- **CRLF line endings** — Windows, zachovat při editaci

## Kde jsou co endpointy volány

- Autentizace: `LoginScreen.kt` → `viewModel.login()`
- CRM operace: `MainActivity.kt` → přímo přes `viewModel.api.*`
- Nastavení: `SettingsScreen.kt` → `viewModel.api.getTenantConfig()`, `getTenantLanguages()`
- Onboarding: `OnboardingScreen.kt` → `viewModel.api.getIndustryGroups()`, `getIndustrySubtypes()`
- Hlasový vstup: `VoiceManager.kt` → `viewModel.sendMessage()`

## Závislosti (build.gradle.kts)

```
retrofit2, okhttp3, gson — HTTP klient
compose-bom, material3 — UI
lifecycle-viewmodel-compose — ViewModel
accompanist-permissions — Android oprávnění
biometric — biometrické přihlášení
```

## Co NESMÍŠ dělat

- Nemazat ani nepřepisovat `VersionInfo.kt` changelog záznamy
- Netlačit do `main` bez PR pokud jde o větší změny
- Necommitovat soubory: `.env`, `*.keystore`, `local.properties`, `secrets.properties`
- Nepřidávat testovací data se skutečnými kontakty nebo hesly
- Nevytvářet nové soubory bez explicitní instrukce — vždy editovat existující
