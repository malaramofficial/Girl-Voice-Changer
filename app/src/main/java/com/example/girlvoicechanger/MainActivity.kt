package com.example.girlvoicechanger

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
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
    private var statusView: TextView? = null
    private var recordButton: Button? = null

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
        val title = TextView(this).apply {
            text = "Girl Voice Changer"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        val note = TextView(this).apply {
            text = "Prototype: record, listen, and try a higher-pitch effect. This is not natural AI voice conversion and does not change WhatsApp live-call audio."
            textSize = 15f
            setPadding(0, 16, 0, 18)
        }
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
        val playOriginal = Button(this).apply {
            text = "Play original"
            setOnClickListener { playPcm(pcm.toByteArray(), sampleRate) }
        }
        val playEffect = Button(this).apply {
            text = "Play higher-pitch effect"
            setOnClickListener {
                // Naive pitch shift by resampling: raises pitch but also shortens playback.
                val source = pcm.toByteArray()
                if (source.isNotEmpty()) playPcm(source, (sampleRate * 1.25).toInt())
                else setStatus("Record some audio first.")
            }
        }
        val limitation = TextView(this).apply {
            text = "\nImportant limitation\nAndroid does not let an ordinary unrooted app inject its processed microphone audio into WhatsApp calls. Live-call support is not implemented."
            textSize = 14f
            setPadding(0, 18, 0, 0)
        }
        root.addView(title)
        root.addView(note)
        root.addView(statusView)
        root.addView(recordButton)
        root.addView(playOriginal)
        root.addView(playEffect)
        root.addView(limitation)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            setStatus("Microphone permission is required.")
            return
        }
        if (isRecording.get()) return
        try {
            pcm.reset()
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
                }
            }
        } catch (e: Exception) {
            isRecording.set(false)
            setStatus("Recording error: ${e.message ?: "unknown error"}")
        }
    }

    private fun stopRecording() {
        if (!isRecording.getAndSet(false)) return
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        recordingThread?.join(500)
        recordingThread = null
        recordButton?.text = "Start recording"
        val size = synchronized(pcm) { pcm.size() }
        setStatus("Recording saved in memory: ${size / 2 / sampleRate} seconds. It is not saved as a file.")
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
        thread(name = "VoicePlayback") {
            var track: AudioTrack? = null
            try {
                val min = AudioTrack.getMinBufferSize(
                    playbackRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                track = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                    AudioFormat.Builder().setSampleRate(playbackRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    max(min, 4096),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                track.play()
                var offset = 0
                while (offset < data.size) {
                    val count = min(4096, data.size - offset)
                    val written = track.write(data, offset, count)
                    if (written <= 0) break
                    offset += written
                }
                track.stop()
                runOnUiThread { setStatus("Playback finished.") }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Playback error: ${e.message ?: "unknown error"}") }
            } finally {
                track?.release()
            }
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { statusView?.text = message }
    }

    override fun onDestroy() {
        if (isRecording.get()) stopRecording()
        super.onDestroy()
    }
}
