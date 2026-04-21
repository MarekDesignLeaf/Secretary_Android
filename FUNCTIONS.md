# Secretary Android – Mapa funkcí, chybějící obrazovky a vazby

## Přehled

> Tento dokument mapuje všechny ViewModel funkce, composable obrazovky,
> API volání a identifikuje mezery v implementaci.

---

## 1. SecretaryApi.kt – pokrytí server endpointů

### Endpointy v SecretaryApi.kt ✅

```
AUTH:        authLogin, authMe, authRefresh, registerUser, getFirstLoginUsers,
             getAuthRoles, getAuthUsers, updateAuthUser, deleteAuthUser, changePassword

CLIENTS:     getClients, searchClients, getClientDetail, createClient, updateClient,
             archiveClient, addClientNote, syncContacts,
             getClientServiceRates, updateClientServiceRates, updateClientRate

JOBS:        getJobs, getJobDetail, createJob, updateJob,
             addJobNote, uploadJobPhoto

TASKS:       getTasks, createTask, updateTask

LEADS:       getLeads, createLead, updateLead,
             convertLeadToClient, convertLeadToJob

QUOTES:      getQuotes, getQuoteDetail, createQuote, updateQuote,
             addQuoteItem, deleteQuoteItem, approveQuote

INVOICES:    getInvoices, createInvoice, updateInvoice,
             createInvoiceFromWorkReport, batchInvoiceFromWorkReports,
             getInvoiceItems, addInvoiceItem, deleteInvoiceItem,
             getPayments, addPayment

COMMUNIC:    getCommunications, logCommunication

CONTACTS:    getContactSections, createContactSection,
             getSharedContacts, createSharedContact, updateSharedContact,
             deleteSharedContact, importSharedContacts

NOTIF:       getNotifications, markNotificationRead

WORK RPT:    getWorkReports, getWorkReport, createWorkReport

PLANTS:      identifyPlant, assessPlantHealth, identifyMushroom, getNatureHistory

VOICE:       voiceSessionStart, voiceSessionInput, voiceSessionResume

CALENDAR:    getCalendarFeed

SETTINGS:    getSettings, healthCheck

TENANT:      getTenantConfig, updateTenantLanguages,
             getDefaultRates, updateDefaultRates,
             getUserRates, updateUserRates

ONBOARDING:  getOnboardingStatus, companySetup,
             getIndustryGroups, getIndustrySubtypes

PHOTOS:      addPhoto, getPhotos

TIMELINE:    getTimeline

EXPORT:      exportCsv
```

### Chybí v SecretaryApi.kt ❌

```
addJobAuditEntry     POST /crm/jobs/{id}/audit           ❌
getJobPhotos         GET  /crm/jobs/{id}/photos          ❌
updateQuoteItem      PUT  /crm/quotes/{id}/items/{itemId} ❌
deleteTask           DELETE /crm/tasks/{id}              ❌ (VM obchází přes updateTask)
getLeadDetail        GET  /crm/leads/{id}                ❌
getPricingRules      GET  /pricing-rules                 ❌
createPricingRule    POST /pricing-rules                 ❌
getNatureServices    GET  /nature/services/status        ❌
sendWhatsApp         POST /whatsapp/send                 ❌
getWhatsAppStatus    GET  /whatsapp/status               ❌
getWaste             GET  /crm/waste                     ❌
archiveJob           DELETE /crm/jobs/{id}               ❌ (server endpoint chybí)
deleteLead           DELETE /crm/leads/{id}              ❌ (server endpoint chybí)
hierarchyIntegrity   GET  /admin/hierarchy-integrity     ❌ (server endpoint chybí)
```

---

## 2. ViewModel funkce – stav

### SecretaryViewModel – existující funkce

#### Auth
```kotlin
login(email, password, fromBiometric, onError)       ✅
autoLogin()                                          ✅
finalizeLoginAfterCredentialSetup()                  ✅
logout()                                             ✅
tryRefreshToken(): Boolean                           ✅
completeFirstPasswordChange(oldPw, newPw, onDone)   ✅
```

