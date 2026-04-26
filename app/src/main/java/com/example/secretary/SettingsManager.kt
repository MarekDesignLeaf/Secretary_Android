package com.example.secretary

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import java.security.MessageDigest
import java.util.Locale

data class UserProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val role: String = "viewer",
    val passwordHash: String = "",
    val permissions: Map<String, Boolean> = defaultPermissions(role)
) {
    companion object {
        fun defaultPermissions(role: String): Map<String, Boolean> = when (role) {
            "admin" -> mapOf("crm_read" to true, "crm_write" to true, "crm_delete" to true, "calendar_read" to true, "calendar_write" to true, "contacts_read" to true, "contacts_write" to true, "voice_commands" to true, "settings_access" to true, "import_data" to true, "export_data" to true, "manage_users" to true)
            "manager" -> mapOf("crm_read" to true, "crm_write" to true, "crm_delete" to false, "calendar_read" to true, "calendar_write" to true, "contacts_read" to true, "contacts_write" to true, "voice_commands" to true, "settings_access" to true, "import_data" to true, "export_data" to true, "manage_users" to false)
            "worker" -> mapOf("crm_read" to true, "crm_write" to false, "crm_delete" to false, "calendar_read" to true, "calendar_write" to true, "contacts_read" to true, "contacts_write" to false, "voice_commands" to true, "settings_access" to false, "import_data" to false, "export_data" to false, "manage_users" to false)
            else -> mapOf("crm_read" to true, "crm_write" to false, "crm_delete" to false, "calendar_read" to true, "calendar_write" to false, "contacts_read" to true, "contacts_write" to false, "voice_commands" to false, "settings_access" to false, "import_data" to false, "export_data" to false, "manage_users" to false)
        }
    }
}

data class SavedSignature(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Novy podpis",
    val content: String = ""
)

data class BiometricProfile(
    val userId: Long? = null,
    val displayName: String = "",
    val email: String = "",
    val password: String = ""
)

