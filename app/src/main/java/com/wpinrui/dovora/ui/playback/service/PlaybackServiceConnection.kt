package com.wpinrui.dovora.ui.playback.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.wpinrui.dovora.ui.playback.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackServiceConnection(context: Context) {

    private val appContext = context.applicationContext
    private val _service = MutableStateFlow<MusicPlaybackService?>(null)
    val service: StateFlow<MusicPlaybackService?> = _service.asStateFlow()
    private var pendingQueue: List<MusicTrack>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val playbackBinder = binder as? MusicPlaybackService.PlaybackBinder
            _service.value = playbackBinder?.service
            pendingQueue?.let { queue ->
                playbackBinder?.service?.setQueue(queue)
                pendingQueue = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    init {
        val intent = Intent(appContext, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun release() {
        try {
            appContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // already unbound
        }
        _service.value = null
    }

    fun play(track: MusicTrack) {
        _service.value?.playTrack(track)
    }

    fun play() {
        _service.value?.play()
    }

    fun pause() {
        _service.value?.pause()
    }

    fun seekToFraction(fraction: Float) {
        _service.value?.seekToFraction(fraction)
    }

    fun seekTo(position: Long) {
        _service.value?.seekTo(position)
    }

    fun currentDuration(): Long = _service.value?.currentDuration() ?: 0L

    fun setQueue(queue: List<MusicTrack>) {
        pendingQueue = queue
        _service.value?.setQueue(queue)
    }
    
    fun setHistory(history: List<MusicTrack>) {
        _service.value?.setHistory(history)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _service.value?.moveQueueItem(fromIndex, toIndex)
    }

    fun playNextFromQueue() {
        _service.value?.playNextFromQueue()
    }

    fun playPrevious() {
        _service.value?.playPrevious()
    }
    
    fun getHistory(): List<MusicTrack> = _service.value?.getHistory() ?: emptyList()
}