#### Admin
```kotlin
createBackendUser(email, displayName, role, onDone) ✅
loadBackendUsers()                                   ✅
updateBackendUser(userId, ...)                       ✅
resetBackendUserPassword(userId, onDone)             ✅
deleteBackendUser(userId, onDone)                    ✅
loadBackendRoles()                                   ✅
loadFirstLoginUsers()                                ✅
loadAdminActivityLog(actorUserId)                    ✅
```

#### Config / Onboarding
```kotlin
loadTenantConfig()                                   ✅
checkOnboardingStatus()                              ✅
submitOnboarding(...)                                ✅
updateCustomerLanguage(customerLang, onDone)         ✅
```

#### CRM – Klienti
```kotlin
loadClientDetail(clientId)                           ✅
createClientManual(name, email, phone)               ✅
updateClient(clientId, data)                         ✅
updateClientServiceRates(clientId, rates, onDone)   ✅
addClientNote(clientId, note)                        ✅
```

#### CRM – Zakázky
```kotlin
createJobManual(title, clientId, startDate)          ✅
loadJobDetail(jobId)                                 ✅
updateJob(jobId, data)                               ✅
addJobNote(jobId, note, noteType)                    ✅
addJobAuditEntry(jobId, actionType, description)     ✅
uploadJobPhoto(jobId, photoType, file, description) ✅
```

#### CRM – Úkoly
```kotlin
createTaskManual(title, type, priority, ...)         ✅
updateTask(taskId, data)                             ✅
completeTask(taskId)                                 ✅
getTaskById(taskId): Task?                           ✅
```

#### CRM – Leady
```kotlin
createLeadManual(name, source, email, phone, desc)  ✅
updateLead(leadId, data)                             ✅
convertLeadToClient(leadId, name, email, phone)     ✅
convertLeadToJob(leadId, title)                      ✅
```

#### CRM – Nabídky a Faktury
```kotlin
createQuote(clientId, title)                         ✅
approveQuote(quoteId, createJob)                     ✅
addQuoteItem(quoteId, description, qty, price)       ✅
createInvoiceManual(clientId, amount, dueDate)       ✅
updateInvoiceStatus(invoiceId, status)               ✅
createInvoiceFromWorkReport(workReportId, onResult)  ✅
batchInvoiceFromWorkReports(ids, onResult)           ✅
```

#### CRM – Work Reports
```kotlin
createWorkReportManual(clientId, date, hours, ...)  ✅
loadWorkReportsFromServer() [private suspend]        ✅
```

#### CRM – Komunikace a Kontakty
```kotlin
logCommunication(clientId, jobId, type, ...)         ✅
loadAllCommunications(): List<Communication>         ✅
loadClientSelectionContacts(context, onDone)         ✅
saveClientSelectionContacts(contacts, onDone)        ✅
syncPhoneContacts(context, ...)                      ✅
createContactSection(displayName, onDone)            ✅
saveSharedContact(data, contactId, onDone)           ✅
deleteSharedContact(contactId, onDone)               ✅
importSharedContacts(contacts, onDone)               ✅
```

#### Refresh
```kotlin
refreshCrmData()                                     ✅ + offline guard
refreshCrmDataKeepTasks() [private]                 ✅ + offline guard
loadCalendarFeed(days)                               ✅ + offline + permission guard
loadCalendarFeedFromServer(days) [private suspend]  ✅
loadTasksFromServer() [private suspend]              ✅
loadWorkReportsFromServer() [private suspend]        ✅
```

#### Voice a AI
```kotlin
onVoiceInput(text)                                   ✅
handleAction(response)                               ✅
startWorkReportSession()                             ✅
processVoiceSessionInput(text)                       ✅
resumeVoiceSession(sessionId)                        ✅
endVoiceSession()                                    ✅
shareWhatsApp(context, message)                      ✅
```

#### Plants / Nature
```kotlin
identifyPlant(photos, captureContext)                ✅
assessPlantHealth(photos, captureContext)             ✅
identifyMushroom(photos, captureContext)              ✅
loadNatureHistory()                                  ✅
requestPlantCaptureFromVoice(mode)                   ✅
setPlantCaptureMode(mode)                            ✅
consumePendingPlantCaptureRequest(resumeHotword)     ✅
clearPlantRecognitionResult()                        ✅
```

