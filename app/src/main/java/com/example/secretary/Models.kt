package com.example.secretary

import java.io.File

data class ChatMessage(
    val role: String,
    val content: String
)

data class MessageRequest(
    val text: String,
    val history: List<ChatMessage> = emptyList(),
    val context_entity_id: Long? = null,
    val context_type: String? = null,
    val internal_language: String = "cs-CZ",
    val external_language: String = "en-GB",
    val calendar_context: String? = null,
    val current_datetime: String? = null
)

data class AssistantResponse(
    val reply_cs: String,
    val action_type: String? = null,
    val action_data: Map<String, @JvmSuppressWildcards Any>? = null,
    val needs_confirmation: Boolean = false,
    val is_question: Boolean = false
)

data class SummarizeRequest(
    val history: List<ChatMessage>,
    val user_id: Long? = null,
    val tenant_id: Int = 1,
    val internal_language: String = "cs"
)

data class SummarizeResponse(
    val stored: Boolean = false,
    val summary: String? = null,
    val reason: String? = null,
    val error: String? = null
)

data class AssistantMemoryItem(
    val id: Long = 0,
    val memory_type: String = "long",
    val content: String = "",
    val updated_at: String? = null
)

data class BackendPermission(
    val permission_code: String = "",
    val module_name: String = "",
    val name: String = "",
    val description: String? = null
)

data class BackendRole(
    val role_name: String = "",
    val description: String? = null,
    val permissions: Map<String, Boolean> = emptyMap(),
    val permission_details: List<BackendPermission> = emptyList()
)

data class BackendUser(
    val id: Long = 0,
    val display_name: String = "",
    val email: String = "",
    val phone: String? = null,
    val status: String = "active",
    val role_name: String? = null,
    val must_change_password: Boolean = false,
    val created_at: String? = null,
    val permissions: Map<String, Boolean> = emptyMap(),
    val role_permissions: Map<String, Boolean> = emptyMap(),
    val user_permission_overrides: Map<String, Boolean> = emptyMap()
)

data class FirstLoginUser(
    val id: Long = 0,
    val display_name: String = "",
    val email: String = ""
)

data class WorkflowActionDraft(
    val title: String = "",
    val assignedUserId: Long? = null,
    val assignedTo: String? = null,
    val plannedStartAt: String? = null,
    val deadline: String? = null,
    val priority: String = "bezna",
    val planningNote: String? = null
)

data class ClientCreationDraft(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val ownerUserId: Long? = null,
    val firstAction: WorkflowActionDraft = WorkflowActionDraft()
)

data class JobCreationDraft(
    val title: String = "",
    val clientId: Long? = null,
    val clientName: String? = null,
    val assignedUserId: Long? = null,
    val assignedTo: String? = null,
    val startDate: String? = null,
    val firstAction: WorkflowActionDraft = WorkflowActionDraft()
)

data class TaskCreationDraft(
    val title: String = "",
    val taskType: String = "interni_poznamka",
    val priority: String = "bezna",
    val clientId: Long? = null,
    val clientName: String? = null,
    val jobId: Long? = null,
    val assignedUserId: Long? = null,
    val assignedTo: String? = null,
    val plannedStartAt: String? = null,
    val deadline: String? = null,
    val planningNote: String? = null,
    val setAsNextAction: Boolean = false
)

// === CLIENT — matches DB: clients table (schema.sql) ===
data class Client(
    val id: Long = 0,
    val client_code: String? = null,
    val client_type: String? = "residential",
    val title: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val display_name: String = "",
    val company_name: String? = null,
    val company_registration_no: String? = null,
    val vat_no: String? = null,
    val phone_primary: String? = null,
    val phone_secondary: String? = null,
    val email_primary: String? = null,
    val email_secondary: String? = null,
    val website: String? = null,
    val preferred_contact_method: String? = "email",
    val billing_address_line1: String? = null,
    val billing_city: String? = null,
    val billing_postcode: String? = null,
    val billing_country: String? = "GB",
    val status: String? = "active",
    val is_commercial: Boolean = false,
    val owner_user_id: Long? = null,
    val next_action_task_id: String? = null,
    val hierarchy_status: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val tenant_id: Int? = 1,
    val default_hourly_rate: Double? = 0.0
)

data class ClientDetail(
    val client: Client = Client(),
    val properties: List<Property> = emptyList(),
    val recent_jobs: List<Job> = emptyList(),
    val communications: List<Communication> = emptyList(),
    val tasks: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
    val notes: List<ClientNote> = emptyList(),
    val service_rates: Map<String, Double> = emptyMap(),
    val service_rate_overrides: Map<String, Double> = emptyMap(),
    val has_individual_service_rates: Boolean = false
)

