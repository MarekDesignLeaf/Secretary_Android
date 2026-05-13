package com.example.secretary

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

data class MessageRequest(
    val text: String,
    val history: List<ChatMessage> = emptyList(),
    val context_entity_id: Long? = null,
    val context_type: String? = null,
    val internal_language: String = "cs-CZ",
    val external_language: String = "en-GB",
    val calendar_context: String? = null
)

data class AssistantResponse(
    val reply_cs: String,
    val action_type: String? = null,
    val action_data: Map<String, Any>? = null,
    val needs_confirmation: Boolean = true,
    val is_question: Boolean = false
)

data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val deadline: String?,
    val is_completed: Boolean,
    val priority: String
)

// --- CRM MODELS ---

data class Client(
    val id: Long,
    val client_code: String,
    val display_name: String,
    val email_primary: String?,
    val phone_primary: String?,
    val status: String,
    val is_commercial: Boolean
)

data class ClientDetail(
    val client: Client,
    val properties: List<Property>,
    val recent_jobs: List<Job>,
    val communications: List<Communication>
)

data class Property(
    val id: Long,
    val client_id: Long,
    val property_code: String,
    val property_name: String,
    val address_line1: String,
    val city: String,
    val postcode: String,
    val status: String
)

data class Lead(
    val id: Long,
    val lead_code: String,
    val lead_name: String?,
    val status: String,
    val received_at: String
)

data class Job(
    val id: Long,
    val job_number: String,
    val job_title: String,
    val job_status: String,
    val start_date_planned: String?
)

data class Invoice(
    val id: Long,
    val invoice_number: String,
    val grand_total: Double,
    val status: String,
    val due_date: String?
)

data class WasteLoad(
    val id: Long,
    val waste_type: String,
    val quantity: Double,
    val unit: String,
    val load_date: String
)

data class Communication(
    val id: Long,
    val subject: String? = null,
    val message_summary: String,
    val sent_at: String?,
    val direction: String
)

enum class ConnectionStatus {
    UNKNOWN, TESTING, CONNECTED, DISCONNECTED
}
