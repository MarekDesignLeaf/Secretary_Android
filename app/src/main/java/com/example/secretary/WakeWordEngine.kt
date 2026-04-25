package com.example.secretary

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.max

class WakeWordEngine(
    context: Context,
    private val onStatus: (String) -> Unit = {},
    private val onError: (String, Throwable?) -> Unit = { _, _ -> }
) {
    private data class ModelSpec(
        val name: String,
        val url: String
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient()
    private val tag = "WakeWordEngine"
    private val sampleRate = 16_000f
    private val modelBaseDir = File(appContext.filesDir, "wakeword_model")
    private val fallbackEnglishModel = ModelSpec(
        name = "vosk-model-small-en-us-0.15",
        url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    )

    @Volatile private var running = false
    private var model: Model? = null
    private var loadedModelName: String? = null
    private var runJob: Job? = null
    @Volatile private var currentAudioRecord: AudioRecord? = null
    fun start(hotwords: List<String>, recognitionLanguage: String, onDetected: () -> Unit) {
        if (running) return
        val targetHotwords = buildHotwordVariants(hotwords)
        if (targetHotwords.isEmpty()) return
        val requestedModel = resolveModelSpec(recognitionLanguage)
        Log.d(tag, "Wake-word start request: model=${requestedModel.name}, hotwords=${targetHotwords.joinToString(", ")}")
        running = true
        runJob = scope.launch {
            try {
                val primaryResult = runCatching {
                    val modelDir = ensureModelDirectory(requestedModel)
                    val activeModel = loadModel(requestedModel, modelDir)
                    listenLoop(activeModel, targetHotwords, onDetected)
                }
                if (primaryResult.isFailure) {
                    if (requestedModel.name != fallbackEnglishModel.name) {
                        Log.w(tag, "Failed to initialize ${requestedModel.name}, falling back to ${fallbackEnglishModel.name}", primaryResult.exceptionOrNull())
                        postStatus("Offline model ${requestedModel.name} není dostupný, přepínám na en-us...")
                        val fallbackDir = ensureModelDirectory(fallbackEnglishModel)
                        val fallbackModel = loadModel(fallbackEnglishModel, fallbackDir)
                        listenLoop(fallbackModel, targetHotwords, onDetected)
                    } else {
                        throw primaryResult.exceptionOrNull() ?: IllegalStateException("Unknown wake-word initialization failure")
                    }
                }
            } catch (t: Throwable) {
                postError("Wake-word engine failed", t)
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try {
            currentAudioRecord?.stop()
        } catch (_: Throwable) {
        }
        runJob?.cancel()
        runJob = null
    }

    fun shutdown() {
        stop()
        try {
            model?.close()
        } catch (_: Throwable) {
        }
        model = null
        loadedModelName = null
        scope.cancel()
    }

    private fun loadModel(spec: ModelSpec, modelDir: File): Model {
        if (loadedModelName == spec.name) {
            model?.let { return it }
        }
        try {
            model?.close()
        } catch (_: Throwable) {
        }
        val loaded = Model(modelDir.absolutePath)
        model = loaded
        loadedModelName = spec.name
        return loaded
    }

    private fun listenLoop(activeModel: Model, hotwords: Set<String>, onDetected: () -> Unit) {
        // Use full-vocabulary recognition instead of grammar mode.
        // Grammar mode can drop custom wake words that are not in model vocabulary.
        val recognizer = Recognizer(activeModel, sampleRate)
        val (audioRecord, source) = createAudioRecord()
        Log.d(tag, "Wake-word listening via source=${audioSourceName(source)}")
        currentAudioRecord = audioRecord
        val shortBuffer = ShortArray(2048)
        val byteBuffer = ByteArray(shortBuffer.size * 2)
        var consecutiveReadErrors = 0
        var lastPhrase = ""
        var lastPhraseLogAt = 0L
        try {
            audioRecord.startRecording()
            while (running && scope.isActive) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read < 0) {
                    consecutiveReadErrors++
                    if (consecutiveReadErrors == 1 || consecutiveReadErrors % 50 == 0) {
                        Log.w(tag, "AudioRecord.read error=$read source=${audioSourceName(source)} consecutive=$consecutiveReadErrors")
                    }
                    if (consecutiveReadErrors >= 250) {
                        error("AudioRecord.read failed repeatedly: $read")
                    }
                    continue
                }
                if (read == 0) continue
                consecutiveReadErrors = 0
                shortsToLittleEndianBytes(shortBuffer, read, byteBuffer)
                val accepted = recognizer.acceptWaveForm(byteBuffer, read * 2)
                val json = if (accepted) recognizer.result else recognizer.partialResult
                val phrase = extractPhrase(json)
                if (phrase.isBlank()) continue
                val now = System.currentTimeMillis()
                if (phrase != lastPhrase || now - lastPhraseLogAt > 3000L) {
                    Log.d(tag, "Heard phrase: '$phrase'")
                    lastPhrase = phrase
                    lastPhraseLogAt = now
                }
                if (matchesAnyHotword(phrase, hotwords)) {
                    Log.i(tag, "Wake-word detected: '$phrase'")
                    running = false
                    mainHandler.post(onDetected)
                    break
                }
            }
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Throwable) {
            }
            audioRecord.release()
            currentAudioRecord = null
            recognizer.close()
        }
    }

    private fun createAudioRecord(): Pair<AudioRecord, Int> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission is not granted")
        }
        val sampleRateInt = sampleRate.toInt()
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRateInt,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuffer, sampleRateInt * 2)
        var lastError: Throwable? = null
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        sources.forEach { source ->
            try {
                val audioRecord = createAudioRecordUnchecked(
                    source,
                    sampleRateInt,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    return audioRecord to source
                }
                try {
                    audioRecord.release()
                } catch (_: Throwable) {
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("AudioRecord init failed for all sources", lastError)
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecordUnchecked(
        source: Int,
        sampleRateInHz: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSizeInBytes: Int
    ): AudioRecord = AudioRecord(source, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)

    private fun ensureModelDirectory(spec: ModelSpec): File {
        val modelDir = File(modelBaseDir, spec.name)
        if (File(modelDir, "am/final.mdl").exists()) {
            return modelDir
        }
        if (!modelBaseDir.exists()) {
            modelBaseDir.mkdirs()
        }
        postStatus("Stahuji offline wake-word model (${spec.name})...")
        val zipFile = File(modelBaseDir, "${spec.name}.zip")
        downloadFile(spec.url, zipFile)
        unzip(zipFile, modelBaseDir)
        zipFile.delete()
        if (!File(modelDir, "am/final.mdl").exists()) {
            error("Downloaded model is incomplete")
        }
        postStatus("Offline wake-word model ${spec.name} je připraven.")
        return modelDir
    }

    private fun resolveModelSpec(recognitionLanguage: String): ModelSpec {
        val normalized = recognitionLanguage.trim().lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("cs") -> ModelSpec(
                name = "vosk-model-small-cs-0.4-rhasspy",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-cs-0.4-rhasspy.zip"
            )
            normalized.startsWith("pl") -> ModelSpec(
                name = "vosk-model-small-pl-0.22",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip"
            )
            else -> fallbackEnglishModel
        }
    }

    private fun downloadFile(url: String, output: File) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Model download failed: HTTP ${response.code}")
            val body = response.body ?: error("Model download body is empty")
            body.byteStream().use { input ->
                FileOutputStream(output).use { out -> input.copyTo(out) }
            }
        }
    }

    private fun unzip(zipFile: File, destination: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destination, entry.name)
                val canonicalDest = destination.canonicalPath + File.separator
                val canonicalOut = outFile.canonicalPath
                if (!canonicalOut.startsWith(canonicalDest)) {
                    error("Blocked zip traversal entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun extractPhrase(json: String): String {
        val partial = Regex("\"partial\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.getOrNull(1).orEmpty()
        if (partial.isNotBlank()) return partial
        return Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun buildHotwordVariants(hotwords: List<String>): Set<String> {
        val variants = linkedSetOf<String>()
        hotwords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { hotword ->
                val normalized = normalize(hotword)
                if (normalized.isBlank()) return@forEach
                variants += normalized
                variants += "hej $normalized"
                variants += "hey $normalized"
            }
        return variants
    }

    private fun matchesAnyHotword(phrase: String, hotwords: Set<String>): Boolean {
        val normalizedPhrase = normalize(phrase)
        if (normalizedPhrase.isBlank()) return false
        val phraseTokens = normalizedPhrase.split(" ").filter { it.isNotBlank() }
        if (phraseTokens.isEmpty()) return false
        return hotwords.any { hotword ->
            val normalizedHotword = normalize(hotword)
            if (normalizedHotword.isBlank()) return@any false
            if (normalizedPhrase == normalizedHotword) return@any true
            if (
                normalizedPhrase.startsWith("$normalizedHotword ") ||
                normalizedPhrase.endsWith(" $normalizedHotword") ||
                normalizedPhrase.contains(" $normalizedHotword ")
            ) {
                return@any true
            }

            val hotwordTokens = normalizedHotword.split(" ").filter { it.isNotBlank() }
            if (hotwordTokens.isEmpty() || phraseTokens.size < hotwordTokens.size) return@any false
            val targetCompact = hotwordTokens.joinToString("")
            val fuzzyAllowed = targetCompact.length >= 4

            for (start in 0..(phraseTokens.size - hotwordTokens.size)) {
                val windowCompact = phraseTokens.subList(start, start + hotwordTokens.size).joinToString("")
                if (windowCompact == targetCompact) return@any true
                if (!fuzzyAllowed) continue
                if (windowCompact.firstOrNull() != targetCompact.firstOrNull()) continue
                if (levenshteinDistance(windowCompact, targetCompact) <= 1) return@any true
            }
            false
        }
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun audioSourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> source.toString()
    }

    private fun shortsToLittleEndianBytes(shorts: ShortArray, length: Int, bytes: ByteArray) {
        var i = 0
        while (i < length) {
            val sample = shorts[i].toInt()
            val byteIndex = i * 2
            bytes[byteIndex] = (sample and 0xFF).toByte()
            bytes[byteIndex + 1] = ((sample ushr 8) and 0xFF).toByte()
            i++
        }
    }

    private fun postStatus(message: String) {
        Log.d(tag, message)
        mainHandler.post { onStatus(message) }
    }

    private fun postError(message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        mainHandler.post { onError(message, throwable) }
    }
}