data class ClientNote(
    val id: Long = 0,
    val note: String = "",
    val created_by: String? = null,
    val created_at: String? = null
)

data class SyncedContactCandidate(
    val contact_key: String = "",
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val address_line1: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val billing_address_line1: String? = null,
    val billing_city: String? = null,
    val billing_postcode: String? = null,
    val billing_country: String? = null,
    val selected_as_client: Boolean = false,
    val linked_client_id: Long? = null,
    val linked_client_name: String? = null
)

data class ContactSyncResponse(
    val total_contacts: Int = 0,
    val selected_clients: Int = 0,
    val contacts: List<SyncedContactCandidate> = emptyList(),
    val errors: List<String> = emptyList()
)

data class ContactSection(
    val section_code: String = "",
    val display_name: String = "",
    val is_default: Boolean = false,
    val sort_order: Int = 0
)

data class SharedContact(
    val id: Long = 0,
    val section_code: String = "",
    val section_name: String? = null,
    val display_name: String = "",
    val company_name: String? = null,
    val phone_primary: String? = null,
    val email_primary: String? = null,
    val address: String? = null,
    val address_line1: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val notes: String? = null,
    val source: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class SharedContactImportResult(
    val imported: Int = 0,
    val merged: Int = 0,
    val errors: List<String> = emptyList()
)

data class PlantPhotoUpload(
    val file: File,
    val organ: String,
    val label: String
)

data class RecognitionCaptureContext(
    val capturedAt: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationSource: String? = null
)

data class PlantRecognitionSuggestion(
    val display_name: String = "",
    val scientific_name: String = "",
    val common_names: List<String> = emptyList(),
    val family: String? = null,
    val genus: String? = null,
    val score: Double = 0.0
)

data class PlantRecognitionResponse(
    val database: String = "Pl@ntNet",
    val display_name: String = "",
    val scientific_name: String = "",
    val common_names: List<String> = emptyList(),
    val family: String? = null,
    val genus: String? = null,
    val score: Double = 0.0,
    val organs: List<String> = emptyList(),
    val guidance: String? = null,
    val spoken_summary: String? = null,
    val suggestions: List<PlantRecognitionSuggestion> = emptyList()
)

data class PlantDiseaseTreatment(
    val chemical: List<String> = emptyList(),
    val biological: List<String> = emptyList(),
    val prevention: List<String> = emptyList()
)

data class PlantDiseaseSuggestion(
    val name: String = "",
    val probability: Double = 0.0,
    val common_names: List<String> = emptyList(),
    val description: String? = null,
    val treatment: PlantDiseaseTreatment = PlantDiseaseTreatment(),
    val classification: List<String> = emptyList()
)

data class PlantDiseaseResponse(
    val database: String = "Plant.id Health Assessment",
    val is_healthy: Boolean = false,
    val health_probability: Double = 0.0,
    val top_issue_name: String? = null,
    val top_issue_common_names: List<String> = emptyList(),
    val top_issue_probability: Double = 0.0,
    val top_issue_description: String? = null,
    val guidance: String? = null,
    val spoken_summary: String? = null,
    val suggestions: List<PlantDiseaseSuggestion> = emptyList()
)

data class MushroomRecognitionSuggestion(
    val name: String = "",
    val common_names: List<String> = emptyList(),
    val probability: Double = 0.0,
    val description: String? = null,
    val url: String? = null,
    val edibility: String? = null,
    val psychoactive: Boolean? = null,
    val family: String? = null,
    val genus: String? = null,
    val look_alikes: List<String> = emptyList(),
    val characteristics: List<String> = emptyList()
)

data class MushroomRecognitionResponse(
    val database: String = "mushroom.id",
    val display_name: String = "",
    val scientific_name: String = "",
    val common_names: List<String> = emptyList(),
    val probability: Double = 0.0,
    val description: String? = null,
    val url: String? = null,
    val edibility: String? = null,
    val psychoactive: Boolean? = null,
    val family: String? = null,
    val genus: String? = null,
    val look_alikes: List<String> = emptyList(),
    val characteristics: List<String> = emptyList(),
    val guidance: String? = null,
    val spoken_summary: String? = null,
    val suggestions: List<MushroomRecognitionSuggestion> = emptyList()
)

data class RecognitionHistoryPhoto(
    val filename: String = "",
    val photo_type: String = "capture",
    val url: String = ""
)

data class RecognitionHistoryEntry(
    val id: Long = 0,
    val recognition_type: String = "",
    val recognition_label: String = "",
    val display_name: String = "",
    val scientific_name: String = "",
    val confidence: Double = 0.0,
    val guidance: String? = null,
    val database: String? = null,
    val captured_at: String? = null,
    val created_at: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy_meters: Double? = null,
    val location_source: String? = null,
    val owner_user_id: Long? = null,
    val owner_display_name: String = "",
    val owner_email: String = "",
    val photos: List<RecognitionHistoryPhoto> = emptyList(),
    val result: Map<String, @JvmSuppressWildcards Any?> = emptyMap()
)

data class AdminActivityLogEntry(
    val id: Long = 0,
    val entity_type: String = "",
    val entity_id: String = "",
    val action: String = "",
    val description: String = "",
    val source_channel: String = "",
    val created_at: String = "",
    val actor_user_id: Long? = null,
    val actor_display_name: String = "",
    val actor_email: String = "",
    val details: Map<String, @JvmSuppressWildcards Any?> = emptyMap()
)

data class HierarchyIntegritySummary(
    val orphan_clients: Int = 0,
    val orphan_jobs: Int = 0,
    val orphan_tasks: Int = 0,
    val blocked_user_deactivations: Int = 0,
    val next_action_mismatches: Int = 0
)

data class HierarchyEntityIssue(
    val id: Long = 0,
    val display_name: String? = null,
    val job_title: String? = null,
    val client_id: Long? = null,
    val owner_user_id: Long? = null,
    val assigned_user_id: Long? = null,
    val next_action_task_id: String? = null,
    val issues: List<String> = emptyList(),
    val entity_type: String? = null
)

data class HierarchyTaskIssue(
    val id: String = "",
    val title: String = "",
    val client_id: Long? = null,
    val job_id: Long? = null,
    val assigned_user_id: Long? = null,
    val status: String? = null,
    val issues: List<String> = emptyList()
)

data class BlockedUserDeactivation(
    val id: Long = 0,
    val display_name: String = "",
    val email: String = "",
    val owns_clients: Boolean = false,
    val owns_jobs: Boolean = false,
    val has_open_tasks: Boolean = false,
    val is_client_next_action_assignee: Boolean = false,
    val is_job_next_action_assignee: Boolean = false
)

data class HierarchyIntegrityReport(
    val tenant_id: Int = 0,
    val orphan_clients: List<HierarchyEntityIssue> = emptyList(),
    val orphan_jobs: List<HierarchyEntityIssue> = emptyList(),
    val orphan_tasks: List<HierarchyTaskIssue> = emptyList(),
    val blocked_user_deactivations: List<BlockedUserDeactivation> = emptyList(),
    val next_action_mismatches: List<HierarchyEntityIssue> = emptyList(),
    val summary: HierarchyIntegritySummary = HierarchyIntegritySummary()
)

data class ImportableSharedContact(
    val contact_key: String = "",
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val address_line1: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val selected: Boolean = false,
    val section_code: String = ""
)

// === PROPERTY — matches DB: properties table ===
data class Property(
    val id: Long = 0,
    val client_id: Long = 0,
    val property_code: String? = null,
    val property_name: String = "",
    val property_type: String? = null,
    val address_line1: String = "",
    val city: String = "",
    val postcode: String = "",
    val country: String = "GB",
    val status: String? = "active"
)

// === JOB — matches DB: jobs table ===
data class Job(
    val id: Long = 0,
    val job_number: String? = null,
    val client_id: Long? = null,
    val client_name: String? = null,
    val property_id: Long? = null,
    val property_address: String? = null,
    val quote_id: Long? = null,
    val job_title: String = "",
    val job_status: String = "nova",
    val start_date_planned: String? = null,
    val planned_start_at: String? = null,
    val planned_end_at: String? = null,
    val assigned_user_id: Long? = null,
    val assigned_to: String? = null,
    val next_action_task_id: String? = null,
    val hierarchy_status: String? = null,
    val handover_note: String? = null,
    val handed_over_by: String? = null,
    val handed_over_at: String? = null,
    val calendar_sync_enabled: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class JobDetail(
    val job: Job = Job(),
    val tasks: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
    val notes: List<JobNote> = emptyList(),
    val photos: List<JobPhoto> = emptyList(),
    val audit_log: List<JobAuditEntry> = emptyList()
)

data class JobNote(
    val id: Long = 0,
    val job_id: Long = 0,
    val note: String = "",
    val note_type: String = "general", // general, complication, handover
    val created_by: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class JobPhoto(
    val id: Long = 0,
    val job_id: Long = 0,
    val url: String = "",
    val description: String? = null,
    val photo_type: String = "general", // start, process, end, complication
    val uploaded_by: String? = null,
    val uploaded_at: String? = null
)

data class JobAuditEntry(
    val id: Long = 0,
    val job_id: Long = 0,
    val action_type: String = "",
    val description: String = "",
    val user_name: String? = null,
    val created_at: String = ""
)

// === LEAD — matches DB: leads table + ALTER columns ===
data class Lead(
    val id: Long = 0,
    val lead_code: String? = null,
    val lead_source: String? = null,
    val contact_name: String? = null,
    val contact_email: String? = null,
    val contact_phone: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val status: String = "new",
    val client_id: Long? = null,
    val job_id: Long? = null,
    val received_at: String? = null,
    val updated_at: String? = null
)

// === INVOICE — matches DB: invoices table ===
data class Invoice(
    val id: Long = 0,
    val invoice_number: String? = null,
    val client_id: Long? = null,
    val client_name: String? = null,
    val grand_total: Double = 0.0,
    val status: String = "draft",
    val due_date: String? = null,
    val created_at: String? = null
)

// === COMMUNICATION — matches DB: communications table + ALTER ===
data class Communication(
    val id: Long = 0,
    val client_id: Long? = null,
    val client_name: String? = null,
    val job_id: Long? = null,
    val job_title: String? = null,
    val comm_type: String? = "telefon",
    val source: String? = null,
    val external_message_id: String? = null,
    val source_phone: String? = null,
    val target_phone: String? = null,
    val conversation_key: String? = null,
    val subject: String? = null,
    val message_summary: String = "",
    val sent_at: String? = null,
    val direction: String = "inbound",
    val notes: String? = null,
    val created_at: String? = null,
    val imported_at: String? = null
)

// === TASK — matches DB: tasks table (EXTRA_TABLES_SQL) ===
data class Task(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val taskType: String = "interni_poznamka",
    val status: String = "novy",
    val priority: String = "bezna",
    val createdAt: String? = null,
    val deadline: String? = null,
    val plannedDate: String? = null,
    val plannedStartAt: String? = null,
    val plannedEndAt: String? = null,
    val timeWindowStart: String? = null,
    val timeWindowEnd: String? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val createdBy: String? = null,
    val assignedUserId: Long? = null,
    val assignedTo: String? = null,
    val planningNote: String? = null,
    val reminderForAssigneeOnly: Boolean = true,
    val delegatedBy: String? = null,
    val clientId: Long? = null,
    val clientName: String? = null,
    val jobId: Long? = null,
    val propertyId: Long? = null,
    val propertyAddress: String? = null,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    val result: String? = null,
    val notes: List<String> = emptyList(),
    val communicationMethod: String? = null,
    val source: String? = null,
    val isBillable: Boolean = false,
    val hasCost: Boolean = false,
    val waitingForPayment: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
    val calendarSyncEnabled: Boolean = true,
    val isCompleted: Boolean = false
)

data class CalendarFeedEntry(
    val entry_key: String = "",
    val entry_type: String = "",
    val source_id: String? = null,
    val title: String = "",
    val client_name: String? = null,
    val job_title: String? = null,
    val assigned_user_id: Long? = null,
    val assigned_to: String? = null,
    val is_assigned_to_current: Boolean = false,
    val display_mode: String = "shared",
    val planned_start_at: String? = null,
    val planned_end_at: String? = null,
    val planned_date: String? = null,
    val description: String? = null,
    val calendar_sync_enabled: Boolean = true,
    val reminder_for_assignee_only: Boolean = true,
    val status: String? = null
)

data class ChecklistItem(
    val id: String = "",
    val text: String = "",
    val isChecked: Boolean = false
)

data class WasteLoad(
    val id: Long = 0,
    val waste_type: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val load_date: String = ""
)

// === QUOTE — matches DB: quotes + quote_items ===
data class Quote(
    val id: Long = 0,
    val quote_number: String? = null,
    val client_id: Long? = null,
    val client_name: String? = null,
    val quote_title: String? = null,
    val status: String = "draft",
    val grand_total: Double = 0.0,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class QuoteItem(
    val id: Long = 0,
    val description: String = "",
    val quantity: Double = 1.0,
    val unit_price: Double = 0.0,
    val total: Double = 0.0,
    val sort_order: Int = 0
)

// === WORK REPORT — matches DB: work_reports + sub-tables ===
data class WorkReport(
    val id: Long = 0,
    val client_id: Long? = null,
    val client_name: String? = null,
    val work_date: String? = null,
    val total_hours: Double = 0.0,
    val total_price: Double = 0.0,
    val currency: String = "GBP",
    val notes: String? = null,
    val status: String = "draft",
    val input_type: String? = null,
    val created_at: String? = null,
    val workers: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
    val entries: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
    val waste: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
    val materials: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList()
)

enum class ConnectionStatus { UNKNOWN, TESTING, CONNECTED, DISCONNECTED }
