package com.wpinrui.dovora.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.wpinrui.dovora.data.api.TokenStorage
import com.wpinrui.dovora.data.download.DownloadManager
import com.wpinrui.dovora.data.download.DownloadState
import com.wpinrui.dovora.data.download.MediaKind
import com.wpinrui.dovora.ui.auth.AccountSheet
import com.wpinrui.dovora.ui.auth.AuthViewModel
import com.wpinrui.dovora.ui.auth.RegisterDialog
import com.wpinrui.dovora.ui.auth.SignInDialog
import com.wpinrui.dovora.ui.components.LibraryIconWithProgress
import com.wpinrui.dovora.ui.playback.LyricsUiState
import com.wpinrui.dovora.ui.playback.MusicPlaybackViewModel
import com.wpinrui.dovora.ui.playback.MusicTrack
import com.wpinrui.dovora.ui.playback.components.TrackArtwork
import com.wpinrui.dovora.ui.playback.formatDuration
import com.wpinrui.dovora.ui.playback.library.MusicLibraryScreen
import com.wpinrui.dovora.ui.playback.nowplaying.NowPlayingDrawer
import com.wpinrui.dovora.ui.playback.nowplaying.NowPlayingScreen
import com.wpinrui.dovora.ui.playback.nowplaying.PlayerPage
import com.wpinrui.dovora.ui.playback.nowplaying.RepeatMode
import com.wpinrui.dovora.ui.download.ExistingTrack
import com.wpinrui.dovora.ui.search.SearchScreen
import com.wpinrui.dovora.ui.video.VideoLibraryScreen
import com.wpinrui.dovora.ui.video.VideoPlaybackService
import com.wpinrui.dovora.ui.video.VideoPlayerScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Playlists : Screen("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic)
    object Search : Screen("search", "Search", Icons.Default.Search) {
        const val ROUTE_WITH_QUERY = "search?query={query}"
        fun createRouteWithQuery(query: String) = "search?query=${java.net.URLEncoder.encode(query, "UTF-8")}"
    }
    // Keep Player route for internal navigation but don't show in bottom nav
    object Player : Screen("player", "Player", Icons.Default.MusicNote)
}

