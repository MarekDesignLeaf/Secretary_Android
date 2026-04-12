package com.example.secretary

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
    val reply: String? = null,
    val action_type: String? = null,
    val action_data: Map<String, @JvmSuppressWildcards Any>? = null,
    val needs_confirmation: Boolean = false,
    val is_question: Boolean = false
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
    val notes: List<ClientNote> = emptyList()
)

data class ClientNote(
    val id: Long = 0,
    val note: String = "",
    val created_by: String? = null,
    val created_at: String? = null
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
    val created_at: String? = null,
    val updated_at: String? = null
)

data class JobDetail(
    val job: Job = Job(),
    val tasks: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
    val notes: List<ClientNote> = emptyList()
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
    val subject: String? = null,
    val message_summary: String = "",
    val sent_at: String? = null,
    val direction: String = "inbound",
    val notes: String? = null,
    val created_at: String? = null
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
    val timeWindowStart: String? = null,
    val timeWindowEnd: String? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val createdBy: String? = null,
    val assignedTo: String? = null,
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
    val isCompleted: Boolean = false
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

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean = false,
    val calendarId: Long = 0
)
