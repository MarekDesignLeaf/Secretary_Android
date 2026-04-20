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
    // FIX A2: track permanent TTS init failure so speak() can skip audio but continue state machine
    private var ttsFailedPermanently = false
    // FIX A7: guard against zombie recognizer callbacks after destroy()
    @Volatile private var isDestroyed = false
    private var isSpeaking = false
    private var expectReplyAfterSpeak = false
    private var stayIdleAfterSpeak = false
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
            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun currentRecognitionLanguage(): String {
        val lang = when (Strings.fromCode(settings.getCurrentAppLanguage())) {
            Strings.Lang.CS -> "cs-CZ"
            Strings.Lang.PL -> "pl-PL"
            Strings.Lang.EN -> "en-GB"
        }
        if (settings.recognitionLanguage != lang) {
            settings.recognitionLanguage = lang
        }
        return lang
    }

    private fun applyTtsLanguage(): Boolean {
        val activeRecognitionLanguage = currentRecognitionLanguage()
        val locale = Locale.forLanguageTag(activeRecognitionLanguage)
        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        Log.d(TAG, "TTS setLanguage($activeRecognitionLanguage) result=$result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS language $activeRecognitionLanguage not available, falling back to en-GB")
            val fallbackResult = tts?.setLanguage(Locale.forLanguageTag("en-GB"))
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS fallback en-GB also unavailable")
                return false
            }
        } else {
            Log.d(TAG, "TTS language set to $activeRecognitionLanguage OK")
        }
        return true
    }

    fun refreshLanguage() {
        handler.post {
            if (isDestroyed) return@post
            if (isTtsReady && !ttsFailedPermanently) {
                applyTtsLanguage()
            }
            val previousMode = mode
            if (!isSpeaking && previousMode != ListenMode.IDLE) {
                stop()
                handler.postDelayed({
                    if (isDestroyed) return@postDelayed
                    when (previousMode) {
                        ListenMode.HOTWORD -> startHotwordLoop()
                        ListenMode.COMMAND -> startListening()
                        ListenMode.IDLE -> Unit
                    }
                }, 700)
            }
        }
    }

    private fun spokenTokenCorrections(): Map<String, String> = mapOf(
        "hey" to "hej",
        "hei" to "hej",
        "okey" to "ok",
        "tree" to "tri",
        "free" to "tri",
        "three" to "tri",
        "pete" to "pet",
        "fit" to "pet",
        "know" to "no"
    )

    private fun normalizeRecognizedText(text: String): String {
        val corrected = normalize(text)
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> spokenTokenCorrections()[token] ?: token }
        return corrected.trim()
    }

    private fun recognitionCandidates(results: Bundle?): List<String> {
        return results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.map { it.orEmpty() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
    }

    private fun getHotwords(): List<String> {
        val custom = settings.activationWord.trim()
        return listOf(custom).filter { it.isNotBlank() }.distinct()
    }

    private fun matchesHotword(text: String): Boolean {
        val normText = normalizeRecognizedText(text)
        if (normText.isBlank()) return false
        return getHotwords().any {
            val normHw = normalizeRecognizedText(it)
            normText == normHw || normText.startsWith("$normHw ") || normText.contains(" $normHw ")
        }
    }

    fun startHotwordLoop() {
        if (!settings.hotwordEnabled) {
            mode = ListenMode.IDLE
            onStatusChange(Strings.hotwordDisabledStatus)
            return
        }
        if (!settings.isWithinWorkHours()) {
            mode = ListenMode.IDLE
            onStatusChange(Strings.outsideWorkHoursStatus)
            return
        }
        if (isSpeaking) return
        mode = ListenMode.HOTWORD
        consecutiveErrors = 0
        hotwordMatchedInSession = false
        onStatusChange(Strings.waitingForCommand)
        ensureRecognizerAndListen()
    }

    fun startListening() {
        if (isSpeaking) return
        cancelRecognizer()
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

    fun speak(text: String, expectReply: Boolean = false, stayIdle: Boolean = false) {
        expectReplyAfterSpeak = expectReply
        stayIdleAfterSpeak = stayIdle
        // FIX A2: if TTS failed permanently or is not ready, skip audio and continue state machine
        if (!isTtsReady || ttsFailedPermanently) {
            handler.post {
                when {
                    stayIdleAfterSpeak -> stop()
                    expectReply -> startListening()
                    else -> startHotwordLoop()
                }
            }
            return
        }
        isSpeaking = true
        cancelRecognizer()
        // Update TTS locale before every utterance so language switches apply immediately.
        applyTtsLanguage()
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
        // FIX A7: set flag first so any pending handler posts abort immediately
        isDestroyed = true
        stop()
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }

    private fun ensureRecognizerAndListen() {
        handler.post {
            // FIX A7: abort if VoiceManager was destroyed between posting and executing
            if (isDestroyed) return@post
            try {
                Log.d(TAG, "ensureRecognizerAndListen: mode=$mode isSpeaking=$isSpeaking")
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(TAG, "Speech recognition NOT available!")
                    onStatusChange(Strings.speechRecognitionUnavailable)
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
            val lang = currentRecognitionLanguage()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, mode == ListenMode.HOTWORD)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
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
            // FIX A7: abort if destroyed
            if (isDestroyed) return@postDelayed
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
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                try { recognizer?.cancel() } catch (_: Exception) { }
                try { recognizer?.destroy() } catch (_: Exception) { }
                recognizer = null
            }
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                recognizer?.destroy()
                recognizer = null
                consecutiveErrors = 0
            }
            if (mode == ListenMode.IDLE) return
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> 200L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> 1500L
                else -> 1000L
            }
            scheduleRestart(delay)
        }
        override fun onResults(results: Bundle?) {
            isRecognizerActive = false
            if (mode == ListenMode.HOTWORD && hotwordMatchedInSession) return
            val candidates = recognitionCandidates(results)
            when (mode) {
                ListenMode.HOTWORD -> if (candidates.any(::matchesHotword)) triggerHotword() else scheduleRestart(200)
                ListenMode.COMMAND -> {
                    val text = candidates.firstOrNull()?.let(::normalizeRecognizedText).orEmpty()
                    if (text.isNotBlank()) onResult(text) else startHotwordLoop()
                }
                else -> {}
            }
        }
        override fun onPartialResults(results: Bundle?) {
            if (mode != ListenMode.HOTWORD || hotwordMatchedInSession) return
            val candidates = recognitionCandidates(results)
            if (candidates.any(::matchesHotword)) {
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
                if (!applyTtsLanguage()) {
                    isTtsReady = false
                    ttsFailedPermanently = true
                    onStatusChange(Strings.ttsOutputUnavailable)
                    return@TextToSpeech
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { isSpeaking = true; Log.d(TAG, "TTS onStart: $id") }
                    override fun onDone(id: String?) {
                        isSpeaking = false
                        Log.d(TAG, "TTS onDone: $id")
                        handler.post {
                            if (isDestroyed) return@post  // FIX A7: guard in callback
                            when {
                                stayIdleAfterSpeak -> stop()
                                expectReplyAfterSpeak -> startListening()
                                else -> startHotwordLoop()
                            }
                        }
                    }
                    override fun onError(id: String?) {
                        isSpeaking = false
                        Log.e(TAG, "TTS onError: $id")
                        handler.post {
                            if (isDestroyed) return@post  // FIX A7: guard in callback
                            if (stayIdleAfterSpeak) stop() else startHotwordLoop()
                        }
                    }
                })
                isTtsReady = true
                ttsFailedPermanently = false
                // Force audio to speaker with USAGE_MEDIA
                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                    .build()
                tts?.setAudioAttributes(audioAttrs)
                Log.d(TAG, "TTS ready with USAGE_MEDIA audio attributes")
            } else {
                // FIX A2: TTS engine failed to initialise – mark permanently and notify UI
                Log.e(TAG, "TTS initialisation failed with status $status")
                isTtsReady = false
                ttsFailedPermanently = true
                onStatusChange(Strings.ttsInitFailed(status))
            }
        }, "com.google.android.tts")  // explicit Google TTS
    }
}
