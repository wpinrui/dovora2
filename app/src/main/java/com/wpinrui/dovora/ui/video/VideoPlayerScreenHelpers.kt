package com.wpinrui.dovora.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wpinrui.dovora.ui.VideoItem
import kotlinx.coroutines.delay
import java.util.Locale
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun FullscreenVideoLayout(
    video: VideoItem,
    videoService: VideoPlaybackService?,
    isFitToWidth: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    showControls: Boolean,
    showSeekBackward: Boolean,
    showSeekForward: Boolean,
    playbackSpeed: Float,
    showSpeedMenu: Boolean,
    speedOptions: List<Float>,
    onTogglePlayback: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedMenuDismiss: () -> Unit,
    onSpeedMenuClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onTap: () -> Unit,
    isBuffering: Boolean,
    formatDuration: (Long) -> String,
    onZoomChange: ((Boolean) -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        VideoPlayerSurface(
            videoService = videoService,
            isFitToWidth = isFitToWidth,
            onTap = onTap,
            onDoubleTapLeft = onSeekBackward,
            onDoubleTapRight = onSeekForward,
            onZoomChange = onZoomChange,
            onSwipeDown = onSwipeDown
        )
        
        SeekFeedback(
            showSeekBackward = showSeekBackward,
            showSeekForward = showSeekForward,
            modifier = Modifier.fillMaxSize()
        )
        
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient shadow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Bottom gradient shadow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
                
                // Top bar with chevron and title
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Exit fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Center play/pause button
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onTogglePlayback),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Bottom controls area
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Controls row above progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time on left: 0:00 / 6:35
                        Text(
                            text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        
                        // Controls on right
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed control - simple box with multiplier
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .clickable(onClick = onSpeedMenuClick)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = onSpeedMenuDismiss,
                                    modifier = Modifier.background(Color(0xFF1E1E2E))
                                ) {
                                    speedOptions.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    color = if (speed == playbackSpeed) Color(0xFF4CAF50) else Color.White
                                                )
                                            },
                                            onClick = { onSpeedChange(speed) }
                                        )
                                    }
                                }
                            }
                            
                            // Fullscreen button
                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    // Custom thin progress bar with circle thumb
                    VideoProgressBar(
                        progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
internal fun PortraitVideoLayout(
    video: VideoItem,
    videoLibrary: List<VideoItem>,
    videoService: VideoPlaybackService?,
    isFitToWidth: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    showControls: Boolean,
    showSeekBackward: Boolean,
    showSeekForward: Boolean,
    playbackSpeed: Float,
    showSpeedMenu: Boolean,
    speedOptions: List<Float>,
    isBuffering: Boolean,
    onPlayVideo: ((VideoItem) -> Unit)?,
    onTogglePlayback: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onMinimize: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedMenuDismiss: () -> Unit,
    onSpeedMenuClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onTap: () -> Unit,
    formatDuration: (Long) -> String,
    onZoomChange: ((Boolean) -> Unit)? = null
) {
    // Gradient background for the entire view
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black,
                        0.3f to Color(0xFF0A0A0A),
                        1.0f to Color.Black
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            VideoPlayerSurface(
                videoService = videoService,
                isFitToWidth = isFitToWidth,
                onTap = onTap,
                onDoubleTapLeft = onSeekBackward,
                onDoubleTapRight = onSeekForward,
                onSwipeUp = onToggleFullscreen,
                onSwipeDown = onMinimize,
                onZoomChange = onZoomChange
            )
            
            SeekFeedback(
                showSeekBackward = showSeekBackward,
                showSeekForward = showSeekForward,
                modifier = Modifier.fillMaxSize()
            )
            
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top gradient shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    // Bottom gradient shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                    
                    // Chevron at top left to minimize
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 4.dp, top = 4.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Center play/pause button
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable(onClick = onTogglePlayback),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    // Bottom controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Controls row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time on left
                            Text(
                                text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            
                            // Controls on right
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speed control
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                            .clickable(onClick = onSpeedMenuClick)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${playbackSpeed}x",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = onSpeedMenuDismiss,
                                        modifier = Modifier.background(Color(0xFF1E1E2E))
                                    ) {
                                        speedOptions.forEach { speed ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${speed}x",
                                                        color = if (speed == playbackSpeed) Color(0xFF4CAF50) else Color.White
                                                    )
                                                },
                                                onClick = { onSpeedChange(speed) }
                                            )
                                        }
                                    }
                                }
                                
                                // Fullscreen button
                                IconButton(
                                    onClick = onToggleFullscreen,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        
                        // Progress bar at bottom
                        VideoProgressBar(
                            progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onSeek = onSeek,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Buffering indicator
            if (isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        // Title below player
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        // Video list below title
        if (videoLibrary.isNotEmpty() && onPlayVideo != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = rememberLazyListState(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videoLibrary, key = { it.id }) { videoItem ->
                    VideoListItem(
                        video = videoItem,
                        isCurrentVideo = videoItem.id == video.id,
                        onClick = { onPlayVideo(videoItem) }
                    )
                }
            }
        } else {
            // Fill remaining space when no video list
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun VideoPlayerSurface(
    videoService: VideoPlaybackService?,
    isFitToWidth: Boolean,
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onZoomChange: ((Boolean) -> Unit)? = null
) {
    var cumulativeZoom by remember { mutableFloatStateOf(1f) }
    var totalSwipeUp by remember { mutableFloatStateOf(0f) }
    var totalSwipeDown by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val screenWidth = size.width
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        if (offset.x < screenWidth / 2) {
                            onDoubleTapLeft()
                        } else {
                            onDoubleTapRight()
                        }
                    }
                )
            }
            .then(
                if (onZoomChange != null) {
                    Modifier.pointerInput(isFitToWidth) {
                        detectTransformGestures { _, _, zoom, _ ->
                            cumulativeZoom *= zoom
                            if (cumulativeZoom > 1.3f && !isFitToWidth) {
                                onZoomChange(true)
                                cumulativeZoom = 1f
                            } else if (cumulativeZoom < 0.7f && isFitToWidth) {
                                onZoomChange(false)
                                cumulativeZoom = 1f
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (onSwipeUp != null || onSwipeDown != null) {
                    Modifier.pointerInput(onSwipeUp, onSwipeDown) {
                        val threshold = size.height * 0.15f
                        detectVerticalDragGestures(
                            onDragStart = { 
                                totalSwipeUp = 0f
                                totalSwipeDown = 0f
                            },
                            onDragEnd = {
                                if (onSwipeUp != null && totalSwipeUp > threshold) {
                                    onSwipeUp()
                                }
                                if (onSwipeDown != null && totalSwipeDown > threshold) {
                                    onSwipeDown()
                                }
                                totalSwipeUp = 0f
                                totalSwipeDown = 0f
                            },
                            onDragCancel = { 
                                totalSwipeUp = 0f
                                totalSwipeDown = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // Accumulate upward swipes (negative dragAmount)
                                if (dragAmount < 0) {
                                    totalSwipeUp += -dragAmount
                                }
                                // Accumulate downward swipes (positive dragAmount)
                                if (dragAmount > 0) {
                                    totalSwipeDown += dragAmount
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val player = videoService?.player
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.resizeMode = if (isFitToWidth) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun VideoProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    
    // Update drag progress when not dragging
    LaunchedEffect(progress, isDragging) {
        if (!isDragging) {
            dragProgress = progress
        }
    }
    
    val displayProgress = if (isDragging) dragProgress else progress
    val thumbSize = 12.dp
    val trackHeight = 3.dp
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val fraction = (tapOffset.x / size.width).coerceIn(0f, 1f)
                        onSeek(fraction)
                    }
                )
            }
            .pointerInput(Unit) {
                val width = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        dragProgress = (startOffset.x / width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragProgress)
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / width).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = 0.3f))
        )
        
        // Active track
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.Red)
        )
        
        // Thumb circle - calculate offset based on progress
        val thumbOffsetDp = with(density) {
            val totalWidth = 300.dp.toPx() // approximate, will be overridden
            (displayProgress * totalWidth).toDp()
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbSize),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayProgress.coerceIn(0.001f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(thumbSize)
                        .clip(RoundedCornerShape(thumbSize / 2))
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
internal fun SeekFeedback(
    showSeekBackward: Boolean,
    showSeekForward: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showSeekBackward,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
        ) {
            LaunchedEffect(showSeekBackward) {
                if (showSeekBackward) {
                    delay(500)
                }
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-10s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        AnimatedVisibility(
            visible = showSeekForward,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
        ) {
            LaunchedEffect(showSeekForward) {
                if (showSeekForward) {
                    delay(500)
                }
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+10s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun VideoListItem(
    video: VideoItem,
    isCurrentVideo: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrentVideo) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                if (video.thumbnailPath != null) {
                    AsyncImage(
                        model = video.thumbnailPath,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = if (isCurrentVideo) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.durationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
