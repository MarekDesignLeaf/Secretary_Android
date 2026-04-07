package com.example.secretary

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.text.Normalizer
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val settings: SettingsManager,
    private val onResult: (String) -> Unit,
    private val onReady: () -> Unit = {},
    private val onRecognizerError: (Int) -> Unit = {},
    private val onHotwordDetected: () -> Unit = {},
    private val onStatusChange: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isSpeaking = false
    private var expectReplyAfterSpeak = false
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "VoiceManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var wasMuted = false

    enum class ListenMode { IDLE, HOTWORD, COMMAND }
    private var mode = ListenMode.IDLE
    private var isRecognizerActive = false
    private var consecutiveErrors = 0
    private var hotwordMatchedInSession = false
    private val MAX_CONSECUTIVE_ERRORS = 5

    init {
        setupTts()
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    private fun getHotwords(): List<String> {
        val custom = settings.activationWord.lowercase().trim()
        // Custom word replaces defaults; if empty, use built-in defaults
        return if (custom.isNotBlank()) listOf(custom) else listOf("hej kundo", "kundo")
    }

    private fun matchesHotword(text: String): Boolean {
        val normText = normalize(text)
        return getHotwords().any { normalize(it).let { normHw -> normText.contains(normHw) } }
    }

    fun startHotwordLoop() {
        if (!settings.hotwordEnabled || isSpeaking || !settings.isWithinWorkHours()) return
        mode = ListenMode.HOTWORD
        consecutiveErrors = 0
        hotwordMatchedInSession = false
        onStatusChange(Strings.waitingForCommand)
        ensureRecognizerAndListen()
    }

    fun startListening() {
        if (isSpeaking) return
        cancelRecognizer()
        // Destroy and recreate recognizer so any language change takes effect
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        mode = ListenMode.COMMAND
        consecutiveErrors = 0
        onStatusChange("${Strings.listening}...")
        handler.postDelayed({ ensureRecognizerAndListen() }, 400)
    }

    fun stop() {
        mode = ListenMode.IDLE
        cancelRecognizer()
        handler.removeCallbacksAndMessages(null)
    }

    fun speak(text: String, expectReply: Boolean = false) {
        expectReplyAfterSpeak = expectReply
        if (!isTtsReady) {
            handler.post {
                if (expectReply) startListening()
                else startHotwordLoop()
            }
            return
        }
        isSpeaking = true
        cancelRecognizer()
        // Update TTS locale — try current language, fall back to cs-CZ then device default
        val locale = Locale.forLanguageTag(settings.recognitionLanguage)
        val langResult = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS: ${settings.recognitionLanguage} not available, trying cs-CZ")
            val csResult = tts?.setLanguage(Locale.forLanguageTag("cs-CZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (csResult == TextToSpeech.LANG_MISSING_DATA || csResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
        }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "reply")
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }
        tts?.setSpeechRate(settings.ttsRate)
        tts?.setPitch(settings.ttsPitch)
        Log.d(TAG, "TTS speak: '${text.take(50)}...' rate=${settings.ttsRate} pitch=${settings.ttsPitch}")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "reply")
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }

    private fun ensureRecognizerAndListen() {
        handler.post {
            try {
                Log.d(TAG, "ensureRecognizerAndListen: mode=$mode isSpeaking=$isSpeaking")
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(TAG, "Speech recognition NOT available!")
                    onStatusChange("Speech recognition not available")
                    return@post
                }
                if (recognizer == null) {
                    Log.d(TAG, "Creating new SpeechRecognizer")
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer?.setRecognitionListener(createListener())
                }
                hotwordMatchedInSession = false
                // Don't mute in HOTWORD mode — Samsung recognizer may fail silently when muted
                if (mode == ListenMode.COMMAND) muteBeep()
                Log.d(TAG, "Calling startListening on recognizer")
                recognizer?.startListening(createRecognizerIntent())
                isRecognizerActive = true
            } catch (e: Exception) {
                isRecognizerActive = false
                scheduleRestart(1500)
            }
        }
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val lang = settings.recognitionLanguage
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, mode == ListenMode.HOTWORD)
            // Do NOT use EXTRA_PREFER_OFFLINE — breaks recognizer if no offline model
            
            val silence = settings.silenceLength
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", silence)
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", silence + 1000L)
        }
    }

    private fun cancelRecognizer() {
        isRecognizerActive = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) { }
        // Only unmute if we're NOT in hotword mode (hotword stays silent)
        if (mode != ListenMode.HOTWORD) unmuteBeep()
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.postDelayed({
            if (mode != ListenMode.IDLE && !isSpeaking) ensureRecognizerAndListen()
        }, delayMs)
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isRecognizerActive = true
            consecutiveErrors = 0
            // Only unmute in COMMAND mode (user speaking command), stay silent in HOTWORD mode
            if (mode == ListenMode.COMMAND) {
                handler.postDelayed({ unmuteBeep() }, 500)
                onReady()
            }
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isRecognizerActive = false; unmuteBeep() }
        override fun onError(error: Int) {
            isRecognizerActive = false
            consecutiveErrors++
            if (mode == ListenMode.COMMAND) onRecognizerError(error)
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                recognizer?.destroy()
                recognizer = null
                consecutiveErrors = 0
            }
            if (mode == ListenMode.IDLE) return
            val delay = if (error == SpeechRecognizer.ERROR_NO_MATCH) 200L else 1000L
            scheduleRestart(delay)
        }
        override fun onResults(results: Bundle?) {
            isRecognizerActive = false
            if (mode == ListenMode.HOTWORD && hotwordMatchedInSession) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            when (mode) {
                ListenMode.HOTWORD -> if (matchesHotword(text)) triggerHotword() else scheduleRestart(200)
                ListenMode.COMMAND -> if (text.isNotBlank()) onResult(text) else startHotwordLoop()
                else -> {}
            }
        }
        override fun onPartialResults(results: Bundle?) {
            if (mode != ListenMode.HOTWORD || hotwordMatchedInSession) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            if (matchesHotword(text)) {
                hotwordMatchedInSession = true
                triggerHotword()
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun triggerHotword() {
        cancelRecognizer()
        onHotwordDetected()
        speak(Strings.listening, expectReply = true)
    }

    private var originalMusicVol = 0
    private var originalNotifVol = 0
    private var originalSystemVol = 0

    private fun muteBeep() {
        try {
            originalNotifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            originalSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        } catch (e: Exception) { Log.e(TAG, "Mute error", e) }
    }

    private fun unmuteBeep() {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotifVol, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVol, 0)
        } catch (e: Exception) { Log.e(TAG, "Unmute error", e) }
    }

    private fun setupTts() {
        tts = TextToSpeech(context.applicationContext, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.forLanguageTag(settings.recognitionLanguage)
                val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                Log.d(TAG, "TTS setLanguage(${settings.recognitionLanguage}) result=$result")
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS language ${settings.recognitionLanguage} not available (result=$result), falling back to en-GB")
                    tts?.setLanguage(Locale.forLanguageTag("en-GB"))
                } else {
                    Log.d(TAG, "TTS language set to ${settings.recognitionLanguage} OK")
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { isSpeaking = true; Log.d(TAG, "TTS onStart: $id") }
                    override fun onDone(id: String?) {
                        isSpeaking = false
                        Log.d(TAG, "TTS onDone: $id")
                        handler.post { if (expectReplyAfterSpeak) startListening() else startHotwordLoop() }
                    }
                    override fun onError(id: String?) {
                        isSpeaking = false
                        Log.e(TAG, "TTS onError: $id expectReply=$expectReplyAfterSpeak")
                        handler.post { if (expectReplyAfterSpeak) startListening() else startHotwordLoop() }
                    }
                })
                isTtsReady = true
                // Force audio to speaker with USAGE_MEDIA
                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                    .build()
                tts?.setAudioAttributes(audioAttrs)
                Log.d(TAG, "TTS ready with USAGE_MEDIA audio attributes")
            }
        }, "com.google.android.tts")  // explicit Google TTS
    }
}
