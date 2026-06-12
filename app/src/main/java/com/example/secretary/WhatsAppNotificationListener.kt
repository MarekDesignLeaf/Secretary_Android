package com.example.secretary

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reads INCOMING WhatsApp message notifications on this phone and forwards them
 * to the backend as a communication (type=whatsapp, direction=in, read=false) —
 * the same shape the Meta webhook produces, so the Communications screen and the
 * voice "přečti zprávu" command work for both sources.
 *
 * No Meta account needed; uses the user's own WhatsApp. Limitation: only messages
 * that arrive as notifications while access is granted (no history).
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WaNotifListener"
        private val WHATSAPP_PKGS = setOf("com.whatsapp", "com.whatsapp.w4b")

        /** Group-summary / non-message titles we must skip. */
        private val SKIP_TITLE_PATTERNS = listOf(
            Regex("""^\d+\s+(new\s+)?messages?$""", RegexOption.IGNORE_CASE),
            Regex("""^\d+\s+chats?$""", RegexOption.IGNORE_CASE),
            Regex("""\d+\s+(nov[áé]\s+)?zpráv""", RegexOption.IGNORE_CASE),
            Regex("""WhatsApp""", RegexOption.IGNORE_CASE),
        )

        fun isAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { it.contains(context.packageName) }
        }

        fun openAccessSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // De-dupe identical notifications fired repeatedly (WhatsApp re-posts on update).
    private val recentKeys = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in WHATSAPP_PKGS) return
        val extras = sbn.notification?.extras ?: return

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        // Multi-line notifications expose the last line in EXTRA_TEXT; that's fine.
        if (sender.isBlank() || text.isBlank()) return
        if (SKIP_TITLE_PATTERNS.any { it.containsMatchIn(sender) }) return
        // "You replied", call notifications, etc. are not inbound chat text.
        if (text.startsWith("You ", ignoreCase = true) || text.startsWith("Ty ", ignoreCase = true)) return

        val key = "$sender|$text"
        val now = System.currentTimeMillis()
        val last = recentKeys[key]
        if (last != null && now - last < 60_000) return
        recentKeys[key] = now

        forwardToBackend(sender, text)
    }

    private fun forwardToBackend(sender: String, text: String) {
        val settings = SettingsManager(applicationContext)
        val token = settings.accessToken
        if (token.isNullOrBlank()) {
            Log.d(TAG, "Not logged in — inbound WhatsApp not forwarded")
            return
        }
        val root = settings.apiUrl.let { if (it.endsWith("/")) it else "$it/" }
        val base = if (root.endsWith("api/v1/")) root else root + "api/v1/"
        val body = JSONObject().apply {
            put("message_summary", "WhatsApp od $sender")
            put("type", "whatsapp")
            put("direction", "in")
            put("contact", sender)
            put("note", text)
            put("read", false)
            put("source", "notification")
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(base + "crm/communications")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) Log.w(TAG, "Forward failed HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Forward error: ${e.message}")
        }
    }
}
