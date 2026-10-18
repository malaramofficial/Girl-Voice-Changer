package com.example.girlvoicechanger

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private val sampleRate = 44100
    private val minBuffer by lazy {
        max(AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ), 4096)
    }
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val pcm = ByteArrayOutputStream()
    @Volatile private var playbackTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    private var statusView: TextView? = null
    private var recordButton: Button? = null
    private var livePreviewButton: Button? = null
    @Volatile private var liveRecorder: AudioRecord? = null
    @Volatile private var liveTrack: AudioTrack? = null
    @Volatile private var liveThread: Thread? = null
    private val isLivePreviewing = AtomicBoolean(false)
    private val createWavRequest = 201
    private val createPitchWavRequest = 202

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
        }
        root.addView(TextView(this).apply {
            text = "Girl Voice Changer"
            textSize = 26f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Record your voice, save it as WAV, and preview a simple pitch effect."
            textSize = 15f
            setPadding(0, 16, 0, 18)
        })
        statusView = TextView(this).apply {
            text = "Ready. Grant microphone permission to begin."
            textSize = 14f
            setPadding(0, 0, 0, 18)
        }
        recordButton = Button(this).apply {
            text = "Start recording"
            setOnClickListener {
                if (isRecording.get()) stopRecording() else startRecording()
            }
        }
        livePreviewButton = Button(this).apply {
            text = "Start live high-pitch preview"
            setOnClickListener { if (isLivePreviewing.get()) stopLivePreview() else startLivePreview() }
        }
        val playOriginal = Button(this).apply {
            text = "Play original"
            setOnClickListener { playPcm(getPcmCopy(), sampleRate) }
        }
        val playEffect = Button(this).apply {
            text = "Preview higher pitch (demo)"
            setOnClickListener {
                val source = getPcmCopy()
                if (source.isNotEmpty()) {
                    // Demonstration only: resampling raises pitch but also shortens audio.
                    playPcm(source, (sampleRate * 1.25).toInt())
                } else setStatus("Record some audio first.")
            }
        }
        val saveWav = Button(this).apply {
            text = "Save recording as WAV"
            setOnClickListener { requestWavSave(pitchDemo = false) }
        }
        val savePitchWav = Button(this).apply {
            text = "Save higher-pitch demo as WAV"
            setOnClickListener { requestWavSave(pitchDemo = true) }
        }
        val stopPlayback = Button(this).apply {
            text = "Stop playback"
            setOnClickListener { stopPlayback() }
        }
        root.addView(statusView)
        root.addView(recordButton)
        root.addView(livePreviewButton)
        root.addView(playOriginal)
        root.addView(playEffect)
        root.addView(saveWav)
        root.addView(savePitchWav)
        root.addView(stopPlayback)
        root.addView(TextView(this).apply {
            text = "\nVoice conversion status\nThe pitch demo is not natural female voice conversion. A properly licensed DSP/neural conversion engine and on-device quality testing are still needed. WhatsApp live-call microphone replacement is not implemented; ordinary unrooted Android apps cannot normally inject processed audio into another app's microphone."
            textSize = 14f
            setPadding(0, 18, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    /**
     * Real-time in-app pitch-up monitor. This is intentionally NOT advertised as a
     * WhatsApp virtual microphone: Android apps cannot generally replace another app's
     * microphone stream without system-level support.
     *
     * This simple resampling effect raises pitch and changes timbre; it is not neural conversion.
     * Use headphones to avoid acoustic feedback.
     */
    private fun startLivePreview() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            setStatus("Microphone permission is required for live preview.")
            return
        }
        if (isLivePreviewing.get() || isRecording.get()) {
            setStatus("Stop recording or live preview before starting another audio mode.")
            return
        }
        stopPlayback()
        try {
            val inputRate = sampleRate
            val pitchRatio = 1.20
            val outputRate = (inputRate * pitchRatio).toInt()
            val inputBufferBytes = max(AudioRecord.getMinBufferSize(inputRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096)
            val outputMin = AudioTrack.getMinBufferSize(outputRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val outputBufferBytes = max(if (outputMin > 0) outputMin else 8192, 8192)
            val input = AudioRecord(MediaRecorder.AudioSource.MIC, inputRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inputBufferBytes)
            if (input.state != AudioRecord.STATE_INITIALIZED) { input.release(); setStatus("Microphone could not be initialized for live preview."); return }
            val output = AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                AudioFormat.Builder().setSampleRate(outputRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                outputBufferBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            if (output.state != AudioTrack.STATE_INITIALIZED) { input.release(); output.release(); setStatus("Audio output is not supported by this device."); return }
            liveRecorder = input
            liveTrack = output
            input.startRecording()
            output.play()
            isLivePreviewing.set(true)
            livePreviewButton?.text = "Stop live preview"
            setStatus("Live high-pitch preview is on in this app only. Use headphones; WhatsApp routing is not enabled.")
            liveThread = thread(name = "LivePitchPreview") {
                val inputSamples = ShortArray(1024)
                val outputSamples = ShortArray((inputSamples.size / pitchRatio).toInt() + 2)
                try {
                    while (isLivePreviewing.get()) {
                        val count = input.read(inputSamples, 0, inputSamples.size, AudioRecord.READ_BLOCKING)
                        if (count <= 1) continue
                        val outCount = min(outputSamples.size, (count / pitchRatio).toInt())
                        for (i in 0 until outCount) {
                            val sourcePosition = i * pitchRatio
                            val left = sourcePosition.toInt().coerceIn(0, count - 1)
                            val right = min(left + 1, count - 1)
                            val fraction = (sourcePosition - left).toFloat()
                            val value = inputSamples[left] * (1f - fraction) + inputSamples[right] * fraction
                            outputSamples[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                        var writtenSamples = 0
                        while (writtenSamples < outCount && isLivePreviewing.get()) {
                            val written = output.write(outputSamples, writtenSamples, outCount - writtenSamples, AudioTrack.WRITE_BLOCKING)
                            if (written <= 0) break
                            writtenSamples += written
                        }
                    }
                } catch (e: Exception) { runOnUiThread { setStatus("Live preview stopped: ${e.message ?: "audio error"}") } }
                finally {
                    isLivePreviewing.set(false)
                    try { input.stop() } catch (_: Exception) {}
                    input.release()
                    try { output.stop() } catch (_: Exception) {}
                    output.release()
                    if (liveRecorder === input) liveRecorder = null
                    if (liveTrack === output) liveTrack = null
                    runOnUiThread { livePreviewButton?.text = "Start live high-pitch preview" }
                }
            }
        } catch (e: Exception) {
            isLivePreviewing.set(false)
            liveRecorder?.let { try { it.release() } catch (_: Exception) {} }
            liveTrack?.let { try { it.release() } catch (_: Exception) {} }
            liveRecorder = null; liveTrack = null
            livePreviewButton?.text = "Start live high-pitch preview"
            setStatus("Could not start live preview: ${e.message ?: "unknown error"}")
        }
    }

    private fun stopLivePreview() {
        if (!isLivePreviewing.getAndSet(false)) return
        try { liveRecorder?.stop() } catch (_: Exception) {}
        try { liveTrack?.pause(); liveTrack?.flush(); liveTrack?.stop() } catch (_: Exception) {}
        livePreviewButton?.text = "Start live high-pitch preview"
        setStatus("Live preview stopped.")
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            setStatus("Microphone permission is required.")
            return
        }
        if (isRecording.get()) return
        stopLivePreview()
        stopPlayback()
        try {
            synchronized(pcm) { pcm.reset() }
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                setStatus("Microphone could not be initialized on this device.")
                return
            }
            recorder = audioRecord
            audioRecord.startRecording()
            isRecording.set(true)
            recordButton?.text = "Stop recording"
            setStatus("Recording… tap Stop recording when finished.")
            recordingThread = thread(name = "VoiceRecorder") {
                val buffer = ByteArray(minBuffer)
                while (isRecording.get()) {
                    val count = audioRecord.read(buffer, 0, buffer.size)
                    if (count > 0) synchronized(pcm) { pcm.write(buffer, 0, count) }
                    if (count < 0) {
                        runOnUiThread { setStatus("Microphone read error: $count") }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            isRecording.set(false)
            recorder?.release()
            recorder = null
            recordButton?.text = "Start recording"
            setStatus("Recording error: ${e.message ?: "unknown error"}")
        }
    }

    private fun stopRecording() {
        if (!isRecording.getAndSet(false)) return
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        if (Thread.currentThread() != recordingThread) {
            try { recordingThread?.join(800) } catch (_: InterruptedException) { }
        }
        recordingThread = null
        recordButton?.text = "Start recording"
        val data = getPcmCopy()
        setStatus("Recording ready: ${data.size / 2 / sampleRate} seconds. Save it as WAV to keep a copy.")
    }

    private fun getPcmCopy(): ByteArray = synchronized(pcm) { pcm.toByteArray() }

    private fun requestWavSave(pitchDemo: Boolean) {
        if (isRecording.get()) {
            setStatus("Stop recording before saving.")
            return
        }
        if (getPcmCopy().isEmpty()) {
            setStatus("Record some audio first.")
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(
                Intent.EXTRA_TITLE,
                if (pitchDemo) "girl-voice-higher-pitch-demo.wav" else "girl-voice-recording.wav"
            )
        }
        startActivityForResult(intent, if (pitchDemo) createPitchWavRequest else createWavRequest)
    }

    @Deprecated("Use Activity Result APIs in a future update")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode != createWavRequest && requestCode != createPitchWavRequest) || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val pitchDemo = requestCode == createPitchWavRequest
        val audio = getPcmCopy()
        thread(name = "WavExporter") {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    val outputRate = if (pitchDemo) (sampleRate * 1.25).toInt() else sampleRate
                    writeWav(output, audio, outputRate)
                } ?: throw IllegalStateException("Could not open the selected destination")
                runOnUiThread {
                    setStatus(if (pitchDemo) "Higher-pitch demo WAV saved. It is faster/shorter, not natural AI conversion." else "WAV recording saved successfully.")
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Save failed: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private fun writeWav(output: OutputStream, audio: ByteArray, outputSampleRate: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + audio.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort()) // PCM
        header.putShort(1.toShort()) // mono
        header.putInt(outputSampleRate)
        header.putInt(outputSampleRate * 2)
        header.putShort(2.toShort()) // block align
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(audio.size)
        output.write(header.array())
        output.write(audio)
        output.flush()
    }

    private fun playPcm(data: ByteArray, playbackRate: Int) {
        if (isRecording.get()) {
            setStatus("Stop recording before playback.")
            return
        }
        if (data.isEmpty()) {
            setStatus("Record some audio first.")
            return
        }
        stopPlayback()
        thread(name = "VoicePlayback") {
            var track: AudioTrack? = null
            try {
                val minSize = AudioTrack.getMinBufferSize(
                    playbackRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                track = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                    AudioFormat.Builder().setSampleRate(playbackRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    max(minSize, 4096),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                playbackTrack = track
                isPlaying = true
                track.play()
                var offset = 0
                while (offset < data.size && isPlaying) {
                    val count = min(4096, data.size - offset)
                    val written = track.write(data, offset, count)
                    if (written <= 0) break
                    offset += written
                }
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                runOnUiThread {
                    if (isPlaying) setStatus("Playback finished.")
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Playback error: ${e.message ?: "unknown error"}") }
            } finally {
                isPlaying = false
                if (playbackTrack === track) playbackTrack = null
                track?.release()
            }
        }
    }

    private fun stopPlayback() {
        isPlaying = false
        try {
            playbackTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                track.flush()
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    try { track.stop() } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        setStatus("Playback stopped.")
    }

    private fun setStatus(message: String) {
        runOnUiThread { statusView?.text = message }
    }

    override fun onDestroy() {
        if (isRecording.get()) stopRecording()
        stopLivePreview()
        stopPlayback()
        super.onDestroy()
    }
}