private const val DEBUG_LIBRARY_BUTTON = "DEBUG_LIBRARY_BUTTON"

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val playbackViewModel: MusicPlaybackViewModel = viewModel()
    val appViewModel: AppViewModel = viewModel()
    // Create SearchViewModel at activity level so it persists across navigation and app lifecycle
    val searchViewModel: com.wpinrui.dovora.ui.search.SearchViewModel = viewModel(key = "search_viewmodel")
    val playbackUiState by playbackViewModel.uiState.collectAsState()
    val playerPage by playbackViewModel.playerPage.collectAsState()
    val dominantColor by playbackViewModel.dominantColor.collectAsState()
    val appMode by appViewModel.appMode.collectAsState()
    val videoLibrary by appViewModel.videoLibrary.collectAsState()
    val currentVideo by appViewModel.currentVideo.collectAsState()
    val context = LocalContext.current
    
    // Auth ViewModel for UI (settings, account, etc.)
    val tokenStorage = remember { TokenStorage(context) }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(context, tokenStorage))
    val currentUser by authViewModel.currentUser.collectAsState()
    val showSignInDialog by authViewModel.showSignInDialog.collectAsState()
    val showRegisterDialog by authViewModel.showRegisterDialog.collectAsState()
    val showAccountMenu by authViewModel.showAccountMenu.collectAsState()
    val isSigningIn by authViewModel.isSigningIn.collectAsState()
    val isRegistering by authViewModel.isRegistering.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val email by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()
    val confirmPassword by authViewModel.confirmPassword.collectAsState()
    val inviteCode by authViewModel.inviteCode.collectAsState()

    // Settings state
    val aiPrefillEnabled by authViewModel.aiPrefillEnabled.collectAsState()
    val defaultDownloadType by authViewModel.defaultDownloadType.collectAsState()
    val maxVideoQuality by authViewModel.maxVideoQuality.collectAsState()

    // TODO: Sync on app launch when implementing issue #25 (auth state management)
    // This will sync library from backend when user is logged in

    // Now Playing bottom sheet state
    var showNowPlayingSheet by remember { mutableStateOf(false) }
    
    // Video miniplayer state
    var isVideoMinimized by remember { mutableStateOf(false) }
    
    // Download manager for progress indicator
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val recentlyCompletedTitles by downloadManager.recentlyCompletedTitles.collectAsState()
    val recentlyCompletedVideoTitles by downloadManager.recentlyCompletedVideoTitles.collectAsState()
    
    // Calculate download stats for badge (audio for library icon)
    val pendingAudioDownloads = activeDownloads.filterValues { 
        it.mediaKind == MediaKind.AUDIO && it.state !is DownloadState.Completed && it.state !is DownloadState.Failed 
    }
    val overallAudioProgress = if (pendingAudioDownloads.isEmpty()) {
        0f
    } else {
        pendingAudioDownloads.values.map { it.progress }.average().toFloat() / 100f
    }
    val newAudioDownloadsCount = recentlyCompletedTitles.size
    
    // Calculate video download stats
    val pendingVideoDownloads = activeDownloads.filterValues { 
        it.mediaKind == MediaKind.VIDEO && it.state !is DownloadState.Completed && it.state !is DownloadState.Failed 
    }
    val overallVideoProgress = if (pendingVideoDownloads.isEmpty()) {
        0f
    } else {
        pendingVideoDownloads.values.map { it.progress }.average().toFloat() / 100f
    }
    val newVideoDownloadsCount = recentlyCompletedVideoTitles.size
    
    // Combined progress for library icon based on current mode
    val overallProgress = if (appMode == AppMode.MUSIC) overallAudioProgress else overallVideoProgress
    val pendingDownloadsCount = if (appMode == AppMode.MUSIC) pendingAudioDownloads.size else pendingVideoDownloads.size
    val newDownloadsCount = if (appMode == AppMode.MUSIC) newAudioDownloadsCount else newVideoDownloadsCount
    
    // Auto-switch mode when a download completes
    val isMusicPlaying = playbackUiState.isPlaying && playbackUiState.currentTrack != null
    val isVideoPlaying = currentVideo != null
    
    LaunchedEffect(recentlyCompletedVideoTitles.size) {
        if (recentlyCompletedVideoTitles.isNotEmpty()) {
            appViewModel.onVideoDownloadComplete(isMusicPlaying)
        }
    }
    
    LaunchedEffect(recentlyCompletedTitles.size) {
        if (recentlyCompletedTitles.isNotEmpty()) {
            appViewModel.onAudioDownloadComplete(isVideoPlaying)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    val currentTrack = playbackUiState.currentTrack
    // Show music miniplayer whenever music is playing, regardless of library view
    val showMiniPlayer = currentTrack != null && !showNowPlayingSheet
    val showVideoMiniPlayer = currentVideo != null && isVideoMinimized
    
    // Track if video is in actual fullscreen (landscape) mode
    var isVideoInFullscreen by remember { mutableStateOf(false) }
    
    // Hide bottom bar when video is playing (not minimized) or when Now Playing sheet is open
    val isVideoPlayingNotMinimized = currentVideo != null && !isVideoMinimized
    val shouldHideBottomBar = isVideoPlayingNotMinimized || showNowPlayingSheet
    
    // Track the last route before opening Now Playing for navigation back
    var lastRouteBeforePlayer by remember { mutableStateOf<String?>(null) }
    
    // Video miniplayer snap position (0=bottom-right, 1=bottom-left, 2=top-right, 3=top-left)
    var miniplayerSnapPosition by remember { mutableStateOf(0) }

    LaunchedEffect(currentDestination?.route) {
        Log.d(
            DEBUG_LIBRARY_BUTTON,
            "Nav destination changed to ${currentDestination?.route}, playerPage=$playerPage, currentTrack=${currentTrack?.title}"
        )
    }
    
    // Handle back press when Now Playing sheet is open
    BackHandler(enabled = showNowPlayingSheet) {
        showNowPlayingSheet = false
        // Navigate back to last route or library
        lastRouteBeforePlayer?.let { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } ?: run {
            navController.navigate(Screen.Library.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        lastRouteBeforePlayer = null
    }
    
    // Track route before opening Now Playing
    LaunchedEffect(showNowPlayingSheet) {
        if (showNowPlayingSheet && lastRouteBeforePlayer == null) {
            lastRouteBeforePlayer = currentDestination?.route ?: Screen.Library.route
        }
    }

    // Video dominant color
    val videoDominantColor by appViewModel.videoDominantColor.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        val hasTrack = currentTrack != null
        val hasVideo = currentVideo != null
        val gradientTopColor = when {
            hasVideo -> videoDominantColor
            hasTrack -> dominantColor
            else -> Color(0xFF1A1A2E)
        }
        
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to gradientTopColor,
                            0.3f to gradientTopColor.copy(alpha = 0.8f),
                            0.7f to Color.Black,
                            1.0f to Color.Black
                        )
                    )
                )
        )
        
        Scaffold(
            bottomBar = {
                // Hide entire bottom bar when video is playing (not minimized) or Now Playing sheet is open
                if (!shouldHideBottomBar) {
                    Column {
                        currentTrack?.let { track ->
                            if (showMiniPlayer) {
                                GlobalMiniPlayer(
                                    track = track,
                                    isPlaying = playbackUiState.isPlaying,
                                    progress = playbackUiState.playbackProgress,
                                    onSeek = playbackViewModel::seekTo,
                                    onPrevious = playbackViewModel::skipToPrevious,
                                    onPlayPause = playbackViewModel::togglePlayback,
                                    onNext = playbackViewModel::skipToNext,
                                    onExpand = { showNowPlayingSheet = true }
                                )
                            }
                        }
                        val screens = listOf(Screen.Library, Screen.Playlists, Screen.Search)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(32.dp)
                                )
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            screens.forEach { screen ->
                                val selected =
                                    currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable {
                                            if (screen.route == Screen.Library.route) {
                                                if (currentDestination?.route != Screen.Library.route) {
                                                    val popped = navController.popBackStack(
                                                        Screen.Library.route,
                                                        inclusive = false
                                                    )
                                                    if (!popped) {
                                                        navController.navigate(Screen.Library.route) {
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                }
                                            } else {
                                                // If navigating to Player tab and video is playing, show video player
                                                if (screen.route == Screen.Player.route && currentVideo != null) {
                                                    isVideoMinimized = false
                                                }
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (screen.route == Screen.Library.route) {
                                        LibraryIconWithProgress(
                                            downloadProgress = overallProgress,
                                            activeDownloads = pendingDownloadsCount,
                                            readyCount = newDownloadsCount,
                                            selected = selected,
                                            size = if (selected) 26.dp else 24.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title,
                                            tint = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(if (selected) 26.dp else 24.dp)
                                        )
                                    }
                                    Text(
                                        text = screen.title,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Library.route
                ) {
                    composable(Screen.Library.route) {
                        LibraryScreenWithModeSelector(
                            appMode = appMode,
                            onModeChange = { newMode ->
                                appViewModel.setAppMode(
                                    newMode,
                                    forceSwitchIfPlaying = true,
                                    isMusicPlaying = isMusicPlaying
                                )
                            },
                            canSwitchMode = true, // Always allow switching between music and videos
                            // Music library props
                            playbackViewModel = playbackViewModel,
                            onNavigateToPlayer = {
                                // Open Now Playing sheet directly instead of navigating
                                showNowPlayingSheet = true
                            },
                            onNavigateToSearch = { query ->
                                navController.navigate(Screen.Search.createRouteWithQuery(query)) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            // Video library props
                            videoLibrary = videoLibrary,
                            recentlyCompletedVideoTitles = recentlyCompletedVideoTitles,
                            activeVideoDownloads = activeDownloads.filterValues { it.mediaKind == MediaKind.VIDEO },
                            onPlayVideo = { video ->
                                appViewModel.playVideo(video)
                                isVideoMinimized = false // Reset minimized state for new video
                                // Navigate to player view when clicking on a video
                                navController.navigate(Screen.Player.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onRenameVideo = { video, newTitle ->
                                appViewModel.renameVideo(video, newTitle)
                            },
                            onDeleteVideo = { video ->
                                appViewModel.deleteVideo(video)
                            },
                            onRefreshVideos = { appViewModel.refreshVideoLibrary() },
                            onMarkVideoTitleSeen = { downloadManager.markVideoTitleAsSeen(it) },
                            onDismissVideoDownload = { downloadManager.removeDownload(it) }
                        )
                    }
                    composable(Screen.Player.route) {
                        // Player route now only shows video player - music uses Now Playing sheet
                        val videoToPlay = currentVideo
                        val isVideoCurrentlyPlaying = videoToPlay != null && !isVideoMinimized
                        
                        if (isVideoCurrentlyPlaying) {
                            VideoPlayerScreen(
                                video = videoToPlay,
                                videoLibrary = videoLibrary,
                                onPlayVideo = { video ->
                                    appViewModel.playVideo(video)
                                },
                                onBack = {
                                    // Minimize to floating miniplayer instead of navigating away
                                    isVideoMinimized = true
                                },
                                onVideoEnded = {
                                    appViewModel.stopVideoPlayback()
                                    isVideoMinimized = false
                                    isVideoInFullscreen = false
                                    navController.navigate(Screen.Library.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                onMinimize = {
                                    isVideoMinimized = true
                                    navController.navigate(Screen.Library.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                onFullscreenChange = { isFullscreen ->
                                    isVideoInFullscreen = isFullscreen
                                }
                            )
                        } else {
                            // If no video, navigate back to library
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                    composable(Screen.Playlists.route) {
                        // Placeholder Playlists screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "Playlists",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Coming soon",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    composable(
                        route = Screen.Search.ROUTE_WITH_QUERY,
                        arguments = listOf(
                            androidx.navigation.navArgument("query") {
                                type = androidx.navigation.NavType.StringType
                                defaultValue = ""
                                nullable = true
                            }
                        )
                    ) { backStackEntry ->
                        val query = backStackEntry.arguments?.getString("query")
                        // Use the activity-scoped ViewModel created at MainScreen level
                        // This ensures search state persists across navigation and app lifecycle
                        val musicLibrary = playbackUiState.library
                        val existingTracks = musicLibrary.map { track ->
                            ExistingTrack(title = track.title, artist = track.artist)
                        }
                        val existingVideoTitles = videoLibrary.map { it.title }
                        SearchScreen(
                            initialQuery = query,
                            viewModel = searchViewModel,
                            existingTracks = existingTracks,
                            existingVideos = existingVideoTitles,
                            aiPrefillEnabled = aiPrefillEnabled,
                            defaultDownloadType = defaultDownloadType,
                            maxVideoQualityHeight = maxVideoQuality.height
                        )
                    }
                }
            }
        }
        
        // Now Playing Bottom Sheet
        val trackForBottomSheet = currentTrack
        val shuffleEnabled by playbackViewModel.shuffleEnabled.collectAsState()
        val repeatMode by playbackViewModel.repeatMode.collectAsState()
        val lyricsState by playbackViewModel.lyricsState.collectAsState()
        val fullQueue = playbackUiState.fullQueue
        val currentQueueIndex = playbackUiState.currentQueueIndex
        
        if (trackForBottomSheet != null) {
            NowPlayingBottomSheet(
                isVisible = showNowPlayingSheet,
                track = trackForBottomSheet,
                isPlaying = playbackUiState.isPlaying,
                playbackProgress = playbackUiState.playbackProgress,
                playbackPositionLabel = formatDuration(playbackUiState.playbackPositionMillis),
                durationLabel = formatDuration(playbackUiState.effectiveDurationMs),
                durationMs = playbackUiState.effectiveDurationMs,
                dominantColor = dominantColor,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                fullQueue = fullQueue,
                currentQueueIndex = currentQueueIndex,
                lyricsState = lyricsState,
                onSeek = playbackViewModel::seekTo,
                onPlayPause = playbackViewModel::togglePlayback,
                onPrevious = playbackViewModel::skipToPrevious,
                onNext = playbackViewModel::skipToNext,
                onShuffleToggle = playbackViewModel::toggleShuffle,
                onRepeatToggle = playbackViewModel::toggleRepeat,
                onMoveQueueItem = playbackViewModel::moveQueueItem,
                onQueueItemClick = playbackViewModel::playQueuedTrack,
                onShuffleUpNext = playbackViewModel::shuffleUpNext,
                onFetchLyrics = playbackViewModel::fetchLyricsForCurrentTrack,
                onDeleteQueueItem = playbackViewModel::deleteQueueItem,
                getCurrentQueue = { playbackViewModel.uiState.value.upNext },
                onDismiss = {
                    showNowPlayingSheet = false
                    // Navigate back to last route or library
                    lastRouteBeforePlayer?.let { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } ?: run {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    lastRouteBeforePlayer = null
                }
            )
        }
        
        // Floating Video Miniplayer
        val videoForFloatingMiniplayer = currentVideo
        if (showVideoMiniPlayer && videoForFloatingMiniplayer != null) {
            FloatingVideoMiniPlayer(
                video = videoForFloatingMiniplayer,
                snapPosition = miniplayerSnapPosition,
                onSnapPositionChange = { miniplayerSnapPosition = it },
                onExpand = {
                    isVideoMinimized = false
                    navController.navigate(Screen.Player.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                },
                onClose = {
                    isVideoMinimized = false
                    VideoPlaybackService.stop(context)
                    appViewModel.stopVideoPlayback()
                }
            )
        }
    }
}

@Composable
private fun NowPlayingBottomSheet(
    isVisible: Boolean,
    track: MusicTrack,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPositionLabel: String,
    durationLabel: String,
    durationMs: Long,
    dominantColor: Color,
    shuffleEnabled: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    fullQueue: List<MusicTrack>,
    currentQueueIndex: Int,
    lyricsState: LyricsUiState,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit = {},
    onRepeatToggle: () -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit,
    onQueueItemClick: (MusicTrack) -> Unit,
    onShuffleUpNext: () -> Unit,
    onFetchLyrics: () -> Unit,
    onDeleteQueueItem: (MusicTrack) -> Unit,
    getCurrentQueue: () -> List<MusicTrack>,
    onDismiss: () -> Unit
) {
    var showDrawer by remember { mutableStateOf(false) }
    var drawerInitialTab by remember { mutableStateOf(PlayerPage.UpNext) }
    
    // Animated height for Now Playing content when drawer is open
    val nowPlayingHeightAnimatable = remember { Animatable(1f) } // 1 = full, 0 = collapsed
    
    LaunchedEffect(showDrawer) {
        nowPlayingHeightAnimatable.animateTo(
            targetValue = if (showDrawer) 0f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }
    
    // Use Animatable for precise control over animation starting point
    val offsetAnimatable = remember { Animatable(0f) }
    
    // Track the current offset value for recomposition
    var currentOffset by remember { mutableFloatStateOf(0f) }
    
    // Observe Animatable value changes
    LaunchedEffect(offsetAnimatable.value) {
        currentOffset = offsetAnimatable.value
    }
    
    // Track if we're manually handling dismiss animation
    var isManuallyDismissing by remember { mutableStateOf(false) }
    
    // Drag state - declared early so LaunchedEffects can access them
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var shouldDismissFromDrag by remember { mutableStateOf(false) }
    var shouldSnapBack by remember { mutableStateOf(false) }
    val dismissThreshold = screenHeightPx * 0.2f
    
    // Handle dismiss animation from drag position
    LaunchedEffect(shouldDismissFromDrag) {
        if (shouldDismissFromDrag) {
            isManuallyDismissing = true
            // Snap to current drag position first (dragOffset still has the value)
            offsetAnimatable.snapTo(currentOffset + dragOffset)
            // NOW reset dragOffset after snap
            dragOffset = 0f
            // Then animate to dismiss from that position
            offsetAnimatable.animateTo(
                targetValue = screenHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            // Call onDismiss after animation completes
            onDismiss()
            shouldDismissFromDrag = false
        }
    }
    
    // Handle snap back animation
    LaunchedEffect(shouldSnapBack) {
        if (shouldSnapBack) {
            // Snap to current position first, then animate
            offsetAnimatable.snapTo(currentOffset + dragOffset)
            dragOffset = 0f
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            shouldSnapBack = false
        }
    }
    
    // Animate to target position - only handle show animation here
    LaunchedEffect(isVisible) {
        if (isVisible) {
            isManuallyDismissing = false
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else if (!isManuallyDismissing) {
            // Only auto-animate dismiss if not manually handling it
            // This handles cases like back button press
            offsetAnimatable.animateTo(
                targetValue = screenHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    
    
    // Only render when visible or animating
    if (isVisible || currentOffset < screenHeightPx) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (currentOffset + dragOffset).roundToInt().coerceAtLeast(0)) }
                // Solid background to prevent seeing through
                .background(Color.Black)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to dominantColor,
                            0.4f to dominantColor.copy(alpha = 0.8f),
                            0.7f to Color.Black,
                            1.0f to Color.Black
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { 
                            isDragging = true
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            if (dragOffset > dismissThreshold) {
                                // Trigger dismiss animation (dragOffset will be reset in LaunchedEffect)
                                shouldDismissFromDrag = true
                            } else {
                                // Trigger snap back animation (dragOffset will be reset in LaunchedEffect)
                                shouldSnapBack = true
                            }
                            // Don't reset dragOffset here - let LaunchedEffect handle it after snap
                        },
                        onDragCancel = { 
                            isDragging = false
                            dragOffset = 0f 
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Only allow dragging down
                            if (dragAmount > 0 || dragOffset > 0) {
                                dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Header row with chevron and menu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Now Playing content - collapsible when drawer is open
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((LocalConfiguration.current.screenHeightDp.dp - 64.dp) * nowPlayingHeightAnimatable.value)
                        .graphicsLayer {
                            alpha = nowPlayingHeightAnimatable.value
                        }
                ) {
                    if (nowPlayingHeightAnimatable.value > 0.1f) {
                        NowPlayingScreen(
                            track = track,
                            isPlaying = isPlaying,
                            playbackProgress = playbackProgress,
                            playbackPositionLabel = playbackPositionLabel,
                            durationLabel = durationLabel,
                            durationMs = durationMs,
                            dominantColor = dominantColor,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            onSeek = onSeek,
                            onPlayPause = onPlayPause,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onShuffleToggle = onShuffleToggle,
                            onRepeatToggle = onRepeatToggle,
                            onUpNextClick = { 
                                drawerInitialTab = PlayerPage.UpNext
                                showDrawer = true 
                            },
                            onLyricsClick = { 
                                drawerInitialTab = PlayerPage.Lyrics
                                showDrawer = true 
                            }
                        )
                    }
                }
                
            }
        }
        
        // Drawer overlay
        if (showDrawer) {
            NowPlayingDrawer(
                isVisible = showDrawer,
                currentTrack = track,
                isPlaying = isPlaying,
                playbackProgress = playbackProgress,
                playbackPositionLabel = playbackPositionLabel,
                durationLabel = durationLabel,
                durationMs = durationMs,
                fullQueue = fullQueue,
                currentQueueIndex = currentQueueIndex,
                lyricsState = lyricsState,
                dominantColor = dominantColor,
                initialTab = drawerInitialTab,
                onDismiss = { showDrawer = false },
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onMoveQueueItem = onMoveQueueItem,
                onQueueItemClick = onQueueItemClick,
                onShuffleUpNext = onShuffleUpNext,
                onFetchLyrics = onFetchLyrics,
                onDeleteQueueItem = onDeleteQueueItem,
                getCurrentQueue = { fullQueue }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlobalMiniPlayer(
    track: MusicTrack,
    isPlaying: Boolean,
    progress: Float,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
    ) {
        // Progress bar at the top
        SeekableProgressBar(
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(
                artworkPath = track.artworkPath,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingVideoMiniPlayer(
    video: VideoItem,
    snapPosition: Int, // 0=bottom-right, 1=bottom-left, 2=top-right, 3=top-left
    onSnapPositionChange: (Int) -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val miniplayerWidth = 200.dp
    val miniplayerHeight = 112.dp + 40.dp // Video + controls
    val miniplayerWidthPx = with(density) { miniplayerWidth.toPx() }
    val miniplayerHeightPx = with(density) { miniplayerHeight.toPx() }
    val padding = 16.dp
    val paddingPx = with(density) { padding.toPx() }
    val bottomNavHeight = 80.dp
    val bottomNavHeightPx = with(density) { bottomNavHeight.toPx() }
    
    // Calculate snap points - 4 corners only
    val snapPoints = remember(screenWidthPx, screenHeightPx, miniplayerWidthPx, miniplayerHeightPx) {
        listOf(
            Offset(screenWidthPx - miniplayerWidthPx - paddingPx, screenHeightPx - miniplayerHeightPx - paddingPx - bottomNavHeightPx), // bottom-right
            Offset(paddingPx, screenHeightPx - miniplayerHeightPx - paddingPx - bottomNavHeightPx), // bottom-left
            Offset(screenWidthPx - miniplayerWidthPx - paddingPx, paddingPx + 50f), // top-right (below status bar)
            Offset(paddingPx, paddingPx + 50f) // top-left
        )
    }
    
    val currentSnapPoint = snapPoints.getOrElse(snapPosition) { snapPoints[0] }
    val offsetAnimatable = remember { Animatable(currentSnapPoint, Offset.VectorConverter) }
    
    // Animate to snap position when it changes
    LaunchedEffect(snapPosition) {
        offsetAnimatable.animateTo(
            targetValue = snapPoints[snapPosition],
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Track velocity for flick detection
    var lastDragVelocity by remember { mutableStateOf(Offset.Zero) }
    var dragStartTime by remember { mutableStateOf(0L) }
    var lastDragAmount by remember { mutableStateOf(Offset.Zero) }
    
    // Get video service for playback control
    var videoService by remember { mutableStateOf<VideoPlaybackService?>(null) }
    var isBound by remember { mutableStateOf(false) }
    
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                videoService = (binder as VideoPlaybackService.VideoBinder).service
                isBound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                videoService = null
                isBound = false
            }
        }
    }
    
    DisposableEffect(Unit) {
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
    
    val playbackState by videoService?.playbackState?.collectAsState()
        ?: remember { mutableStateOf(VideoPlaybackService.VideoPlaybackState()) }
    
    val isPlaying = playbackState.isPlaying
    val player = videoService?.player
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetAnimatable.value.x.roundToInt(), offsetAnimatable.value.y.roundToInt()) }
                .size(miniplayerWidth, miniplayerHeight)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            dragStartTime = System.currentTimeMillis()
                            lastDragAmount = Offset.Zero
                        },
                        onDragEnd = {
                            // Calculate velocity based on last few drag amounts
                            val elapsed = System.currentTimeMillis() - dragStartTime
                            val velocityThreshold = 800f // pixels per second
                            
                            // Determine target corner based on velocity and position
                            val currentPos = offsetAnimatable.value
                            val centerX = screenWidthPx / 2
                            val centerY = screenHeightPx / 2
                            
                            // Use velocity for flick, otherwise use nearest corner
                            val targetIdx = if (elapsed < 300 && lastDragVelocity.getDistance() > velocityThreshold) {
                                // Flick detected - use velocity direction
                                val velX = lastDragVelocity.x
                                val velY = lastDragVelocity.y
                                when {
                                    velX > 0 && velY > 0 -> 0 // bottom-right
                                    velX < 0 && velY > 0 -> 1 // bottom-left
                                    velX > 0 && velY < 0 -> 2 // top-right
                                    else -> 3 // top-left
                                }
                            } else {
                                // Find nearest corner based on position
                                var minDist = Float.MAX_VALUE
                                var nearestIdx = 0
                                snapPoints.forEachIndexed { idx, point ->
                                    val dist = (currentPos - point).getDistance()
                                    if (dist < minDist) {
                                        minDist = dist
                                        nearestIdx = idx
                                    }
                                }
                                nearestIdx
                            }
                            
                            onSnapPositionChange(targetIdx)
                            lastDragVelocity = Offset.Zero
                        },
                        onDragCancel = { 
                            lastDragVelocity = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            // Track velocity (simple moving average)
                            lastDragVelocity = dragAmount * 60f // Approximate velocity per second at 60fps
                            lastDragAmount = dragAmount
                            
                            val newOffset = Offset(
                                (offsetAnimatable.value.x + dragAmount.x).coerceIn(0f, screenWidthPx - miniplayerWidthPx),
                                (offsetAnimatable.value.y + dragAmount.y).coerceIn(0f, screenHeightPx - miniplayerHeightPx)
                            )
                            coroutineScope.launch {
                                offsetAnimatable.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            Column {
                // Live video with tap to expand
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(Color.Black)
                        .clickable(onClick = onExpand)
                ) {
                    // Show actual video using AndroidView with PlayerView
                    if (player != null) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    this.player = player
                                    useController = false
                                    layoutParams = android.widget.FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Fallback to thumbnail
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
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    
                    // Close button at top right
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // Controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            val pos = videoService?.playbackState?.value?.positionMs ?: 0L
                            videoService?.seekTo((pos - 10000).coerceAtLeast(0))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Play/Pause
                    IconButton(
                        onClick = { videoService?.togglePlayback() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Forward 10s
                    IconButton(
                        onClick = {
                            val pos = videoService?.playbackState?.value?.positionMs ?: 0L
                            val dur = videoService?.playbackState?.value?.durationMs ?: video.durationMs
                            videoService?.seekTo((pos + 10000).coerceAtMost(dur))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekableProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Float = 3f,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.2f)
) {
    var width by remember { mutableStateOf(0f) }
    
    Box(
        modifier = modifier
            .height(16.dp)
            .drawBehind {
                width = size.width
                val barY = size.height / 2 - trackHeight / 2
                drawRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, barY),
                    size = Size(size.width, trackHeight)
                )
                drawRect(
                    color = activeColor,
                    topLeft = Offset(0f, barY),
                    size = Size(size.width * progress.coerceIn(0f, 1f), trackHeight)
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (width > 0f) {
                        onSeek((offset.x / width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (width > 0f) {
                        onSeek((change.position.x / width).coerceIn(0f, 1f))
                    }
                }
            }
    )
}

@Composable
private fun LibraryScreenWithModeSelector(
    appMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    canSwitchMode: Boolean,
    // Music library props
    playbackViewModel: MusicPlaybackViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    // Video library props
    videoLibrary: List<VideoItem>,
    recentlyCompletedVideoTitles: Set<String>,
    activeVideoDownloads: Map<String, com.wpinrui.dovora.data.download.ActiveDownload>,
    onPlayVideo: (VideoItem) -> Unit,
    onRenameVideo: (VideoItem, String) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onRefreshVideos: () -> Unit,
    onMarkVideoTitleSeen: (String) -> Unit,
    onDismissVideoDownload: (String) -> Unit
) {
    val context = LocalContext.current
    val tokenStorage = remember { TokenStorage(context) }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(context, tokenStorage))
    val currentUser by authViewModel.currentUser.collectAsState()
    val showSignInDialog by authViewModel.showSignInDialog.collectAsState()
    val showRegisterDialog by authViewModel.showRegisterDialog.collectAsState()
    val showAccountMenu by authViewModel.showAccountMenu.collectAsState()
    val isSigningIn by authViewModel.isSigningIn.collectAsState()
    val isRegistering by authViewModel.isRegistering.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val email by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()
    val confirmPassword by authViewModel.confirmPassword.collectAsState()
    val inviteCode by authViewModel.inviteCode.collectAsState()

    // Settings state
    val aiPrefillEnabled by authViewModel.aiPrefillEnabled.collectAsState()
    val defaultDownloadType by authViewModel.defaultDownloadType.collectAsState()
    val maxVideoQuality by authViewModel.maxVideoQuality.collectAsState()

    var showModeDropdown by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    // Reset search when mode changes
    LaunchedEffect(appMode) {
        isSearching = false
        searchQuery = ""
    }
    
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Mode selector header with search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSearching) {
                // Search bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (appMode == AppMode.MUSIC) "Search music..." else "Search videos...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                    ),
                                    singleLine = true,
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onSearch = { focusManager.clearFocus() }
                                    )
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val user = currentUser
                                if (user == null) {
                                    authViewModel.openSignInDialog()
                                } else {
                                    authViewModel.openAccountMenu()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Account",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                isSearching = false
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close search",
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                // Mode dropdown trigger
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canSwitchMode) { showModeDropdown = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (appMode == AppMode.MUSIC) "Music" else "Videos",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (canSwitchMode) Color.White.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch mode",
                            tint = if (canSwitchMode) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // Mode dropdown menu
                androidx.compose.material3.DropdownMenu(
                    expanded = showModeDropdown,
                    onDismissRequest = { showModeDropdown = false },
                    modifier = Modifier.background(Color(0xFF1E1E2E), RoundedCornerShape(12.dp))
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (appMode == AppMode.MUSIC) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Music",
                                    color = if (appMode == AppMode.MUSIC) Color.White else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        onClick = {
                            onModeChange(AppMode.MUSIC)
                            showModeDropdown = false
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (appMode == AppMode.VIDEO) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Videos",
                                    color = if (appMode == AppMode.VIDEO) Color.White else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        onClick = {
                            onModeChange(AppMode.VIDEO)
                            showModeDropdown = false
                        }
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Account and Search buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val user = currentUser
                            if (user == null) {
                                authViewModel.openSignInDialog()
                            } else {
                                authViewModel.openAccountMenu()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Account",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { isSearching = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        // Auth dialogs
        if (showSignInDialog) {
            SignInDialog(
                onDismiss = { authViewModel.closeSignInDialog() },
                onLogin = { authViewModel.login() },
                onSwitchToRegister = { authViewModel.switchToRegister() },
                email = email,
                onEmailChange = { authViewModel.updateEmail(it) },
                password = password,
                onPasswordChange = { authViewModel.updatePassword(it) },
                isSigningIn = isSigningIn,
                errorMessage = errorMessage
            )
        }

        if (showRegisterDialog) {
            RegisterDialog(
                onDismiss = { authViewModel.closeRegisterDialog() },
                onRegister = { authViewModel.register() },
                onSwitchToLogin = { authViewModel.switchToLogin() },
                email = email,
                onEmailChange = { authViewModel.updateEmail(it) },
                password = password,
                onPasswordChange = { authViewModel.updatePassword(it) },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { authViewModel.updateConfirmPassword(it) },
                inviteCode = inviteCode,
                onInviteCodeChange = { authViewModel.updateInviteCode(it) },
                isRegistering = isRegistering,
                errorMessage = errorMessage
            )
        }

        // Account Sheet (bottom sheet instead of dialog)
        AccountSheet(
            isVisible = showAccountMenu,
            isSignedIn = currentUser != null,
            userName = if (currentUser != null) authViewModel.getUserDisplayName() else null,
            userEmail = currentUser?.email,
            userPhotoUrl = if (currentUser != null) authViewModel.getUserPhotoUrl() else null,
            aiPrefillEnabled = aiPrefillEnabled,
            defaultDownloadType = defaultDownloadType,
            maxVideoQuality = maxVideoQuality,
            onAiPrefillChange = { authViewModel.setAiPrefillEnabled(it) },
            onDefaultDownloadChange = { authViewModel.setDefaultDownloadType(it) },
            onMaxQualityChange = { authViewModel.setMaxVideoQuality(it) },
            onSignIn = { authViewModel.openSignInDialog() },
            onSignOut = { authViewModel.signOut() },
            onDismiss = { authViewModel.closeAccountMenu() }
        )
        
        // Content based on mode
        if (appMode == AppMode.MUSIC) {
            MusicLibraryScreen(
                viewModel = playbackViewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToSearch = onNavigateToSearch,
                showHeader = false,  // Header is handled by mode selector
                externalSearchQuery = searchQuery
            )
        } else {
            VideoLibraryScreen(
                videos = videoLibrary,
                activeDownloads = activeVideoDownloads,
                recentlyCompletedTitles = recentlyCompletedVideoTitles,
                onPlayVideo = onPlayVideo,
                onRename = onRenameVideo,
                onDelete = onDeleteVideo,
                showHeader = false,
                externalSearchQuery = searchQuery,
                onMarkTitleSeen = onMarkVideoTitleSeen,
                onDismissDownload = onDismissVideoDownload,
                onSearchOnline = onNavigateToSearch
            )
        }
    }
}