#### Settings
```kotlin
loadSettings()                                       ✅
testConnection()                                     ✅
changeLanguage(langCode)                             ✅
resetSettings()                                      ✅
triggerManualImport()                                ✅
cancelImport()                                       ✅
confirmImport()                                      ✅
exportCrmData()                                      ⚠️ STUB – vrací "export unavailable"
updateDefaultRates(rates)                            ✅
```

#### Chybějící ViewModel funkce ❌

```kotlin
// Invoice detail
loadInvoiceDetail(invoiceId)                         ❌
loadInvoiceItems(invoiceId)                          ❌
addInvoiceItem(invoiceId, data)                      ❌
deleteInvoiceItem(invoiceId, itemId)                 ❌
loadPayments(invoiceId)                              ❌
addPayment(invoiceId, amount, method)                ❌

// Quote detail
loadQuoteDetail(quoteId)                             ❌
updateQuoteItem(quoteId, itemId, data)               ❌
deleteQuoteItem(quoteId, itemId)                     ❌

// Work report detail
loadWorkReportDetail(reportId)                       ❌

// Lead detail
loadLeadDetail(leadId)                               ❌

// Properties
createProperty(clientId, data)                       ❌
updateProperty(propertyId, data)                     ❌
deleteProperty(propertyId)                           ❌
loadPropertyDetail(propertyId)                       ❌

// Notifications
loadNotifications()                                  ❌
markNotificationRead(notificationId)                 ❌

// Pricing
loadPricingRules()                                   ❌
createPricingRule(data)                              ❌

// Hierarchy (dle HIERARCHY.md)
createClientWithFirstAction(...)                     ❌
createJobWithFirstAction(...)                        ❌
completeTaskWithReplacement(taskId, ...)             ❌
loadHierarchyIntegrity()                             ❌
deactivateUser(userId, onBlocked)                    ❌
setClientNextAction(clientId, taskId)                ❌
setJobNextAction(jobId, taskId)                      ❌

// Export
exportCrmDataReal()                                  ❌ (stub zatím)

// Search
searchClients(query)                                 ❌
```

---

## 3. UI Composable obrazovky – stav

### Existující obrazovky ✅

```
LoginScreen          – přihlášení + biometrika
OnboardingScreen     – setup firmy
HomeScreen           – chat + voice
CrmHubScreen         – 10 tabů
  DashboardTab       – přehled
  ClientsListTab     – seznam klientů
  JobsListTab        – seznam zakázek
  TasksTab           – seznam úkolů
  LeadsListTab       – seznam leadů
  QuotesListTab      – seznam nabídek
  InvoicesListTab    – seznam faktur
  WorkReportsTab     – seznam reportů
  ContactsDirectoryTab – sdílené kontakty
  CommunicationTab   – komunikace
ClientDetailScreen   – detail klienta (5 tabů)
  ClientInfoTab      – info + sazby
  ClientJobsTab      – zakázky klienta
  ClientTasksTab     – úkoly klienta
  ClientCommsTab     – komunikace
  ClientNotesTab     – poznámky
JobDetailScreen      – detail zakázky (foto, audit, úkoly)
TaskDetailScreen     – detail úkolu
TasksScreen          – standalone úkoly
CalendarScreen       – kalendář
ToolsScreen          – rozpoznávání rostlin/hub
  PlantRecognitionTab – rostliny, zdraví, houby
SettingsScreen       – nastavení
```

### Chybějící obrazovky ❌

