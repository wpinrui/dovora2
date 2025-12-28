package com.wpinrui.dovora.ui.video

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.wpinrui.dovora.ui.VideoItem
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Main video player screen that handles fullscreen and portrait video playback.
 * 
 * @param video The video to play
 * @param videoLibrary List of videos to show in portrait mode for easy switching
 * @param onPlayVideo Callback when user selects a different video from the list
 * @param onBack Called when user wants to go back
 * @param onVideoEnded Called when video finishes playing
 * @param onMinimize Called when user swipes down to minimize (portrait mode only)
 * @param onFullscreenChange Called when fullscreen state changes (for bottom bar visibility)
 */
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    videoLibrary: List<VideoItem> = emptyList(),
    onPlayVideo: ((VideoItem) -> Unit)? = null,
    onBack: () -> Unit,
    onVideoEnded: () -> Unit,
    onMinimize: (() -> Unit)? = null,
    onFullscreenChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // UI State
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isFitToWidth by remember { mutableStateOf(false) }
    var showSeekForward by remember { mutableStateOf(false) }
    var showSeekBackward by remember { mutableStateOf(false) }
    
    // Service binding
    var videoService by remember { mutableStateOf<VideoPlaybackService?>(null) }
    var isBound by remember { mutableStateOf(false) }
    var hasStartedPlayback by remember { mutableStateOf(false) }
    
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    
    // Keep an updated reference to the current video for callbacks
    val currentVideoRef by rememberUpdatedState(video)
    
    // Service connection handler - uses currentVideoRef to always have latest video
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as VideoPlaybackService.VideoBinder).service
                videoService = service
                isBound = true
                // Play will be handled by LaunchedEffect
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                videoService = null
                isBound = false
            }
        }
    }
    
    // Play the video when service is connected or video changes
    LaunchedEffect(video.id, videoService) {
        val service = videoService ?: return@LaunchedEffect
        val currentlyPlaying = service.playbackState.value.currentVideo
        if (currentlyPlaying?.id != video.id) {
            service.playVideo(video)
            hasStartedPlayback = true
        }
    }
    
    // Bind to video service
    DisposableEffect(video.id) {
        VideoPlaybackService.start(context)
        context.bindService(
            Intent(context, VideoPlaybackService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        
        onDispose {
            if (isBound) {
                try { context.unbindService(serviceConnection) } catch (_: Exception) {}
            }
        }
    }
    
    // Observe playback state
    val playbackState by videoService?.playbackState?.collectAsState() 
        ?: remember { mutableStateOf(VideoPlaybackService.VideoPlaybackState()) }
    
    val isPlaying = playbackState.isPlaying
    val currentPosition = playbackState.positionMs
    val duration = playbackState.durationMs.takeIf { it > 0 } ?: video.durationMs
    val isBuffering = videoService?.player?.playbackState == Player.STATE_BUFFERING
    val hasEnded = playbackState.hasEnded
    
    // Handle video ended
    LaunchedEffect(hasEnded) {
        if (hasEnded) {
            VideoPlaybackService.stop(context)
            onVideoEnded()
        }
    }
    
    // Keep screen on while playing video
    DisposableEffect(isPlaying) {
        activity?.window?.let { window ->
            if (isPlaying) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    // Auto-dismiss seek feedback
    LaunchedEffect(showSeekBackward) {
        if (showSeekBackward) {
            delay(500)
            showSeekBackward = false
        }
    }
    
    LaunchedEffect(showSeekForward) {
        if (showSeekForward) {
            delay(500)
            showSeekForward = false
        }
    }
    
    // Handle back button - fullscreen exits to portrait, portrait minimizes
    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onMinimize?.invoke()
        }
    }
    
    // Handle orientation and system UI for fullscreen
    LaunchedEffect(isFullscreen) {
        onFullscreenChange?.invoke(isFullscreen)
        activity?.let { act ->
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    
    // Reset zoom when exiting fullscreen
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            isFitToWidth = false
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    
    // Helper to seek with fresh position
    val seekBackward: () -> Unit = {
        val pos = videoService?.playbackState?.value?.positionMs ?: 0L
        videoService?.seekTo((pos - 10000).coerceAtLeast(0))
        showSeekBackward = true
    }
    
    val seekForward: () -> Unit = {
        val pos = videoService?.playbackState?.value?.positionMs ?: 0L
        val dur = videoService?.playbackState?.value?.durationMs ?: duration
        videoService?.seekTo((pos + 10000).coerceAtMost(dur))
        showSeekForward = true
    }
    
    val changeSpeed: (Float) -> Unit = { speed ->
        playbackSpeed = speed
        videoService?.setPlaybackSpeed(speed)
        showSpeedMenu = false
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isFullscreen) {
            FullscreenVideoLayout(
                video = video,
                videoService = videoService,
                isFitToWidth = isFitToWidth,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                showControls = showControls,
                showSeekBackward = showSeekBackward,
                showSeekForward = showSeekForward,
                playbackSpeed = playbackSpeed,
                showSpeedMenu = showSpeedMenu,
                speedOptions = speedOptions,
                onTogglePlayback = { videoService?.togglePlayback() },
                onSeek = { videoService?.seekToFraction(it) },
                onToggleFullscreen = { isFullscreen = false },
                onBack = onBack,
                onSpeedChange = changeSpeed,
                onSpeedMenuDismiss = { showSpeedMenu = false },
                onSpeedMenuClick = { showSpeedMenu = true },
                onSeekBackward = seekBackward,
                onSeekForward = seekForward,
                onTap = { showControls = !showControls },
                isBuffering = isBuffering,
                formatDuration = ::formatDuration,
                onZoomChange = { zoomIn -> isFitToWidth = zoomIn },
                onSwipeDown = { isFullscreen = false }
            )
        } else {
            PortraitVideoLayout(
                video = video,
                videoLibrary = videoLibrary,
                videoService = videoService,
                isFitToWidth = isFitToWidth,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                showControls = showControls,
                showSeekBackward = showSeekBackward,
                showSeekForward = showSeekForward,
                playbackSpeed = playbackSpeed,
                showSpeedMenu = showSpeedMenu,
                speedOptions = speedOptions,
                isBuffering = isBuffering,
                onPlayVideo = onPlayVideo,
                onTogglePlayback = { videoService?.togglePlayback() },
                onSeek = { videoService?.seekToFraction(it) },
                onToggleFullscreen = { isFullscreen = true },
                onBack = onBack,
                onMinimize = { onMinimize?.invoke() },
                onSpeedChange = changeSpeed,
                onSpeedMenuDismiss = { showSpeedMenu = false },
                onSpeedMenuClick = { showSpeedMenu = true },
                onSeekBackward = seekBackward,
                onSeekForward = seekForward,
                onTap = { showControls = !showControls },
                formatDuration = ::formatDuration,
                onZoomChange = { zoomIn -> isFitToWidth = zoomIn }
            )
        }
    }
}

/**
 * Format milliseconds to a human-readable duration string (e.g., "1:23" or "1:23:45")
 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

