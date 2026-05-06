package com.example.secretary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class VoiceService : Service() {

    private val TAG = "VoiceService"
    private val CHANNEL_ID = "secretary_voice_silent"
    private val NOTIFICATION_ID = 1001

    var voiceManager: VoiceManager? = null
        private set

    private lateinit var settings: SettingsManager

    private val binder = VoiceBinder()

    inner class VoiceBinder : Binder() {
        fun getService(): VoiceService = this@VoiceService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        settings = SettingsManager(this)
        createNotificationChannel()
        val notification = buildNotification("Sekretářka je připravena")
        startForeground(NOTIFICATION_ID, notification)
    }

    fun initVoiceManager(
        onResult: (String) -> Unit,
        onReady: () -> Unit,
        onRecognizerError: (Int) -> Unit,
        onHotwordDetected: () -> Unit,
        onStatusChange: (String) -> Unit
    ) {
        voiceManager?.destroy()
        voiceManager = VoiceManager(
            context = this,
            settings = settings,
            onResult = onResult,
            onReady = onReady,
            onRecognizerError = onRecognizerError,
            onHotwordDetected = onHotwordDetected,
            onStatusChange = { status -> 
                updateNotification(status)
                onStatusChange(status)
            }
        )
        voiceManager?.startHotwordLoop()
        Log.d(TAG, "VoiceManager initialized, hotword loop started")
    }

    fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        voiceManager?.destroy()
        voiceManager = null
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hlasové ovládání",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Sekretářka naslouchá příkazům"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.deleteNotificationChannel("secretary_voice_channel") // cleanup old channel
        nm?.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sekretářka")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
