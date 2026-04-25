package com.example.secretary

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
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
    private var pendingSpeechText: String? = null
    private var pendingSpeechExpectReply = false
    private var pendingSpeechStayIdle = false
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "VoiceManager"
    private var wakeWordEngine: WakeWordEngine? = null

    enum class ListenMode { IDLE, HOTWORD, COMMAND }
    private var mode = ListenMode.IDLE
    private var isRecognizerActive = false
    private var lastRecognizerStartAt = 0L
    private var consecutiveErrors = 0
    private var hotwordMatchedInSession = false
    private val MAX_CONSECUTIVE_ERRORS = 5
    private var commandNoMatchRetries = 0
    private val MAX_COMMAND_NO_MATCH_RETRIES = 0

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
        "true" to "tri",
        "try" to "tri",
        "three" to "tri",
        "pete" to "pet",
        "pat" to "pet",
        "pad" to "pet",
        "fit" to "pet",
        "nulla" to "nula",
        "zero" to "nula",
        "know" to "no",
        "nah" to "ne",
        "nee" to "ne"
    )

    private fun normalizeRecognizedText(text: String): String {
        val corrected = normalize(text)
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> spokenTokenCorrections()[token] ?: token }
        return applyVoiceAliases(corrected).trim()
    }

    private fun applyVoiceAliases(text: String): String {
        var corrected = text
        settings.getVoiceAliases()
            .sortedByDescending { normalize(it.alias).length }
            .forEach { alias ->
                val aliasNorm = normalize(alias.alias)
                val targetNorm = normalize(alias.target)
                if (aliasNorm.length < 2 || targetNorm.length < 2 || aliasNorm == targetNorm) return@forEach
                corrected = corrected.replace(Regex("\\b${Regex.escape(aliasNorm)}\\b"), targetNorm)
            }
        return corrected
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
        return listOf(settings.activationWord.trim())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun shouldUseOfflineWakeWord(): Boolean = true

    private fun matchesHotword(text: String): Boolean {
        val normalizedText = normalize(text)
        if (normalizedText.isBlank()) return false
        val base = normalize(settings.activationWord.trim())
        if (base.isBlank()) return false
        val phrases = linkedSetOf(base)
        if (base != "hej" && base != "hey") {
            phrases += "hej $base"
            phrases += "hey $base"
        }
        return phrases.any { hotword ->
            normalizedText == hotword ||
                normalizedText.startsWith("$hotword ") ||
                normalizedText.contains(" $hotword ")
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
        if (shouldUseOfflineWakeWord()) {
            startOfflineHotwordLoop()
        } else {
            startRecognizerHotwordLoop()
        }
    }

    fun startListening() {
        if (isSpeaking) return
        wakeWordEngine?.stop()
        cancelRecognizer()
        mode = ListenMode.COMMAND
        consecutiveErrors = 0
        commandNoMatchRetries = 0
        onStatusChange("${Strings.listening}...")
        handler.postDelayed({ ensureRecognizerAndListen() }, 400)
    }

    fun stop() {
        mode = ListenMode.IDLE
        wakeWordEngine?.stop()
        cancelRecognizer()
        handler.removeCallbacksAndMessages(null)
    }

    fun speak(text: String, expectReply: Boolean = false, stayIdle: Boolean = false) {
        expectReplyAfterSpeak = expectReply
        stayIdleAfterSpeak = stayIdle
        if (!isTtsReady && !ttsFailedPermanently) {
            pendingSpeechText = text
            pendingSpeechExpectReply = expectReply
            pendingSpeechStayIdle = stayIdle
            Log.w(TAG, "TTS not ready; queued speech '${text.take(50)}...'")
            handler.postDelayed({
                if (!isTtsReady && !ttsFailedPermanently && pendingSpeechText == text) {
                    Log.w(TAG, "TTS still not ready; reinitialising")
                    tts?.shutdown()
                    setupTts()
                }
            }, 800)
            return
        }
        // FIX A2: if TTS failed permanently, skip audio but continue the state machine.
        if (ttsFailedPermanently) {
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
        wakeWordEngine?.stop()
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
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "reply") ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak returned ERROR; reinitialising")
            isSpeaking = false
            isTtsReady = false
            pendingSpeechText = text
            pendingSpeechExpectReply = expectReply
            pendingSpeechStayIdle = stayIdle
            tts?.shutdown()
            setupTts()
        }
    }

    fun destroy() {
        // FIX A7: set flag first so any pending handler posts abort immediately
        isDestroyed = true
        stop()
        wakeWordEngine?.shutdown()
        wakeWordEngine = null
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }

    private fun ensureWakeWordEngine(): WakeWordEngine {
        wakeWordEngine?.let { return it }
        val engine = WakeWordEngine(
            context = context,
            onStatus = { status ->
                if (mode == ListenMode.HOTWORD && !isSpeaking && !isDestroyed) {
                    onStatusChange(status)
                }
            },
            onError = { message, throwable ->
                Log.e(TAG, message, throwable)
                if (mode == ListenMode.HOTWORD && !isSpeaking && !isDestroyed) {
                    onStatusChange("Offline wake-word není dostupný. Kontroluji model...")
                    handler.postDelayed({
                        if (mode == ListenMode.HOTWORD && !isSpeaking && !isDestroyed) {
                            startOfflineHotwordLoop()
                        }
                    }, 30_000L)
                }
            }
        )
        wakeWordEngine = engine
        return engine
    }

    private fun startOfflineHotwordLoop() {
        if (mode != ListenMode.HOTWORD || isSpeaking || isDestroyed) return
        cancelRecognizer()
        hotwordMatchedInSession = false
        val hotwords = getHotwords()
        if (hotwords.isEmpty()) {
            onStatusChange(Strings.hotwordDisabledStatus)
            return
        }
        Log.d(TAG, "Starting offline hotword loop with: ${hotwords.joinToString(", ")}")
        ensureWakeWordEngine().start(hotwords, currentRecognitionLanguage()) {
            if (mode == ListenMode.HOTWORD && !isSpeaking && !isDestroyed) {
                hotwordMatchedInSession = true
                triggerHotword()
            }
        }
    }

    private fun startRecognizerHotwordLoop() {
        if (mode != ListenMode.HOTWORD || isSpeaking || isDestroyed) return
        wakeWordEngine?.stop()
        cancelRecognizer()
        hotwordMatchedInSession = false
        ensureRecognizerAndListen()
    }

    private fun ensureRecognizerAndListen() {
        handler.post {
            // FIX A7: abort if VoiceManager was destroyed between posting and executing
            if (isDestroyed) return@post
            if (mode != ListenMode.COMMAND && mode != ListenMode.HOTWORD) return@post
            try {
                Log.d(TAG, "ensureRecognizerAndListen: mode=$mode isSpeaking=$isSpeaking")
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(TAG, "Speech recognition NOT available!")
                    onStatusChange(Strings.speechRecognitionUnavailable)
                    return@post
                }
                val activeForMs = SystemClock.elapsedRealtime() - lastRecognizerStartAt
                if (isRecognizerActive && activeForMs < 8000L) {
                    Log.d(TAG, "Recognizer already active; skip duplicate startListening")
                    return@post
                } else if (isRecognizerActive) {
                    Log.w(TAG, "Recognizer looked stale for ${activeForMs}ms; recreating")
                    try { recognizer?.cancel() } catch (_: Exception) { }
                    try { recognizer?.destroy() } catch (_: Exception) { }
                    recognizer = null
                    isRecognizerActive = false
                }
                if (recognizer == null) {
                    Log.d(TAG, "Creating new SpeechRecognizer")
                    val preferredService = resolvePreferredRecognitionService()
                    if (preferredService != null) {
                        Log.d(TAG, "Using recognition service: ${preferredService.flattenToShortString()}")
                        recognizer = SpeechRecognizer.createSpeechRecognizer(context, preferredService)
                    } else {
                        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    }
                    recognizer?.setRecognitionListener(createListener())
                }
                hotwordMatchedInSession = false
                Log.d(TAG, "Calling startListening on recognizer")
                recognizer?.startListening(createRecognizerIntent())
                isRecognizerActive = true
                lastRecognizerStartAt = SystemClock.elapsedRealtime()
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
            putExtra("android.speech.extra.SUPPRESS_BEEP", true)
            // Do NOT use EXTRA_PREFER_OFFLINE — breaks recognizer if no offline model

            if (mode == ListenMode.HOTWORD) {
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
                )
            } else {
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra("android.speech.extra.DICTATION_MODE", false)
                val silence = settings.silenceLength
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence + 1000L)
            }
        }
    }

    private fun resolvePreferredRecognitionService(): ComponentName? {
        return try {
            val pm = context.packageManager
            val available = pm.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                PackageManager.MATCH_DEFAULT_ONLY
            ).mapNotNull { info ->
                val si = info.serviceInfo ?: return@mapNotNull null
                ComponentName(si.packageName, si.name)
            }
            if (available.isEmpty()) return null

            val preferredPackages = listOf(
                "com.google.android.tts",
                "com.google.android.as"
            )
            preferredPackages.forEach { pkg ->
                available.firstOrNull { it.packageName == pkg }?.let { return it }
            }
            available.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve recognition service", e)
            null
        }
    }

    private fun cancelRecognizer() {
        isRecognizerActive = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) { }
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.postDelayed({
            // FIX A7: abort if destroyed
            if (isDestroyed) return@postDelayed
            if (isSpeaking) return@postDelayed
            when (mode) {
                ListenMode.COMMAND -> ensureRecognizerAndListen()
                ListenMode.HOTWORD -> {
                    if (shouldUseOfflineWakeWord()) startOfflineHotwordLoop() else startRecognizerHotwordLoop()
                }
                ListenMode.IDLE -> Unit
            }
        }, delayMs)
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isRecognizerActive = true
            consecutiveErrors = 0
            if (mode == ListenMode.COMMAND) {
                onReady()
            }
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isRecognizerActive = false }
        override fun onError(error: Int) {
            isRecognizerActive = false
            consecutiveErrors++
            Log.w(TAG, "Recognizer onError code=$error mode=$mode consecutive=$consecutiveErrors")
            if (mode == ListenMode.HOTWORD) {
                if (shouldUseOfflineWakeWord()) {
                    cancelRecognizer()
                    startOfflineHotwordLoop()
                } else {
                    scheduleRestart(1200L)
                }
                return
            }
            if (mode == ListenMode.COMMAND) onRecognizerError(error)
            if (mode == ListenMode.COMMAND &&
                (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                if (commandNoMatchRetries >= MAX_COMMAND_NO_MATCH_RETRIES) {
                    Log.d(TAG, "No command captured; returning to hotword mode")
                    startHotwordLoop()
                    return
                }
                commandNoMatchRetries++
            } else if (mode == ListenMode.COMMAND) {
                commandNoMatchRetries = 0
            }
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
            Log.d(TAG, "Scheduling recognizer restart in ${delay}ms")
            scheduleRestart(delay)
        }
        override fun onResults(results: Bundle?) {
            isRecognizerActive = false
            val candidates = recognitionCandidates(results)
            when (mode) {
                ListenMode.HOTWORD -> {
                    if (hotwordMatchedInSession) return
                    if (candidates.any(::matchesHotword)) {
                        hotwordMatchedInSession = true
                        triggerHotword()
                    } else if (!isSpeaking) {
                        scheduleRestart(300L)
                    }
                }
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
        override fun onSegmentResults(segmentResults: Bundle) {
            if (mode != ListenMode.HOTWORD || hotwordMatchedInSession) return
            val candidates = recognitionCandidates(segmentResults)
            if (candidates.any(::matchesHotword)) {
                hotwordMatchedInSession = true
                triggerHotword()
            }
        }
        override fun onEndOfSegmentedSession() {
            isRecognizerActive = false
            if (mode == ListenMode.HOTWORD && !hotwordMatchedInSession && !isSpeaking) {
                scheduleRestart(300L)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun triggerHotword() {
        wakeWordEngine?.stop()
        cancelRecognizer()
        onHotwordDetected()
        speak(Strings.listening, expectReply = true)
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
                pendingSpeechText?.let { pending ->
                    val pendingExpectReply = pendingSpeechExpectReply
                    val pendingStayIdle = pendingSpeechStayIdle
                    pendingSpeechText = null
                    handler.post { speak(pending, pendingExpectReply, pendingStayIdle) }
                }
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
