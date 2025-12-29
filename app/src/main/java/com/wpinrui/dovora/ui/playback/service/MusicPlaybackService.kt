package com.wpinrui.dovora.ui.playback.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import androidx.palette.graphics.Palette
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
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.wpinrui.dovora.MainActivity
import com.wpinrui.dovora.R
import com.wpinrui.dovora.ui.playback.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.ArrayDeque
import kotlin.math.roundToInt

class MusicPlaybackService : Service() {

    inner class PlaybackBinder : Binder() {
        val service: MusicPlaybackService
            get() = this@MusicPlaybackService
    }

    private val binder = PlaybackBinder()
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val handler = Handler(Looper.getMainLooper())
    private var currentArtwork: Bitmap? = null
    private var currentNotificationBackground: Bitmap? = null
    private var currentDominantColor: Int = 0xFF1A1A1A.toInt()
    private val placeholderArtwork by lazy { buildPlaceholderArtwork() }
    private val upNextQueue = mutableListOf<MusicTrack>()
    private val playedHistory = ArrayDeque<MusicTrack>()
    private val _playbackState = MutableStateFlow(PlaybackStateSnapshot())
    val playbackState: StateFlow<PlaybackStateSnapshot> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackSnapshot()
            if (playbackState == Player.STATE_ENDED) {
                playNextFromQueue()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackSnapshot()
            if (isPlaying) {
                ensureForeground()
            } else {
                // Keep notification visible but mark as not ongoing
                updateNotification()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackSnapshot()
        }
    }

    private val positionUpdater = object : Runnable {
        override fun run() {
            updatePlaybackSnapshot()
            handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                setForegroundMode(true)
                addListener(playerListener)
            }

        val currentPlayer = player ?: return
        val forwardingPlayer = object : ForwardingPlayer(currentPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT -> true
                    Player.COMMAND_SEEK_TO_PREVIOUS -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                playNextFromQueue()
            }

            override fun seekToPrevious() {
                playPrevious()
            }

            override fun seekToNextMediaItem() {
                playNextFromQueue()
            }

            override fun seekToPreviousMediaItem() {
                playPrevious()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setId(SESSION_TAG)
            .build()

        handler.post(positionUpdater)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> togglePlayback()
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_REWIND -> seekBy(-SKIP_INTERVAL_MS)
            ACTION_FORWARD -> seekBy(SKIP_INTERVAL_MS)
            ACTION_SKIP_PREVIOUS -> playPrevious()
            ACTION_SKIP_NEXT -> playNextFromQueue()
            ACTION_STOP -> {
                pause()
                stopSelf()
            }
        }
        ensureForeground()
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
        super.onDestroy()
    }

    fun playTrack(track: MusicTrack, fromHistory: Boolean = false) {
        val file = File(track.absolutePath)
        if (!file.exists()) return
        val current = _playbackState.value.currentTrack
        if (!fromHistory && current != null && current.id != track.id) {
            insertIntoHistory(current)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        val loadedArtwork = loadArtworkBitmap(track)
        currentArtwork = loadedArtwork ?: placeholderArtwork
        currentNotificationBackground = loadedArtwork?.let { createBlurredDarkenedBitmap(it) }
        currentDominantColor = extractDominantColor(loadedArtwork) ?: 0xFF1A1A1A.toInt()

        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            isPlaying = true,
            durationMs = track.durationMs.takeIf { it > 0 } ?: player?.duration ?: 0L
        )
        ensureForeground()
    }

    fun setQueue(queue: List<MusicTrack>) {
        upNextQueue.clear()
        upNextQueue.addAll(queue)
        updatePlaybackSnapshot()
    }
    
