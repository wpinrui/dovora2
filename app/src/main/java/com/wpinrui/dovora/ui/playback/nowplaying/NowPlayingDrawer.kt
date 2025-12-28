package com.wpinrui.dovora.ui.playback.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.wpinrui.dovora.ui.playback.LyricsUiState
import com.wpinrui.dovora.ui.playback.MusicTrack
import com.wpinrui.dovora.ui.playback.formatDuration
import com.wpinrui.dovora.ui.playback.nowplaying.LyricsScreen
import com.wpinrui.dovora.ui.playback.nowplaying.TopMiniPlayer
import com.wpinrui.dovora.ui.playback.nowplaying.UpNextScreen
import kotlin.math.roundToInt

/**
 * Clamps a color to ensure visibility: not too dark and not too bright.
 */
private fun clampColorForVisibility(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    // Clamp luminance between 0.15 (not too dark) and 0.5 (not too bright)
    hsl[2] = hsl[2].coerceIn(0.15f, 0.5f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Darkens a color by reducing its luminance by a percentage.
 */
private fun darkenColor(color: Color, percent: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = (hsl[2] * (1f - percent)).coerceAtLeast(0.05f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Brightens a color by increasing its luminance by a percentage.
 */
private fun brightenColor(color: Color, percent: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = (hsl[2] * (1f + percent)).coerceAtMost(0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun NowPlayingDrawer(
    isVisible: Boolean,
    currentTrack: MusicTrack,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPositionLabel: String,
    durationLabel: String,
    durationMs: Long,
    fullQueue: List<MusicTrack>,
    currentQueueIndex: Int,
    lyricsState: LyricsUiState,
    dominantColor: Color,
    initialTab: PlayerPage = PlayerPage.UpNext,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onQueueItemClick: (MusicTrack) -> Unit,
    onShuffleUpNext: () -> Unit,
    onFetchLyrics: () -> Unit,
    onDeleteQueueItem: (MusicTrack) -> Unit,
    getCurrentQueue: () -> List<MusicTrack>
) {
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }
    
    val offsetAnimatable = remember { Animatable(screenHeightPx) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dismissThreshold = screenHeightPx * 0.15f
    
    // Tab state
    val tabPages = listOf(PlayerPage.UpNext, PlayerPage.Lyrics)
    val initialTabIndex = tabPages.indexOf(initialTab).coerceAtLeast(0)
    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex) }
    val pagerState = rememberPagerState(
        initialPage = initialTabIndex,
        pageCount = { tabPages.size }
    )
    
    LaunchedEffect(initialTab) {
        val index = tabPages.indexOf(initialTab).coerceAtLeast(0)
        selectedTabIndex = index
        pagerState.animateScrollToPage(index)
    }
    
    LaunchedEffect(selectedTabIndex) {
        pagerState.animateScrollToPage(selectedTabIndex)
    }
    
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            offsetAnimatable.animateTo(
                targetValue = screenHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    
    // Calculate color variants
    val clampedHighlight = clampColorForVisibility(dominantColor)
    val miniplayerColor = darkenColor(clampedHighlight, 0.5f)  // -50%
    val drawerBackgroundColor = darkenColor(clampedHighlight, 0.2f)  // -20%
    val currentTrackCardColor = brightenColor(clampedHighlight, 0.2f)
    
    if (isVisible || offsetAnimatable.value < screenHeightPx) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * (1f - offsetAnimatable.value / screenHeightPx)))
        ) {
            // Combined container that slides up together
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (offsetAnimatable.value + dragOffset).roundToInt().coerceAtLeast(0)) }
            ) {
                // Miniplayer ABOVE the drawer sheet (darkened by 50%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(miniplayerColor)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    TopMiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                
                // Drawer sheet (darkened by 20%) - wrapped in box with miniplayer color beneath
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(miniplayerColor)  // Layer beneath drawer matches miniplayer
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(drawerBackgroundColor)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    isDragging = false
                                    if (dragOffset > dismissThreshold) {
                                        onDismiss()
                                    }
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount > 0 || dragOffset > 0) {
                                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                    }
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Handle bar
                        Box(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    
                    // Tab bar
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = Color.White
                            )
                        },
                        divider = {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        }
                    ) {
                        tabPages.forEachIndexed { index, page ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = page.label,
                                        color = if (selectedTabIndex == index) Color.White else Color.White.copy(alpha = 0.6f),
                                        fontWeight = if (selectedTabIndex == index) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                    
                    // Content
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { pageIndex ->
                        when (tabPages[pageIndex]) {
                            PlayerPage.UpNext -> {
                                UpNextScreen(
                                    track = currentTrack,
                                    isPlaying = isPlaying,
                                    playbackProgress = playbackProgress,
                                    playbackPositionLabel = playbackPositionLabel,
                                    durationLabel = durationLabel,
                                    durationMs = durationMs,
                                    fullQueue = fullQueue,
                                    currentQueueIndex = currentQueueIndex,
                                    currentTrackHighlightColor = currentTrackCardColor,
                                    onPlayPause = onPlayPause,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                    onMoveQueueItem = onMoveQueueItem,
                                    onQueueItemClick = onQueueItemClick,
                                    onShuffleUpNext = onShuffleUpNext,
                                    onDeleteQueueItem = onDeleteQueueItem,
                                    getCurrentQueue = getCurrentQueue
                                )
                            }
                            PlayerPage.Lyrics -> {
                                LyricsScreen(
                                    track = currentTrack,
                                    isPlaying = isPlaying,
                                    playbackProgress = playbackProgress,
                                    playbackPositionLabel = playbackPositionLabel,
                                    durationLabel = durationLabel,
                                    durationMs = durationMs,
                                    onPlayPause = onPlayPause,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                    lyricsState = lyricsState,
                                    onFetchLyrics = onFetchLyrics
                                )
                            }
                            else -> {}
                        }
                    }
                }
                }
            }
            }
        }
    }
}