```
InvoiceDetailScreen       ❌
  – položky faktury, platby, tisk/export PDF
  – navigace z InvoicesListTab chybí

QuoteDetailScreen         ❌
  – editace položek, schválení, konverze na zakázku
  – QuotesListTab existuje bez detailu

WorkReportDetailScreen    ❌
  – pracovníci, zápisy, materiály, odpad
  – navigace z WorkReportsTab chybí

LeadDetailScreen          ❌
  – detail leadu, timeline, konverze
  – LeadsListTab má jen inline edit

PropertyDetailScreen      ❌
  – detail nemovitosti, zóny
  – žádná property UI mimo refresh do state

NotificationsScreen       ❌
  – badge count = 0 vždy
  – žádné UI pro notifikace

PaymentsDialog            ❌
  – záznam platby faktury
  – API endpoint existuje, UI chybí

PricingRulesScreen        ❌
  – správa cenových pravidel
  – dostupné jen přes admin API

WhatsAppScreen            ❌
  – přímé posílání zpráv
  – jen přes hlas/AI příkaz

AdminScreen               ❌
  – správa hierarchie, integrity report
  – dle HIERARCHY.md
```

---

## 4. Chybějící vazby (dependency gaps)

```
Job → Tasks
  Server endpoint GET /crm/jobs/{id}/tasks:    ❌ chybí
  Android řešení: filter z globálního tasks listu  ⚠️ neefektivní

Client → Properties
  Android: properties se načítají do state ✅
  ClientDetailScreen: chybí PropertyTab          ❌

Invoice ↔ Payment
  SecretaryApi: getPayments, addPayment          ✅
  ViewModel funkce:                              ❌ žádné
  UI:                                            ❌ žádné

Quote → Job (po schválení)
  approveQuote() volá API ✅
  Navigace na nový job po schválení:             ❌ chybí

Work Report → Invoice
  createInvoiceFromWorkReport() ✅
  Zobrazení výsledného invoice ID:               ❌ chybí

Voice Session → Work Report editing
  voiceSessionSummary zobrazuje souhrn ✅
  Editace před finálním uložením:                ❌ chybí

Task → Calendar (sync)
  addTaskToCalendar() ✅ jednosměrné
  Změna termínu tasku → update v kalendáři:      ❌ chybí

Notification system
  DB notifikace (server) ✅
  FCM/push delivery:                             ❌ chybí
  Badge count v UI:                              ❌ vždy 0

ArchiveClient
  SecretaryApi.archiveClient() ✅
  ViewModel volání:                              ❌ nevoláno
  UI tlačítko archivace:                         ❌ chybí
```

---

## 5. Bezpečnostní problémy (Android)

| Problém | Řádek | Závažnost |
|---------|-------|-----------|
| Tokeny v plain SharedPreferences | SettingsManager | 🔴 KRITICKÁ |
| `runBlocking` v OkHttp interceptoru | MainActivity ~2293 | 🔴 KRITICKÁ |
| Chybí SSL pinning | OkHttpClient | 🟠 VYSOKÁ |
| Forced `!!` unwrap bez ošetření | 680, 685, 846, 2208 | 🟠 VYSOKÁ |
| God ViewModel – 4600 řádků | MainActivity | 🟠 VYSOKÁ |
| Chybí network connectivity check | – | 🟠 VYSOKÁ |
| Optimistic updates bez rollback | createTaskManual, completeTask | 🟡 STŘEDNÍ |
| History nečistí se při logout | onVoiceInput history | 🟡 STŘEDNÍ |
| Tlačítka nedeaktivují se při API volání | – | 🟡 STŘEDNÍ |
| Duplicitní import kotlinx.coroutines.launch | řádky 47, 63 | 🟢 NÍZKÁ |

---

## 6. Prioritní pořadí implementace

### Sprint A – Bezpečnost
1. Tokeny → EncryptedSharedPreferences
2. Nahradit `runBlocking` v interceptoru
3. SSL pinning
4. Opravit forced `!!` unwrap

### Sprint B – Chybějící obrazovky
1. InvoiceDetailScreen (položky + platby)
2. QuoteDetailScreen (editace položek)
3. WorkReportDetailScreen
4. LeadDetailScreen
5. PaymentsDialog

### Sprint C – Chybějící ViewModel funkce
1. Invoice items CRUD VM funkce
2. Payments VM funkce
3. Notifications VM funkce
4. Lead detail VM funkce
5. Property CRUD VM funkce

### Sprint D – Hierarchy (dle HIERARCHY.md)
1. AddClientWizard
2. AddJobWizard
3. TaskCompletionDialog
4. HierarchyIntegrityCard v Dashboardu
5. AdminScreen pro hierarchy
