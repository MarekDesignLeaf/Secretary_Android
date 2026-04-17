# Secretary Android – Povinná hierarchie předávání práce

## Pravidlo systému

> Žádný klient, žádná zakázka a žádný úkol nesmí existovat bez odpovědné aktivní osoby a bez naplánované navazující akce přiřazené aktivnímu uživateli.

---

## Android UI checklist

### Sprint 3 – Vytváření entit (Wizard dialogy)

#### AddClientWizard (nahradit AddClientDialog)

- [ ] **Krok 1** – Základní údaje: jméno, email, telefon, adresa
- [ ] **Krok 2** – Výběr ownera:
  - Zobrazovat jen aktivní uživatele (`status == 'active'`)
  - Dropdown nebo searchable list
  - Pole povinné, Další neaktivní bez výběru
- [ ] **Krok 3** – První navazující akce:
  - Název úkolu (povinné)
  - Přiřazený uživatel (povinné, jen aktivní)
  - Plánovaný čas nebo deadline (povinné – aspoň jedno)
  - Priorita (default: `vysoka`)
  - Poznámka (volitelné)
- [ ] **Krok 4** – Rekapitulace a Uložit
  - Tlačítko Uložit neaktivní dokud není vše validní
  - Zobrazit souhrn: klient + owner + první akce
- [ ] ViewModel funkce: `createClientWithFirstAction(name, email, phone, ownerUserId, firstAction)`
- [ ] API call: POST `/crm/clients` s `owner_user_id` + `first_action` objektem

#### AddJobWizard (nahradit AddJobDialog)

- [ ] **Krok 1** – Základ zakázky: název, klient (dropdown), datum začátku
- [ ] **Krok 2** – Job owner:
  - Jen aktivní uživatelé
  - Povinné
- [ ] **Krok 3** – První navazující akce:
  - Stejná pole jako u klienta
- [ ] **Krok 4** – Rekapitulace a Uložit
- [ ] ViewModel funkce: `createJobWithFirstAction(title, clientId, ownerUserId, firstAction)`
- [ ] API call: POST `/crm/jobs` s `assigned_user_id` + `first_action`

#### AddTaskDialog – zpřísnění

- [ ] Pole `assignee` povinné (zobrazovat jen aktivní uživatele)
- [ ] Pole `planned_start_at` NEBO `deadline` povinné – aspoň jedno
- [ ] UI validace před odesláním (tlačítko Uložit neaktivní)
- [ ] Pokud task bude new `next_action` klienta/zakázky: checkbox `Nastavit jako další krok`

### Sprint 3 – Dokončení tasku s náhradou

#### TaskCompletionDialog (nový dialog)

Spustit pokud task je `next_action_task_id` klienta nebo zakázky:

- [ ] Zobrazit upozornění:
  > Tento úkol je aktuální další krok [klienta X / zakázky Y]. Vyber nový další krok.
- [ ] Možnosti:
  - [ ] **Vytvořit nový navazující task** (inline mini-formulář: název, assignee, deadline)
  - [ ] **Vybrat existující otevřený task** (seznam otevřených tasků pro daného klienta/zakázku)
  - [ ] **Zrušit dokončení**
- [ ] Uložit nelze bez výběru náhrady
- [ ] ViewModel funkce: `completeTaskWithReplacement(taskId, replacementTaskId?, newTaskPayload?)`
- [ ] API call: PUT `/crm/tasks/{taskId}` s `replacement_task_id` nebo `replacement_task_payload`

### Sprint 3 – Detail klienta a zakázky

#### ClientDetailScreen – doplnit

- [ ] Zobrazit v hlavičce:
  - Owner klienta (jméno, avatar/ikona)
  - Aktuální další krok (název tasku)
  - Assignee dalšího kroku
  - Termín dalšího kroku
  - Stav integrity (`hierarchy_status`)
- [ ] Pokud `hierarchy_status == 'orphan'`: zobrazit červený banner `⚠ Klient nemá platný další krok`
- [ ] Tlačítko pro změnu ownera (jen admin/manager)
- [ ] Tlačítko pro nastavení nového next_action
- [ ] Blokovat archivaci klienta pokud `hierarchy_status != 'valid'`

