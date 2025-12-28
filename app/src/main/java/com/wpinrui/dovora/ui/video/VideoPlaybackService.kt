package com.wpinrui.dovora.ui.video

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.wpinrui.dovora.MainActivity
import com.wpinrui.dovora.R
import com.wpinrui.dovora.ui.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

/**
 * Service for video playback that shows notification controls.
 * Unlike music, video notifications only show play/pause (no next/prev) and the video title with progress.
 */
class VideoPlaybackService : Service() {

    inner class VideoBinder : Binder() {
        val service: VideoPlaybackService
            get() = this@VideoPlaybackService
    }

    private val binder = VideoBinder()
    var player: ExoPlayer? = null
        private set
    private var mediaSession: MediaSession? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val handler = Handler(Looper.getMainLooper())
    private var currentThumbnail: Bitmap? = null

    private val _playbackState = MutableStateFlow(VideoPlaybackState())
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackSnapshot()
            if (playbackState == Player.STATE_ENDED) {
                _playbackState.value = _playbackState.value.copy(hasEnded = true)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackSnapshot()
            if (isPlaying) {
                ensureForeground()
            } else {
                updateNotification()
            }
        }
    }

    private val positionUpdater = object : Runnable {
        override fun run() {
            updatePlaybackSnapshot()
            updateNotification()
            handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                addListener(playerListener)
            }

        player?.let { exoPlayer ->
            mediaSession = MediaSession.Builder(this, exoPlayer)
                .setId(SESSION_TAG)
                .build()
        }

        handler.post(positionUpdater)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE_PLAYBACK -> togglePlayback()
            ACTION_STOP -> {
                stopPlayback()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(positionUpdater)
        mediaSession?.release()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    fun playVideo(video: VideoItem) {
        // If already playing this video, don't restart
        val currentVideo = _playbackState.value.currentVideo
        if (currentVideo?.id == video.id && player?.playbackState != Player.STATE_IDLE) {
            // Just ensure we're in foreground and resume if needed
            if (player?.isPlaying == false) {
                player?.play()
            }
            ensureForeground()
            return
        }
        
        val file = File(video.absolutePath)
        if (!file.exists()) return

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .build()
            )
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        // Load thumbnail if available
        currentThumbnail = video.thumbnailPath?.let { path ->
            val thumbFile = File(path)
            if (thumbFile.exists()) {
                BitmapFactory.decodeFile(thumbFile.absolutePath)
            } else null
        }

        _playbackState.value = VideoPlaybackState(
            currentVideo = video,
            isPlaying = true,
            durationMs = video.durationMs.takeIf { it > 0 } ?: player?.duration ?: 0L,
            hasEnded = false
        )
        
        ensureForeground()
    }

    fun play() {
        player?.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun togglePlayback() {
        if (player?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun seekToFraction(fraction: Float) {
        val duration = player?.duration ?: return
        if (duration <= 0) return
        val position = (fraction.coerceIn(0f, 1f) * duration).toLong()
        player?.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    fun stopPlayback() {
        player?.stop()
        _playbackState.value = VideoPlaybackState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureForeground() {
        updatePlaybackSnapshot()
        val notification = buildNotification()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun updateNotification() {
        if (_playbackState.value.currentVideo == null) return
        val notification = buildNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): android.app.Notification {
        val snapshot = _playbackState.value
        val title = snapshot.currentVideo?.title ?: "Video"
        val thumbnail = currentThumbnail

        // Only play/pause action - no next/prev for video
        val playPauseAction = if (snapshot.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                controlPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                controlPendingIntent(ACTION_PLAY)
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val duration = snapshot.durationMs
        val progressMax = if (duration > 0) PROGRESS_MAX else 0
        val progressValue = if (duration > 0) {
            ((snapshot.positionMs.toFloat() / duration.toFloat()) * PROGRESS_MAX)
                .roundToInt()
                .coerceIn(0, PROGRESS_MAX)
        } else {
            0
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Video") // Subtitle
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(snapshot.isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(progressMax, progressValue, duration <= 0)
            .setContentIntent(contentIntent)
            .addAction(playPauseAction) // Only play/pause action
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0) // Only show play/pause in compact view
                    // Media3 MediaSession works independently, token not needed for MediaStyle
            )

        thumbnail?.let { thumb ->
            val scaledThumb = Bitmap.createScaledBitmap(thumb, NOTIFICATION_ARTWORK_SIZE, NOTIFICATION_ARTWORK_SIZE, true)
            builder.setLargeIcon(scaledThumb)
        }

        return builder.build()
    }

    private fun controlPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, VideoPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updatePlaybackSnapshot() {
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs
        val isPlaying = currentPlayer.isPlaying
        val position = currentPlayer.currentPosition
        
        _playbackState.value = _playbackState.value.copy(
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = duration
        )
    }

    private fun createNotificationChannel() {
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Video playback",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    data class VideoPlaybackState(
        val currentVideo: VideoItem? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val hasEnded: Boolean = false
    )

    companion object {
        private const val CHANNEL_ID = "dovora.video_playback"
        private const val NOTIFICATION_ID = 1002
        private const val SESSION_TAG = "dovora-video-session"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L
        private const val PROGRESS_MAX = 1000
        private const val NOTIFICATION_ARTWORK_SIZE = 256
        const val ACTION_PLAY = "com.wpinrui.dovora.action.VIDEO_PLAY"
        const val ACTION_PAUSE = "com.wpinrui.dovora.action.VIDEO_PAUSE"
        const val ACTION_TOGGLE_PLAYBACK = "com.wpinrui.dovora.action.VIDEO_TOGGLE"
        const val ACTION_STOP = "com.wpinrui.dovora.action.VIDEO_STOP"

        fun start(context: Context) {
            val intent = Intent(context, VideoPlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, VideoPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
