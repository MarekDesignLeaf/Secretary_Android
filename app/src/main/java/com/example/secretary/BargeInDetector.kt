package com.example.secretary

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlin.math.sqrt

/**
 * BargeInDetector
 *
 * Listens on the microphone while TTS is speaking and detects voice activity.
 * Uses AcousticEchoCanceler + NoiseSuppressor + AutomaticGainControl so that
 * the TTS playback does not self-trigger the detector.
 *
 * Detection logic (MVP):
 *   - Calculate RMS of each audio buffer
 *   - If RMS > [rmsThreshold] for >= [sustainedFrames] consecutive frames → fire onBargIn()
 *
 * Phrase matching is done in VoiceManager after barge-in stops TTS and resumes
 * the normal SpeechRecognizer flow — no secondary ASR needed here.
 *
 * Usage:
 *   val det = BargeInDetector(onBargeIn = { voiceManager.interruptTts() })
 *   det.start()   // call when TTS starts
 *   det.stop()    // call when TTS ends naturally or after barge-in
 */
class BargeInDetector(
    private val onBargeIn: () -> Unit,
    private val rmsThreshold: Float = RMS_THRESHOLD_DEFAULT,
    private val sustainedFrames: Int = SUSTAINED_FRAMES_DEFAULT,
) {
    companion object {
        private const val TAG = "BargeInDetector"

        // Audio config — VOICE_COMMUNICATION source is required for AEC to work
        private const val SAMPLE_RATE   = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT

        // RMS threshold: 0–32768 scale. ~800 ignores quiet background; lower = more sensitive.
        const val RMS_THRESHOLD_DEFAULT  = 900f

        // How many consecutive frames above threshold before triggering (1 frame ≈ 10 ms)
        const val SUSTAINED_FRAMES_DEFAULT = 8   // ~80 ms of sustained voice
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Start the detector. Safe to call multiple times — only one thread runs at a time. */
    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "BargeInDetector").also { it.isDaemon = true; it.start() }
        Log.d(TAG, "BargeInDetector started (threshold=$rmsThreshold, sustained=$sustainedFrames)")
    }

    /** Stop the detector. Blocks until the background thread exits. */
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        Log.d(TAG, "BargeInDetector stopped")
    }

    private fun runLoop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioRecord not available (minBuf=$minBuf)")
            running = false
            return
        }
        // Use a slightly larger buffer for smoother reads (~10 ms frames)
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 100 * 2)  // 10 ms × 2 bytes/sample

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufSize,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord: ${e.message}")
            running = false
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            recorder.release()
            running = false
            return
        }

        // ── Apply audio processing effects ────────────────────────────────────
        val audioSessionId = recorder.audioSessionId

        val aec = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioSessionId)?.also {
                it.enabled = true
                Log.d(TAG, "AcousticEchoCanceler enabled")
            }
        } else { Log.w(TAG, "AcousticEchoCanceler not available"); null }

        val ns = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioSessionId)?.also {
                it.enabled = true
                Log.d(TAG, "NoiseSuppressor enabled")
            }
        } else { Log.w(TAG, "NoiseSuppressor not available"); null }

        val agc = if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioSessionId)?.also {
                it.enabled = true
                Log.d(TAG, "AutomaticGainControl enabled")
            }
        } else { Log.w(TAG, "AutomaticGainControl not available"); null }

        recorder.startRecording()

        val buf = ShortArray(bufSize / 2)
        var sustainCount = 0
        var triggered = false

        try {
            while (running && !triggered) {
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue

                val rms = calculateRms(buf, read)

                if (rms > rmsThreshold) {
                    sustainCount++
                    if (sustainCount >= sustainedFrames) {
                        Log.i(TAG, "Barge-in detected! RMS=$rms after $sustainCount frames")
                        triggered = true
                        running = false
                        onBargeIn()
                    }
                } else {
                    // Reset counter — require *consecutive* frames above threshold
                    sustainCount = 0
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            aec?.release()
            ns?.release()
            agc?.release()
            Log.d(TAG, "BargeInDetector thread exiting (triggered=$triggered)")
        }
    }

    private fun calculateRms(buf: ShortArray, len: Int): Float {
        if (len == 0) return 0f
        var sumSq = 0.0
        for (i in 0 until len) sumSq += (buf[i] * buf[i]).toDouble()
        return sqrt(sumSq / len).toFloat()
    }
}