#### JobDetailScreen – doplnit

- [ ] Zobrazit v hlavičce:
  - Job owner (`assigned_user_id`)
  - Aktuální další krok
  - Assignee dalšího kroku
  - Termín dalšího kroku
  - Stav integrity
- [ ] Pokud `hierarchy_status == 'orphan'`: červený banner `⚠ Zakázka nemá platný další krok`
- [ ] Blokovat uzavření zakázky pokud má orphan status

### Sprint 4 – Dashboard integrity bloky

#### DashboardTab – doplnit sekci Stav systému

- [ ] Nový blok `HierarchyIntegrityCard`:
  - Počet klientů bez další akce
  - Počet zakázek bez další akce
  - Počet úkolů bez přiřazeného uživatele
  - Počet úkolů bez termínu
- [ ] Správný stav = všude 0
- [ ] Pokud > 0: kliknout pro přechod na seznam problémů
- [ ] ViewModel funkce: `loadHierarchyIntegrity()`
- [ ] API call: GET `/admin/hierarchy-integrity`
- [ ] Zobrazovat jen pro admin/manager role

### Sprint 4 – Nastavení uživatelů

#### AdminUsersScreen – ochrana deaktivace

- [ ] Před deaktivací volat backend, zachytit HTTP 409
- [ ] Zobrazit dialog:
  > Tento uživatel nelze deaktivovat. Drží následující odpovědnosti:
  > - Klienti: [seznam]
  > - Zakázky: [seznam]
  > - Úkoly: [seznam]
- [ ] Nabídnout: Přiřadit náhradu nebo Zrušit
- [ ] ViewModel funkce: `deactivateUser(userId, onBlocked: (BlockedEntities) -> Unit)`

---

## Datové modely (Android)

Rozšířit existující data třídy o nová pole:

```kotlin
data class Client(
    val id: Long,
    val displayName: String,
    // ... existující pole ...
    val ownerUserId: Long? = null,           // nové
    val ownerDisplayName: String? = null,    // nové
    val nextActionTaskId: String? = null,    // nové
    val nextActionTask: Task? = null,        // nové (nested)
    val hierarchyStatus: String = "unchecked" // nové
)

data class Job(
    val id: Long,
    val title: String,
    // ... existující pole ...
    val nextActionTaskId: String? = null,    // nové
    val nextActionTask: Task? = null,        // nové (nested)
    val hierarchyStatus: String = "unchecked" // nové
)

data class FirstActionPayload(
    val title: String,
    val assignedUserId: Long,
    val plannedStartAt: String? = null,
    val deadline: String? = null,
    val priority: String = "vysoka",
    val planningNotes: String? = null
)

data class BlockedEntities(
    val clients: List<Map<String, Any>>,
    val jobs: List<Map<String, Any>>,
    val tasks: List<Map<String, Any>>
)
```

---

## Nové ViewModel funkce (přidat do SecretaryViewModel)

```
createClientWithFirstAction(name, email, phone, ownerUserId, firstAction: FirstActionPayload)
createJobWithFirstAction(title, clientId, ownerUserId, firstAction: FirstActionPayload)
completeTaskWithReplacement(taskId, replacementTaskId?, newTaskPayload?)
loadHierarchyIntegrity()
deactivateUser(userId, onBlocked)
setClientNextAction(clientId, taskId)
setJobNextAction(jobId, taskId)
```

---

## Akceptační kritéria (UI)

- [ ] AddClientWizard – nelze uložit bez ownera a první akce
- [ ] AddJobWizard – nelze uložit bez ownera a první akce
- [ ] AddTaskDialog – nelze uložit bez assignee a termínu
- [ ] TaskCompletionDialog – zobrazí se pokud je task next_action
- [ ] ClientDetailScreen – zobrazuje stav integrity + owner + next_action
- [ ] JobDetailScreen – zobrazuje stav integrity + owner + next_action
- [ ] DashboardTab – zobrazuje integrity bloky (jen admin/manager)
- [ ] AdminUsersScreen – zobrazuje dialog při blokované deaktivaci
- [ ] Po aktivaci všech pravidel: orphan count = 0
