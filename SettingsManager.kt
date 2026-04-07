package com.example.secretary

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

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

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secretary_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 1. Hlasove ovladani
    var hotwordEnabled: Boolean
        get() = prefs.getBoolean("hotword_enabled", true)
        set(v) = prefs.edit { putBoolean("hotword_enabled", v) }
    var activationWord: String
        get() = prefs.getString("activation_word", "hej kundo") ?: "hej kundo"
        set(v) = prefs.edit { putString("activation_word", v) }
    var recognitionLanguage: String
        get() = prefs.getString("recognition_lang", "cs-CZ") ?: "cs-CZ"
        set(v) = prefs.edit { putString("recognition_lang", v) }
    var ttsRate: Float
        get() = prefs.getFloat("tts_rate", 1.0f)
        set(v) = prefs.edit { putFloat("tts_rate", v) }
    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(v) = prefs.edit { putFloat("tts_pitch", v) }
    var silenceLength: Long
        get() = prefs.getLong("silence_length", 3500L)
        set(v) = prefs.edit { putLong("silence_length", v) }

    // 2. Server
    var apiUrl: String
        get() = prefs.getString("api_url", BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL
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
        get() = prefs.getBoolean("work_hours_enabled", false)
        set(v) = prefs.edit { putBoolean("work_hours_enabled", v) }
    var workHoursStart: String
        get() = prefs.getString("work_start", "07:00") ?: "07:00"
        set(v) = prefs.edit { putString("work_start", v) }
    var workHoursEnd: String
        get() = prefs.getString("work_end", "19:00") ?: "19:00"
        set(v) = prefs.edit { putString("work_end", v) }
    var defaultTaskPriority: String
        get() = prefs.getString("default_priority", "normal") ?: "normal"
        set(v) = prefs.edit { putString("default_priority", v) }
    var emailSignature: String
        get() = prefs.getString("email_signature", "Marek\nDesignLeaf\n07395 813008\nmarek@designleaf.co.uk\nwww.designleaf.co.uk") ?: ""
        set(v) = prefs.edit { putString("email_signature", v) }
    var activeSignatureId: String
        get() = prefs.getString("active_signature_id", "") ?: ""
        set(v) = prefs.edit { putString("active_signature_id", v) }

    fun getSavedSignatures(): List<SavedSignature> {
        val json = prefs.getString("saved_signatures", null) ?: return listOf(SavedSignature(id = "default", name = "DesignLeaf", content = emailSignature))
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
        val json = prefs.getString("user_profiles", null) ?: return listOf(UserProfile(id = "admin", name = "Marek Sima", role = "admin"))
        return try { gson.fromJson(json, object : TypeToken<List<UserProfile>>() {}.type) } catch (e: Exception) { listOf(UserProfile(id = "admin", name = "Marek Sima", role = "admin")) }
    }
    fun saveUserProfiles(p: List<UserProfile>) = prefs.edit { putString("user_profiles", gson.toJson(p)) }
    fun getActiveProfile(): UserProfile = getUserProfiles().find { it.id == activeUserId } ?: UserProfile(id = "admin", name = "Marek Sima", role = "admin")
    fun hasPermission(p: String): Boolean = getActiveProfile().permissions[p] ?: false
    fun verifyPassword(pwd: String): Boolean { val h = adminPasswordHash; return h.isBlank() || hashPassword(pwd) == h }
    fun setAdminPassword(pwd: String) { adminPasswordHash = if (pwd.isBlank()) "" else hashPassword(pwd) }
    private fun hashPassword(pwd: String): String = MessageDigest.getInstance("SHA-256").digest(pwd.toByteArray()).joinToString("") { "%02x".format(it) }

    // Utility
    fun resetAll() = prefs.edit { clear() }
    fun isWithinWorkHours(): Boolean {
        if (!workHoursEnabled) return true
        return try {
            val now = java.util.Calendar.getInstance(); val cur = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            val sp = workHoursStart.split(":"); val start = sp[0].toInt() * 60 + sp.getOrElse(1) { "0" }.toInt()
            val ep = workHoursEnd.split(":"); val end = ep[0].toInt() * 60 + ep.getOrElse(1) { "0" }.toInt()
            cur in start..end
        } catch (e: Exception) { true }
    }
}
