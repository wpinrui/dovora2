package com.wpinrui.dovora.ui.playback

import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import com.wpinrui.dovora.ui.playback.nowplaying.LyricsScreen
import com.wpinrui.dovora.ui.playback.nowplaying.PlayerPage
import com.wpinrui.dovora.ui.playback.nowplaying.UpNextScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicPlaybackScreen(
    viewModel: MusicPlaybackViewModel,
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()
    val playerPage by viewModel.playerPage.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    val currentTrack = uiState.currentTrack

    // Fetch lyrics when on Lyrics tab
    LaunchedEffect(playerPage, currentTrack?.id, lyricsState.lyrics) {
        if (playerPage == PlayerPage.Lyrics &&
            currentTrack != null &&
            lyricsState.lyrics == null &&
            !lyricsState.isLoading
        ) {
            viewModel.fetchLyricsForCurrentTrack()
        }
    }

    if (currentTrack == null) {
        EmptyNowPlayingState(
            onBrowseLibrary = onNavigateToLibrary,
            onSearchMusic = onNavigateToSearch
        )
        return
    }

    // Only use tab pages (Up Next, Lyrics) - Now Playing is accessed via miniplayer
    val tabPages = PlayerPage.tabPages
    val currentTabIndex = tabPages.indexOf(playerPage).coerceAtLeast(0)
    
    val pagerState = rememberPagerState(
        initialPage = currentTabIndex,
        pageCount = { tabPages.size }
    )

    LaunchedEffect(playerPage) {
        val targetIndex = tabPages.indexOf(playerPage)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collectLatest { pageIndex ->
                val desired = tabPages[pageIndex]
                if (viewModel.playerPage.value != desired) {
                    viewModel.setPlayerPage(desired)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = @Composable { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = Color.White
                )
            },
            divider = @Composable {
                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            }
        ) {
            tabPages.forEachIndexed { index, page ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { viewModel.setPlayerPage(page) },
                    text = @Composable { 
                        Text(
                            text = page.label,
                            color = if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.6f)
                        ) 
                    }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            when (tabPages[pageIndex]) {
                            PlayerPage.UpNext -> {
                                UpNextScreen(
                                    track = currentTrack,
                                    isPlaying = uiState.isPlaying,
                                    playbackProgress = uiState.playbackProgress,
                                    playbackPositionLabel = formatDuration(uiState.playbackPositionMillis),
                                    durationLabel = formatDuration(uiState.effectiveDurationMs),
                                    durationMs = uiState.effectiveDurationMs,
                                    fullQueue = uiState.fullQueue,
                                    currentQueueIndex = uiState.currentQueueIndex,
                                    currentTrackHighlightColor = dominantColor,
                                    onPlayPause = viewModel::togglePlayback,
                                    onPrevious = viewModel::skipToPrevious,
                                    onNext = viewModel::skipToNext,
                                    onMoveQueueItem = viewModel::moveQueueItem,
                                    onQueueItemClick = viewModel::playQueuedTrack,
                                    onShuffleUpNext = viewModel::shuffleUpNext,
                                    getCurrentQueue = { viewModel.uiState.value.fullQueue }
                                )
                            }
                PlayerPage.Lyrics -> {
                    LyricsScreen(
                        track = currentTrack,
                        isPlaying = uiState.isPlaying,
                        playbackProgress = uiState.playbackProgress,
                        playbackPositionLabel = formatDuration(uiState.playbackPositionMillis),
                        durationLabel = formatDuration(uiState.effectiveDurationMs),
                        durationMs = uiState.effectiveDurationMs,
                        onPlayPause = viewModel::togglePlayback,
                        onPrevious = viewModel::skipToPrevious,
                        onNext = viewModel::skipToNext,
                        lyricsState = lyricsState,
                        onFetchLyrics = viewModel::fetchLyricsForCurrentTrack
                    )
                }
                else -> {
                    // NowPlaying is handled via bottom sheet, not tabs
                }
            }
        }
    }
}

@Composable
private fun EmptyNowPlayingState(
    onBrowseLibrary: (() -> Unit)?,
    onSearchMusic: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateBadge(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "No active playback"
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Nothing playing yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick any download from your library or find something new in Search to start listening.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (onBrowseLibrary != null) {
            Button(onClick = onBrowseLibrary, modifier = Modifier.fillMaxWidth(0.75f)) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Browse Library",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        if (onSearchMusic != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onSearchMusic, modifier = Modifier.fillMaxWidth(0.75f)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Search for Music",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateBadge(
    icon: ImageVector,
    contentDescription: String?
) {
    Surface(
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = (totalSeconds % 60).toInt()
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