data class VoiceAlias(
    val alias: String = "",
    val target: String = "",
    val targetType: String = "contact",
    val uses: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secretary_settings", Context.MODE_PRIVATE)
    internal val prefsPublic: SharedPreferences get() = prefs
    private val gson = Gson()

    private fun scopedKey(base: String, userId: Long = currentBackendUserId): String? =
        if (userId > 0L) "${base}_user_$userId" else null

    private fun getScopedBoolean(base: String, default: Boolean): Boolean {
        val scoped = scopedKey(base)
        return if (scoped != null && prefs.contains(scoped)) prefs.getBoolean(scoped, default) else prefs.getBoolean(base, default)
    }

    private fun setScopedBoolean(base: String, value: Boolean) {
        val scoped = scopedKey(base)
        prefs.edit {
            if (scoped != null) putBoolean(scoped, value) else putBoolean(base, value)
        }
    }

    private fun getScopedString(base: String, default: String): String {
        val scoped = scopedKey(base)
        return when {
            scoped != null && prefs.contains(scoped) -> prefs.getString(scoped, default) ?: default
            else -> prefs.getString(base, default) ?: default
        }
    }

    private fun setScopedString(base: String, value: String) {
        val scoped = scopedKey(base)
        prefs.edit {
            if (scoped != null) putString(scoped, value) else putString(base, value)
        }
    }

    private fun getScopedFloat(base: String, default: Float): Float {
        val scoped = scopedKey(base)
        return if (scoped != null && prefs.contains(scoped)) prefs.getFloat(scoped, default) else prefs.getFloat(base, default)
    }

    private fun setScopedFloat(base: String, value: Float) {
        val scoped = scopedKey(base)
        prefs.edit {
            if (scoped != null) putFloat(scoped, value) else putFloat(base, value)
        }
    }

    private fun getScopedLong(base: String, default: Long): Long {
        val scoped = scopedKey(base)
        return if (scoped != null && prefs.contains(scoped)) prefs.getLong(scoped, default) else prefs.getLong(base, default)
    }

    private fun setScopedLong(base: String, value: Long) {
        val scoped = scopedKey(base)
        prefs.edit {
            if (scoped != null) putLong(scoped, value) else putLong(base, value)
        }
    }

    // 1. Hlasove ovladani
    var hotwordEnabled: Boolean
        get() = getScopedBoolean("hotword_enabled", true)
        set(v) = setScopedBoolean("hotword_enabled", v)
    var activationWord: String
        get() = getScopedString("activation_word", "hej designleaf")
        set(v) = setScopedString("activation_word", v)

    var avoidAsterisksInReplies: Boolean
        get() = getScopedBoolean("avoid_asterisks_in_replies", true)
        set(v) = setScopedBoolean("avoid_asterisks_in_replies", v)
    var recognitionLanguage: String
        get() = getScopedString("recognition_lang", "cs-CZ")
        set(v) = setScopedString("recognition_lang", v)
    var ttsRate: Float
        get() = getScopedFloat("tts_rate", 1.0f)
        set(v) = setScopedFloat("tts_rate", v)
    var ttsPitch: Float
        get() = getScopedFloat("tts_pitch", 1.0f)
        set(v) = setScopedFloat("tts_pitch", v)
    var silenceLength: Long
        get() = getScopedLong("silence_length", 3500L)
        set(v) = setScopedLong("silence_length", v)
    var pendingVoiceSessionId: String?
        get() {
            val scoped = scopedKey("pending_voice_session")
            return when {
                scoped != null && prefs.contains(scoped) -> prefs.getString(scoped, null)
                else -> prefs.getString("pending_voice_session", null)
            }
        }
        set(v) = prefs.edit {
            val scoped = scopedKey("pending_voice_session")
            when {
                scoped != null && v == null -> remove(scoped)
                scoped != null -> putString(scoped, v)
                v == null -> remove("pending_voice_session")
                else -> putString("pending_voice_session", v)
            }
        }

    // === JWT AUTH TOKENS ===
    var accessToken: String?
        get() = prefs.getString("jwt_access_token", null)
        set(v) = prefs.edit { if (v == null) remove("jwt_access_token") else putString("jwt_access_token", v) }
    var refreshToken: String?
        get() = prefs.getString("jwt_refresh_token", null)
        set(v) = prefs.edit { if (v == null) remove("jwt_refresh_token") else putString("jwt_refresh_token", v) }

    // FIX A9/A3: store the email used at login so CalendarManager and task creation can use it
    var loginEmail: String
        get() = prefs.getString("login_email", "") ?: ""
        set(v) = prefs.edit { if (v.isBlank()) remove("login_email") else putString("login_email", v) }

    // 2. Server
    var apiUrl: String
        get() {
            val saved = prefs.getString("api_url", null)
            return if (saved.isNullOrBlank()) BuildConfig.BASE_URL else saved
        }
        set(v) = prefs.edit { putString("api_url", v) }
    var offlineMode: Boolean
        get() = prefs.getBoolean("offline_mode", false)
        set(v) = prefs.edit { putBoolean("offline_mode", v) }

    // 3. CRM
    var autoRefreshInterval: Int
        get() = prefs.getInt("refresh_interval", 0)
        set(v) = prefs.edit { putInt("refresh_interval", v) }
    var defaultCrmTab: Int
        get() = prefs.getInt("default_crm_tab", 0)
        set(v) = prefs.edit { putInt("default_crm_tab", v) }
    var clientSortOrder: String
        get() = prefs.getString("client_sort", "name") ?: "name"
        set(v) = prefs.edit { putString("client_sort", v) }

    // 4. Notifikace
    var persistentNotification: Boolean
        get() = prefs.getBoolean("persistent_notification", true)
        set(v) = prefs.edit { putBoolean("persistent_notification", v) }
    var reminderMinutes: Int
        get() = prefs.getInt("reminder_minutes", 15)
        set(v) = prefs.edit { putInt("reminder_minutes", v) }
    var notificationSound: String
        get() = prefs.getString("noti_sound", "default") ?: "default"
        set(v) = prefs.edit { putString("noti_sound", v) }

    // 5. Pracovni profil
    var workHoursEnabled: Boolean
        get() = getScopedBoolean("work_hours_enabled", false)
        set(v) = setScopedBoolean("work_hours_enabled", v)
    var workHoursStart: String
        get() = getScopedString("work_start", "07:00")
        set(v) = setScopedString("work_start", normalizeTimeInput(v, "07:00"))
    var workHoursEnd: String
        get() = getScopedString("work_end", "19:00")
        set(v) = setScopedString("work_end", normalizeTimeInput(v, "19:00"))
    // FIX A4: store timezone for work hours so check is correct when user travels
    var workHoursTimezone: String
        get() = getScopedString("work_hours_timezone", java.util.TimeZone.getDefault().id)
        set(v) = setScopedString("work_hours_timezone", v)
    var defaultTaskPriority: String
        get() = prefs.getString("default_priority", "normal") ?: "normal"
        set(v) = prefs.edit { putString("default_priority", v) }
    var emailSignature: String
        get() = prefs.getString("email_signature", "") ?: ""
        set(v) = prefs.edit { putString("email_signature", v) }
    var activeSignatureId: String
        get() = prefs.getString("active_signature_id", "") ?: ""
        set(v) = prefs.edit { putString("active_signature_id", v) }

    fun getSavedSignatures(): List<SavedSignature> {
        val json = prefs.getString("saved_signatures", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<SavedSignature>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    fun saveSignatures(s: List<SavedSignature>) = prefs.edit { putString("saved_signatures", gson.toJson(s)) }

    // 6. Data / Import
    var cacheSizeMb: Int
        get() = prefs.getInt("cache_size", 100)
        set(v) = prefs.edit { putInt("cache_size", v) }
    var importFilePath: String
        get() = prefs.getString("import_file_path", "") ?: ""
        set(v) = prefs.edit { putString("import_file_path", v) }
    var importTargetTable: String
        get() = prefs.getString("import_target_table", "clients") ?: "clients"
        set(v) = prefs.edit { putString("import_target_table", v) }
    var autoImportEnabled: Boolean
        get() = prefs.getBoolean("auto_import_enabled", false)
        set(v) = prefs.edit { putBoolean("auto_import_enabled", v) }

    // 7. Uzivatele a prava
    var adminPasswordHash: String
        get() = prefs.getString("admin_password_hash", "") ?: ""
        set(v) = prefs.edit { putString("admin_password_hash", v) }
    var activeUserId: String
        get() = prefs.getString("active_user_id", "admin") ?: "admin"
        set(v) = prefs.edit { putString("active_user_id", v) }

    fun getUserProfiles(): List<UserProfile> {
        val json = prefs.getString("user_profiles", null) ?: return listOf(UserProfile(id = "admin", name = "Admin", role = "admin"))
        return try { gson.fromJson(json, object : TypeToken<List<UserProfile>>() {}.type) } catch (e: Exception) { listOf(UserProfile(id = "admin", name = "Admin", role = "admin")) }
    }
    fun saveUserProfiles(p: List<UserProfile>) = prefs.edit { putString("user_profiles", gson.toJson(p)) }
    fun getActiveProfile(): UserProfile = getUserProfiles().find { it.id == activeUserId } ?: UserProfile(id = "admin", name = "Admin", role = "admin")
    fun hasPermission(p: String): Boolean = getActiveProfile().permissions[p] ?: false
    fun verifyPassword(pwd: String): Boolean { val h = adminPasswordHash; return h.isBlank() || hashPassword(pwd) == h }
    fun setAdminPassword(pwd: String) { adminPasswordHash = if (pwd.isBlank()) "" else hashPassword(pwd) }
    private fun hashPassword(pwd: String): String = MessageDigest.getInstance("SHA-256").digest(pwd.toByteArray()).joinToString("") { "%02x".format(it) }

    // 8. Motiv (theme)
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(v) = prefs.edit { putString("theme_mode", v) }

    // 9. Jazyk / Language + Backend user
    var currentBackendUserId: Long
        get() = prefs.getLong("current_backend_user_id", -1L)
        set(v) = prefs.edit {
            if (v <= 0L) remove("current_backend_user_id") else putLong("current_backend_user_id", v)
        }
    var currentBackendUserRole: String
        get() = prefs.getString("current_backend_user_role", "") ?: ""
        set(v) = prefs.edit {
            if (v.isBlank()) remove("current_backend_user_role") else putString("current_backend_user_role", v)
        }
    var currentUserDisplayName: String
        get() = prefs.getString("current_user_display_name", "") ?: ""
        set(v) = prefs.edit { if (v.isBlank()) remove("current_user_display_name") else putString("current_user_display_name", v) }
    var appLanguage: String
        get() = normalizeAppLanguage(prefs.getString("app_language", "cs") ?: "cs")
        set(v) = prefs.edit { putString("app_language", normalizeAppLanguage(v)) }
    fun setCurrentBackendUser(userId: Long?, role: String?, displayName: String? = null, email: String? = null) {
        currentBackendUserId = if ((userId ?: 0L) > 0L) userId!! else -1L
        currentBackendUserRole = role.orEmpty()
        if (!displayName.isNullOrBlank()) currentUserDisplayName = displayName
        // FIX A9/A3: persist login email for use in CalendarManager and task creation
        if (!email.isNullOrBlank()) loginEmail = email
    }
    fun clearCurrentBackendUser() {
        currentBackendUserId = -1L
        currentBackendUserRole = ""
        currentUserDisplayName = ""
        // Do NOT clear loginEmail on logout so CalendarManager can still find the calendar
    }
    fun getCurrentAppLanguage(): String {
        val userId = currentBackendUserId
        if (userId > 0L) {
            return normalizeAppLanguage(prefs.getString("app_language_user_$userId", appLanguage) ?: appLanguage)
        }
        return appLanguage
    }
    fun setCurrentAppLanguage(lang: String) {
        val normalized = normalizeAppLanguage(lang)
        val userId = currentBackendUserId
        if (userId > 0L) {
            prefs.edit { putString("app_language_user_$userId", normalized) }
        }
        appLanguage = normalized
    }
    fun getAppLanguageForUser(userId: Long?, fallback: String = appLanguage): String {
        val resolved = userId ?: currentBackendUserId
        if (resolved > 0L) {
            return normalizeAppLanguage(prefs.getString("app_language_user_$resolved", fallback) ?: fallback)
        }
        return normalizeAppLanguage(fallback)
    }

    fun getVoiceAliases(): List<VoiceAlias> {
        val scoped = scopedKey("voice_aliases")
        val json = when {
            scoped != null && prefs.contains(scoped) -> prefs.getString(scoped, null)
            else -> prefs.getString("voice_aliases", null)
        } ?: return emptyList()
        return try {
            gson.fromJson<List<VoiceAlias>>(json, object : TypeToken<List<VoiceAlias>>() {}.type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveVoiceAliases(aliases: List<VoiceAlias>) {
        val scoped = scopedKey("voice_aliases")
        val cleaned = aliases
            .filter { it.alias.isNotBlank() && it.target.isNotBlank() }
            .distinctBy { normalizeVoiceAlias(it.alias) }
            .take(300)
        prefs.edit {
            if (scoped != null) putString(scoped, gson.toJson(cleaned)) else putString("voice_aliases", gson.toJson(cleaned))
        }
    }

    fun upsertVoiceAlias(alias: String, target: String, targetType: String = "contact"): VoiceAlias? {
        val cleanAlias = alias.trim()
        val cleanTarget = target.trim()
        if (cleanAlias.length < 2 || cleanTarget.length < 2) return null
        val aliasKey = normalizeVoiceAlias(cleanAlias)
        val existing = getVoiceAliases().filterNot { normalizeVoiceAlias(it.alias) == aliasKey }
        val currentUses = getVoiceAliases().firstOrNull { normalizeVoiceAlias(it.alias) == aliasKey }?.uses ?: 0
        val saved = VoiceAlias(
            alias = cleanAlias,
            target = cleanTarget,
            targetType = targetType,
            uses = currentUses + 1,
            updatedAt = System.currentTimeMillis()
        )
        saveVoiceAliases(listOf(saved) + existing.sortedByDescending { it.updatedAt })
        return saved
    }

    fun removeVoiceAlias(alias: String): Boolean {
        val aliasKey = normalizeVoiceAlias(alias)
        val current = getVoiceAliases()
        val updated = current.filterNot { normalizeVoiceAlias(it.alias) == aliasKey }
        if (updated.size == current.size) return false
        saveVoiceAliases(updated)
        return true
    }

    private fun normalizeAppLanguage(lang: String): String {
        val normalized = Normalizer.normalize(lang.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return when {
            normalized.startsWith("cs") || normalized.startsWith("cz") || normalized.contains("czech") || normalized.contains("cestina") -> "cs"
            normalized.startsWith("pl") || normalized.contains("polish") || normalized.contains("polski") -> "pl"
            else -> "en"
        }
    }

    private fun normalizeVoiceAlias(value: String): String =
        Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()

    fun getBiometricProfiles(): List<BiometricProfile> {
        val json = prefs.getString("biometric_profiles", null)
        val profiles = try {
            if (json.isNullOrBlank()) emptyList() else gson.fromJson<List<BiometricProfile>>(json, object : TypeToken<List<BiometricProfile>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (profiles.isNotEmpty()) return profiles.distinctBy { it.email.lowercase() }
        val legacyEmail = prefs.getString("saved_email", null)
        val legacyPassword = prefs.getString("saved_pass", null)
        return if (!legacyEmail.isNullOrBlank() && !legacyPassword.isNullOrBlank()) {
            listOf(BiometricProfile(email = legacyEmail, password = legacyPassword, displayName = legacyEmail.substringBefore("@")))
        } else {
            emptyList()
        }
    }

    fun saveBiometricProfile(profile: BiometricProfile) {
        val updated = getBiometricProfiles()
            .filterNot { it.email.equals(profile.email, ignoreCase = true) }
            .plus(profile)
        prefs.edit {
            putString("biometric_profiles", gson.toJson(updated))
            putString("last_biometric_email", profile.email)
            remove("saved_email")
            remove("saved_pass")
        }
    }

    fun removeBiometricProfile(email: String) {
        val updated = getBiometricProfiles().filterNot { it.email.equals(email, ignoreCase = true) }
        prefs.edit {
            putString("biometric_profiles", gson.toJson(updated))
            if ((prefs.getString("last_biometric_email", null) ?: "").equals(email, ignoreCase = true)) {
                remove("last_biometric_email")
            }
            remove("saved_email")
            remove("saved_pass")
        }
    }

    fun clearBiometricProfiles() {
        prefs.edit {
            remove("biometric_profiles")
            remove("last_biometric_email")
            remove("saved_email")
            remove("saved_pass")
        }
    }

    fun getLastBiometricEmail(): String? = prefs.getString("last_biometric_email", null)

    fun setLastBiometricEmail(email: String?) {
        prefs.edit {
            if (email.isNullOrBlank()) remove("last_biometric_email") else putString("last_biometric_email", email)
        }
    }

    // Utility
    private fun normalizeTimeInput(value: String, default: String): String {
        val parts = value.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()
        val m = parts.getOrNull(1)?.toIntOrNull()
        if (h == null || m == null || h !in 0..23 || m !in 0..59) return default
        return "%02d:%02d".format(h, m)
    }
    fun resetAll() = prefs.edit { clear() }

    // FIX A4: use stored timezone so work hours check is correct when user travels
    fun isWithinWorkHours(): Boolean {
        if (!workHoursEnabled) return true
        return try {
            val tz = java.util.TimeZone.getTimeZone(workHoursTimezone)
            val now = java.util.Calendar.getInstance(tz)
            val cur = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            val sp = workHoursStart.split(":")
            val start = sp[0].toInt() * 60 + sp.getOrElse(1) { "0" }.toInt()
            val ep = workHoursEnd.split(":")
            val end = ep[0].toInt() * 60 + ep.getOrElse(1) { "0" }.toInt()
            cur in start..end
        } catch (e: Exception) { true }
    }
}
