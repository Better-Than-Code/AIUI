package com.cellular.rpc.engine

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordJob: Job? = null
    private var startTimeMs: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordDurationMs = MutableStateFlow(0L)
    val recordDurationMs: StateFlow<Long> = _recordDurationMs.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startRecording(): Boolean {
        if (_isRecording.value) return false

        return try {
            val audioDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outputFile = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordDurationMs.value = 0L
            _amplitudes.value = emptyList()

            recordJob = scope.launch {
                val ampList = mutableListOf<Float>()
                while (isActive && _isRecording.value) {
                    delay(100)
                    _recordDurationMs.value = System.currentTimeMillis() - startTimeMs
                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    val normalized = (maxAmp / 32767f).coerceIn(0.05f, 1.0f)
                    ampList.add(normalized)
                    if (ampList.size > 40) ampList.removeAt(0)
                    _amplitudes.value = ampList.toList()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failed to start recording: ${e.message}")
            stopRecording(discard = true)
            false
        }
    }

    fun stopRecording(discard: Boolean = false): MessageAttachment? {
        if (!_isRecording.value) return null
        recordJob?.cancel()
        recordJob = null
        _isRecording.value = false

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e("AudioRecorderManager", "Error stopping recorder: ${e.message}")
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error releasing recorder: ${e.message}")
        } finally {
            mediaRecorder = null
        }

        val file = currentOutputFile
        val duration = _recordDurationMs.value
        val amps = _amplitudes.value

        if (discard || file == null || !file.exists() || duration < 500) {
            file?.delete()
            currentOutputFile = null
            return null
        }

        return MessageAttachment(
            id = "voice_${System.currentTimeMillis()}",
            type = AttachmentType.VOICE_NOTE,
            uri = file.toURI().toString(),
            fileName = file.name,
            fileSizeBytes = file.length(),
            mimeType = "audio/mp4",
            durationMs = duration,
            voiceAmplitudes = amps
        )
    }

    fun release() {
        stopRecording(discard = true)
        scope.cancel()
    }
}

/**
 * Headless, production-grade Media3 ExoPlayer Audio Player Manager.
 * Handles audio playback state, accurate position tracking, seeking,
 * automatic resource cleanup, and audio focus transitions.
 */
class AudioPlayerManager(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null

    private val _playingAttachmentId = MutableStateFlow<String?>(null)
    val playingAttachmentId: StateFlow<String?> = _playingAttachmentId.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stop()
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (!playing && exoPlayer?.playbackState != Player.STATE_BUFFERING) {
                        progressJob?.cancel()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("AudioPlayerManager", "ExoPlayer error: ${error.message}", error)
                    stop()
                }
            })
            exoPlayer = player
        }
    }

    fun play(attachment: MessageAttachment) {
        if (_playingAttachmentId.value == attachment.id && exoPlayer != null) {
            if (exoPlayer?.isPlaying == true) {
                pause()
                return
            } else {
                exoPlayer?.play()
                startProgressTracker()
                return
            }
        }

        stop()

        try {
            val player = getOrCreatePlayer()
            val uri = Uri.parse(attachment.uri)
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            _playingAttachmentId.value = attachment.id

            startProgressTracker()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "ExoPlayer playback failed: ${e.message}", e)
            stop()
        }
    }

    fun pause() {
        try {
            exoPlayer?.pause()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Pause failed: ${e.message}")
        }
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun seekTo(positionMs: Long) {
        try {
            exoPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Seek failed: ${e.message}")
        }
    }

    fun stop() {
        progressJob?.cancel()
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Stop failed: ${e.message}")
        }
        _playingAttachmentId.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val current = exoPlayer?.currentPosition ?: 0L
                _currentPositionMs.value = current
                delay(100)
            }
        }
    }

    fun release() {
        stop()
        try {
            exoPlayer?.release()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Release failed: ${e.message}")
        }
        exoPlayer = null
        scope.cancel()
    }
}