    fun setHistory(history: List<MusicTrack>) {
        playedHistory.clear()
        playedHistory.addAll(history.reversed()) // Add in reverse order since we addFirst
        updatePlaybackSnapshot()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in upNextQueue.indices) return
        val targetIndex = toIndex.coerceIn(0, upNextQueue.size - 1)
        val item = upNextQueue.removeAt(fromIndex)
        val insertIndex = if (targetIndex >= upNextQueue.size) upNextQueue.size else targetIndex
        upNextQueue.add(insertIndex, item)
        updatePlaybackSnapshot()
    }

    fun playNextFromQueue(): Boolean {
        val next = if (upNextQueue.isNotEmpty()) upNextQueue.removeAt(0) else null
        return if (next != null) {
            playTrack(next)
            true
        } else {
            updatePlaybackSnapshot()
            false
        }
    }

    fun playPrevious(): Boolean {
        val currentPlayer = player ?: return false
        val currentPosition = currentPlayer.currentPosition
        if (currentPosition > PREVIOUS_THRESHOLD_MS) {
            currentPlayer.seekTo(0L)
            return true
        }
        val previous = if (playedHistory.isNotEmpty()) playedHistory.removeFirst() else null
        return if (previous != null) {
            playTrack(previous, fromHistory = true)
            true
        } else {
            currentPlayer.seekTo(0L)
            true
        }
    }

    fun play() {
        player?.playWhenReady = true
        ensureForeground()
    }

    fun pause() {
        player?.playWhenReady = false
        updateNotification()
    }

    fun seekToFraction(fraction: Float) {
        val duration = player?.duration ?: return
        if (duration <= 0) return
        val position = (fraction.coerceIn(0f, 1f) * duration).toLong()
        player?.seekTo(position)
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun currentDuration(): Long = player?.duration ?: 0L

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
        updatePlaybackSnapshot()
        val notification = buildNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): android.app.Notification {
        val snapshot = _playbackState.value
        val title = snapshot.currentTrack?.title ?: getString(R.string.app_name)
        val artist = snapshot.currentTrack?.artist ?: getString(R.string.app_name)
        val rawArtwork = currentArtwork ?: placeholderArtwork
        // Scale artwork to appropriate size for notification (256x256 is good for most devices)
        val artwork = Bitmap.createScaledBitmap(rawArtwork, NOTIFICATION_ARTWORK_SIZE, NOTIFICATION_ARTWORK_SIZE, true)

        val playPauseAction = if (snapshot.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_pause),
                controlPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                getString(R.string.notification_play),
                controlPendingIntent(ACTION_PLAY)
            )
        }

        val previousTrackAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            getString(R.string.notification_rewind),
            controlPendingIntent(ACTION_SKIP_PREVIOUS)
        )

        val nextTrackAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            getString(R.string.notification_forward),
            controlPendingIntent(ACTION_SKIP_NEXT)
        )

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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(snapshot.isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setColor(currentDominantColor)
            .setLargeIcon(artwork)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(progressMax, progressValue, duration <= 0)
            .setContentIntent(contentIntent)
            .addAction(previousTrackAction)
            .addAction(playPauseAction)
            .addAction(nextTrackAction)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    // Media3 MediaSession works independently, token not needed for MediaStyle
            )
            .build()
    }

    private fun controlPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updatePlaybackSnapshot() {
        val currentPlayer = player
        val duration = currentPlayer?.duration?.takeIf { it > 0 } ?: _playbackState.value.durationMs
        val isPlaying = currentPlayer?.isPlaying ?: _playbackState.value.isPlaying
        val position = currentPlayer?.currentPosition ?: _playbackState.value.positionMs
        val snapshot = PlaybackStateSnapshot(
            currentTrack = _playbackState.value.currentTrack,
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = duration,
            queue = upNextQueue.toList(),
            history = playedHistory.toList()
        )
        _playbackState.value = snapshot
    }

    private fun togglePlayback() {
        if (player?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    private fun seekBy(offsetMs: Long) {
        val player = player ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val target = (player.currentPosition + offsetMs).coerceIn(0L, duration)
        player.seekTo(target)
    }

    private fun insertIntoHistory(track: MusicTrack) {
        playedHistory.removeAll { it.id == track.id }
        playedHistory.addFirst(track)
        while (playedHistory.size > HISTORY_LIMIT) {
            playedHistory.removeLast()
        }
    }

    private fun loadArtworkBitmap(track: MusicTrack?): Bitmap? {
        track ?: return null
        
        // First try custom artwork path
        track.artworkPath?.let { path ->
            val file = File(path.replace('\\', '/'))
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
            }
        }
        
        // Fall back to embedded artwork
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(track.absolutePath)
            retriever.embeddedPicture?.let { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size)
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release failures
            }
        }
    }

    private fun extractDominantColor(bitmap: Bitmap?): Int? {
        if (bitmap == null) return null
        return try {
            val palette = Palette.from(bitmap).generate()
            val swatch = palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: palette.mutedSwatch
            swatch?.rgb?.let { ensureDarkEnough(it) }
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Ensures the color is dark enough for white text readability.
     * If the color is too bright, it darkens it while preserving the hue.
     */
    private fun ensureDarkEnough(color: Int): Int {
        val maxLuminance = 0.3f
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
        
        if (hsl[2] > maxLuminance) {
            hsl[2] = maxLuminance
        }
        
        // Reduce saturation for very saturated bright colors
        if (hsl[1] > 0.7f && hsl[2] > 0.2f) {
            hsl[1] = hsl[1] * 0.8f
        }
        
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }
    
    /**
     * Creates a blurred and darkened version of the bitmap for notification background.
     * Uses a simple box blur algorithm and applies darkening.
     */
    private fun createBlurredDarkenedBitmap(source: Bitmap): Bitmap {
        // Scale down for faster blur and smaller memory footprint
        val scaleFactor = 8
        val smallWidth = (source.width / scaleFactor).coerceAtLeast(1)
        val smallHeight = (source.height / scaleFactor).coerceAtLeast(1)
        
        // Scale down
        val smallBitmap = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        
        // Apply simple box blur by scaling down and up
        val blurredSmall = Bitmap.createScaledBitmap(smallBitmap, smallWidth / 2, smallHeight / 2, true)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, smallWidth * 2, smallHeight * 2, true)
        
        // Create output bitmap and apply darkening
        val output = Bitmap.createBitmap(blurred.width, blurred.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // Draw the blurred bitmap
        canvas.drawBitmap(blurred, 0f, 0f, null)
        
        // Apply darkening overlay
        val darkenPaint = Paint()
        darkenPaint.color = android.graphics.Color.BLACK
        darkenPaint.alpha = 160 // ~63% darkening
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), darkenPaint)
        
        // Clean up intermediate bitmaps
        if (smallBitmap != source) smallBitmap.recycle()
        if (blurredSmall != smallBitmap) blurredSmall.recycle()
        if (blurred != blurredSmall) blurred.recycle()
        
        return output
    }

    private fun buildPlaceholderArtwork(): Bitmap {
        val size = PLACEHOLDER_ARTWORK_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val startColor = ContextCompat.getColor(this, R.color.purple_500)
        val endColor = ContextCompat.getColor(this, R.color.teal_200)
        paint.shader = LinearGradient(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)?.let { icon ->
            val left = (size - icon.width) / 2f
            val top = (size - icon.height) / 2f
            canvas.drawBitmap(icon, left, top, null)
        }

        return bitmap
    }

    private fun createNotificationChannel() {
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    data class PlaybackStateSnapshot(
        val currentTrack: MusicTrack? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val queue: List<MusicTrack> = emptyList(),
        val history: List<MusicTrack> = emptyList()
    )
    
    fun getHistory(): List<MusicTrack> = playedHistory.toList()

    companion object {
        private const val CHANNEL_ID = "dovora.playback"
        private const val NOTIFICATION_ID = 1001
        private const val SESSION_TAG = "dovora-session"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val PROGRESS_MAX = 1000
        private const val SKIP_INTERVAL_MS = 10_000L
        private const val PREVIOUS_THRESHOLD_MS = 3_000L
        private const val HISTORY_LIMIT = 50
        private const val PLACEHOLDER_ARTWORK_SIZE = 512
        private const val NOTIFICATION_ARTWORK_SIZE = 512
        private const val ACTION_TOGGLE_PLAYBACK = "com.wpinrui.dovora.action.TOGGLE"
        private const val ACTION_PLAY = "com.wpinrui.dovora.action.PLAY"
        private const val ACTION_PAUSE = "com.wpinrui.dovora.action.PAUSE"
        private const val ACTION_REWIND = "com.wpinrui.dovora.action.REWIND"
        private const val ACTION_FORWARD = "com.wpinrui.dovora.action.FORWARD"
        private const val ACTION_SKIP_PREVIOUS = "com.wpinrui.dovora.action.SKIP_PREVIOUS"
        private const val ACTION_SKIP_NEXT = "com.wpinrui.dovora.action.SKIP_NEXT"
        private const val ACTION_STOP = "com.wpinrui.dovora.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}


