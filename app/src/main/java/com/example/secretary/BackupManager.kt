package com.example.secretary

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * BackupManager — creates permission-based pre-uninstall backups.
 *
 * Backup logic:
 *  - Admin / Owner  → full scope: all user credentials + DB reference
 *  - Other roles    → personal scope: own credentials only (no DB reference)
 *
 * Local storage:
 *  - <files-dir>/install_data/backup_<timestamp>.json
 *
 * The backup JSON is stored locally on every call. It is also uploaded to the
 * server if [storageLocation] is "server" or "both".
 */
class BackupManager(
    private val context: Context,
    private val api: SecretaryApi,
    private val settingsManager: SettingsManager,
) {

    companion object {
        private const val TAG = "BackupManager"
        const val INSTALL_DATA_DIR = "install_data"
        const val BACKUP_FILE_PREFIX = "backup_"
    }

    /** Unique, stable device ID derived from Android ID. */
    val deviceId: String by lazy {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        // Hash it so we're not storing raw Android ID on the server
        sha256("secretary:$androidId")
    }

    /**
     * Create a backup and store it locally (and optionally on the server).
     *
     * @param storageLocation "local" | "server" | "both"
     * @return the [BackupResult] describing what was saved
     */
    suspend fun createBackup(
        storageLocation: String = "both"
    ): BackupResult = withContext(Dispatchers.IO) {
        val token = settingsManager.accessToken
            ?: return@withContext BackupResult(
                success = false,
                errorMessage = "Not authenticated — cannot create backup"
            )

        val authHeader = "Bearer $token"

        val body = mapOf(
            "storage_location" to storageLocation,
            "device_id" to deviceId
        )

        try {
            val response = api.createBackup(authHeader, body)
            if (!response.isSuccessful) {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Log.e(TAG, "Backup API call failed: $err")
                return@withContext BackupResult(success = false, errorMessage = err)
            }

            val payload = response.body()
                ?: return@withContext BackupResult(success = false, errorMessage = "Empty response")

            // Persist locally in install_data/
            val localFile = saveLocally(payload)
            Log.i(TAG, "Backup saved locally: ${localFile.absolutePath}")

            val restoreToken = payload["restore_token"] as? String
            val backupId = payload["backup_id"] as? String ?: "unknown"
            val scope = payload["backup_scope"] as? String ?: "personal"

            BackupResult(
                success = true,
                backupId = backupId,
                backupScope = scope,
                localFilePath = localFile.absolutePath,
                restoreToken = restoreToken,
                includesDbReference = (payload["db_reference"] as? String) != null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed with exception", e)
            BackupResult(success = false, errorMessage = e.message ?: "Unknown error")
        }
    }

    /**
     * Register the device's fingerprint hash with the server.
     *
     * The fingerprint hash should be computed by Android BiometricPrompt logic
     * in the calling code and passed as [biometricHash].  This function only
     * sends it to the server; it does not prompt the user.
     */
    suspend fun registerBiometric(
        biometricHash: String,
        label: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager.accessToken ?: return@withContext false
        try {
            val body = buildMap<String, Any?> {
                put("device_id", deviceId)
                put("biometric_hash", biometricHash)
                if (label != null) put("label", label)
            }
            val res = api.registerBiometric("Bearer $token", body)
            res.isSuccessful.also {
                if (it) Log.i(TAG, "Biometric registered for device $deviceId")
                else Log.w(TAG, "Biometric register failed: HTTP ${res.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerBiometric exception", e)
            false
        }
    }

    /**
     * Return all locally-stored backup files, newest first.
     */
    fun listLocalBackups(): List<File> {
        val dir = installDataDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.name.startsWith(BACKUP_FILE_PREFIX) && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Read a locally-stored backup file and return it as a [JSONObject].
     */
    fun readLocalBackup(file: File): JSONObject? {
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup file ${file.name}", e)
            null
        }
    }

    /**
     * Delete all locally-stored backup files (called after successful restore
     * or when the user explicitly clears backup data).
     */
    fun clearLocalBackups() {
        listLocalBackups().forEach { it.delete() }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun installDataDir(): File {
        val dir = File(context.filesDir, INSTALL_DATA_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Suppress("UNCHECKED_CAST")
    private fun saveLocally(payload: Map<String, Any?>): File {
        val dir = installDataDir()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${BACKUP_FILE_PREFIX}${timestamp}.json")

        // Build a clean JSON representation
        val json = JSONObject()
        payload.forEach { (k, v) ->
            when (v) {
                null -> json.put(k, JSONObject.NULL)
                is List<*> -> {
                    val arr = org.json.JSONArray()
                    (v as List<Any?>).forEach { item ->
                        when (item) {
                            is Map<*, *> -> arr.put(JSONObject(item as Map<String, Any?>))
                            else -> arr.put(item)
                        }
                    }
                    json.put(k, arr)
                }
                is Map<*, *> -> json.put(k, JSONObject(v as Map<String, Any?>))
                else -> json.put(k, v)
            }
        }

        file.writeText(json.toString(2))
        return file
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result of a backup operation.
 */
data class BackupResult(
    val success: Boolean,
    val backupId: String? = null,
    val backupScope: String? = null,       // "full" | "personal"
    val localFilePath: String? = null,
    val restoreToken: String? = null,       // server download token (null for local-only)
    val includesDbReference: Boolean = false,
    val errorMessage: String? = null,
)
