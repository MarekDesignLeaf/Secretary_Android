package com.example.secretary

import retrofit2.Response
import retrofit2.http.*

data class RegisterRequest(
    val email: String,
    val password: String,
    val display_name: String,
    val role: String
)

interface SecretaryApi {
    // === AI PROCESS ===
    @POST("process")
    suspend fun processMessage(@Body request: MessageRequest): Response<AssistantResponse>

    // === CLIENTS ===
    @GET("crm/clients")
    suspend fun getClients(): Response<List<Client>>

    @GET("crm/clients/search")
    suspend fun searchClients(@Query("q") query: String): Response<List<Client>>

    @GET("crm/clients/{id}")
    suspend fun getClientDetail(@Path("id") id: Long): Response<ClientDetail>

    @POST("crm/clients")
    suspend fun createClient(@Body data: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any>>

    @PUT("crm/clients/{id}")
    suspend fun updateClient(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @DELETE("crm/clients/{id}")
    suspend fun archiveClient(@Path("id") id: Long): Response<Map<String, @JvmSuppressWildcards Any>>

    @POST("crm/clients/{id}/notes")
    suspend fun addClientNote(@Path("id") id: Long, @Body data: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any>>

    @POST("crm/clients/sync-contacts")
    suspend fun syncContacts(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === JOBS ===
    @GET("crm/jobs")
    suspend fun getJobs(@Query("client_id") clientId: Long? = null, @Query("status") status: String? = null): Response<List<Job>>

    @GET("crm/jobs/{id}")
    suspend fun getJobDetail(@Path("id") id: Long): Response<JobDetail>

    @POST("crm/jobs")
    suspend fun createJob(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @PUT("crm/jobs/{id}")
    suspend fun updateJob(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === TASKS ===
    @GET("crm/tasks")
    suspend fun getTasks(@Query("client_id") clientId: Long? = null, @Query("job_id") jobId: Long? = null): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @POST("crm/tasks")
    suspend fun createTask(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @PUT("crm/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @DELETE("crm/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Map<String, @JvmSuppressWildcards Any>>

    // === LEADS ===
    @GET("crm/leads")
    suspend fun getLeads(): Response<List<Lead>>

    @GET("crm/leads/{id}")
    suspend fun getLeadDetail(@Path("id") id: Long): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("crm/leads")
    suspend fun createLead(@Body data: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any>>

    @PUT("crm/leads/{id}")
    suspend fun updateLead(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @POST("crm/leads/{id}/convert-to-client")
    suspend fun convertLeadToClient(@Path("id") id: Long, @Body data: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any>>

    @POST("crm/leads/{id}/convert-to-job")
    suspend fun convertLeadToJob(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === INVOICES ===
    @GET("crm/invoices")
    suspend fun getInvoices(): Response<List<Invoice>>

    @POST("crm/invoices")
    suspend fun createInvoice(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @PUT("crm/invoices/{id}")
    suspend fun updateInvoice(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === COMMUNICATIONS ===

    // === COMMUNICATIONS ===
    @GET("crm/communications")
    suspend fun getCommunications(@Query("client_id") clientId: Long? = null, @Query("job_id") jobId: Long? = null): Response<List<Communication>>

    @POST("crm/communications")
    suspend fun logCommunication(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === PHOTOS ===
    @GET("crm/photos")
    suspend fun getPhotos(@Query("entity_type") entityType: String? = null, @Query("entity_id") entityId: String? = null): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @POST("crm/photos")
    suspend fun addPhoto(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === VOICE SESSION (Work Report) ===
    @POST("voice/session/start")
    suspend fun voiceSessionStart(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("voice/session/input")
    suspend fun voiceSessionInput(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("voice/session/resume")
    suspend fun voiceSessionResume(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === WORK REPORTS ===
    @GET("work-reports")
    suspend fun getWorkReports(@Query("tenant_id") tenantId: Int = 1): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @GET("work-reports/{id}")
    suspend fun getWorkReport(@Path("id") id: Long): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("work-reports")
    suspend fun createWorkReport(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === JOB NOTES ===
    @POST("crm/jobs/{id}/notes")
    suspend fun addJobNote(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    // === QUOTES ===
    @GET("crm/quotes")
    suspend fun getQuotes(@Query("tenant_id") tenantId: Int = 1, @Query("client_id") clientId: Long? = null): Response<List<Quote>>

    @GET("crm/quotes/{id}")
    suspend fun getQuoteDetail(@Path("id") id: Long): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("crm/quotes")
    suspend fun createQuote(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @PUT("crm/quotes/{id}")
    suspend fun updateQuote(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @POST("crm/quotes/{id}/items")
    suspend fun addQuoteItem(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @DELETE("crm/quotes/{id}/items/{itemId}")
    suspend fun deleteQuoteItem(@Path("id") id: Long, @Path("itemId") itemId: Long): Response<Map<String, @JvmSuppressWildcards Any>>

    @POST("crm/quotes/{id}/approve")
    suspend fun approveQuote(@Path("id") id: Long, @Body data: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): Response<Map<String, @JvmSuppressWildcards Any>>

    // === OTHER ===
    @GET("crm/properties")
    suspend fun getProperties(@Query("client_id") clientId: Long? = null): Response<List<Property>>

    @GET("crm/timeline")
    suspend fun getTimeline(@Query("entity_type") entityType: String? = null, @Query("entity_id") entityId: String? = null): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @GET("crm/export/csv")
    suspend fun exportCsv(): Response<okhttp3.ResponseBody>

    @GET("system/settings")
    suspend fun getSettings(): Response<Map<String, @JvmSuppressWildcards Any>>

    @GET("health")
    suspend fun healthCheck(): Response<Map<String, @JvmSuppressWildcards Any>>

    // === ONBOARDING ===
    @GET("onboarding/status/{tenantId}")
    suspend fun getOnboardingStatus(@Path("tenantId") tenantId: Int): Response<Map<String, @JvmSuppressWildcards Any?>>

    @GET("onboarding/industry-groups")
    suspend fun getIndustryGroups(): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @GET("onboarding/industry-subtypes/{groupId}")
    suspend fun getIndustrySubtypes(@Path("groupId") groupId: Long): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @POST("onboarding/company-setup")
    suspend fun companySetup(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any>>

    @GET("tenant/config/{tenantId}")
    suspend fun getTenantConfig(@Path("tenantId") tenantId: Int): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === AUTH ===
    @POST("auth/login")
    suspend fun authLogin(@Body data: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("auth/refresh")
    suspend fun authRefresh(@Body data: Map<String, String>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): Response<Map<String, @JvmSuppressWildcards Any?>>

    @GET("auth/roles")
    suspend fun getAuthRoles(): Response<List<BackendRole>>

    @GET("auth/users")
    suspend fun getAuthUsers(): Response<List<BackendUser>>

    @PUT("auth/users/{userId}")
    suspend fun updateAuthUser(
        @Path("userId") userId: Long,
        @Body data: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, @JvmSuppressWildcards Any?>>

    @DELETE("auth/users/{userId}")
    suspend fun deleteAuthUser(@Path("userId") userId: Long): Response<Map<String, @JvmSuppressWildcards Any?>>

    @GET("auth/me")
    suspend fun authMe(@retrofit2.http.Header("Authorization") token: String): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === INVOICE ITEMS ===
    @GET("crm/invoices/{invoiceId}/items")
    suspend fun getInvoiceItems(@Path("invoiceId") invoiceId: Long): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @POST("crm/invoices/{invoiceId}/items")
    suspend fun addInvoiceItem(@Path("invoiceId") invoiceId: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @DELETE("crm/invoices/{invoiceId}/items/{itemId}")
    suspend fun deleteInvoiceItem(@Path("invoiceId") invoiceId: Long, @Path("itemId") itemId: Long): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === PAYMENTS ===
    @GET("crm/invoices/{invoiceId}/payments")
    suspend fun getPayments(@Path("invoiceId") invoiceId: Long): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @POST("crm/invoices/{invoiceId}/payments")
    suspend fun addPayment(@Path("invoiceId") invoiceId: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === NOTIFICATIONS ===
    @GET("crm/notifications")
    suspend fun getNotifications(@Query("user_id") userId: Int? = null, @Query("unread_only") unreadOnly: Boolean = false): Response<List<Map<String, @JvmSuppressWildcards Any?>>>

    @PUT("crm/notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") notificationId: Long): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === DEFAULT RATES ===
    @GET("tenant/default-rates/{tenantId}")
    suspend fun getDefaultRates(@Header("Authorization") auth: String, @Path("tenantId") tenantId: Int): Response<Map<String, @JvmSuppressWildcards Any?>>

    @PUT("tenant/default-rates/{tenantId}")
    suspend fun updateDefaultRates(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === USER RATES ===
    @GET("crm/users/{userId}/rates")
    suspend fun getUserRates(@Path("userId") userId: Int): Response<Map<String, @JvmSuppressWildcards Any?>>

    @PUT("crm/users/{userId}/rates")
    suspend fun updateUserRates(@Path("userId") userId: Int, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === CLIENT RATE ===
    @PUT("crm/clients/{clientId}/rate")
    suspend fun updateClientRate(@Path("clientId") clientId: Long, @Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    // === INVOICE FROM WORK REPORT ===
    @POST("crm/invoices/from-work-report")
    suspend fun createInvoiceFromWorkReport(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("crm/invoices/batch-from-work-reports")
    suspend fun batchInvoiceFromWorkReports(@Body data: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, @JvmSuppressWildcards Any?>>
}
