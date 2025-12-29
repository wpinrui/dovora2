package com.wpinrui.dovora.ui.playback.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.wpinrui.dovora.data.api.TokenStorage
import com.wpinrui.dovora.data.download.DownloadManager
import com.wpinrui.dovora.data.download.DownloadState
import com.wpinrui.dovora.ui.auth.AccountSheet
import com.wpinrui.dovora.ui.auth.SignInDialog
import com.wpinrui.dovora.ui.auth.AuthViewModel
import com.wpinrui.dovora.ui.playback.MusicPlaybackViewModel
import com.wpinrui.dovora.ui.playback.MusicTrack
import com.wpinrui.dovora.ui.playback.nowplaying.PlayerPage

private const val DEBUG_LIBRARY_BUTTON = "DEBUG_LIBRARY_BUTTON"

@Composable
fun MusicLibraryScreen(
    viewModel: MusicPlaybackViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: (String) -> Unit = {},
    showHeader: Boolean = true,
    externalSearchQuery: String = ""
) {
    val context = LocalContext.current
    val tokenStorage = remember { TokenStorage(context) }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(context, tokenStorage))
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var trackToRename by remember { mutableStateOf<MusicTrack?>(null) }
    var trackToEditArtist by remember { mutableStateOf<MusicTrack?>(null) }
    var trackToDelete by remember { mutableStateOf<MusicTrack?>(null) }
    var trackForArtwork by remember { mutableStateOf<MusicTrack?>(null) }

    val downloadManager = remember { DownloadManager.getInstance(context) }
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val recentlyCompletedTitles by downloadManager.recentlyCompletedTitles.collectAsState()

    // Auth state
    val showSignInDialog by authViewModel.showSignInDialog.collectAsState()
    val showAccountMenu by authViewModel.showAccountMenu.collectAsState()
    val isSigningIn by authViewModel.isSigningIn.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val email by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()

    // Track completed downloads count to trigger refresh
    val completedCount = activeDownloads.values.count { it.state is DownloadState.Completed }
    
    // Refresh library when a download completes
    LaunchedEffect(completedCount) {
        if (completedCount > 0) {
            viewModel.refreshDownloads()
        }
    }

    val artworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val track = trackForArtwork
        if (uri != null && track != null) {
            viewModel.updateTrackArtwork(track, uri)
        }
        trackForArtwork = null
    }

    LaunchedEffect(trackForArtwork) {
        if (trackForArtwork != null) {
            artworkPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDownloads()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MusicLibraryList(
        tracks = uiState.library,
        activeDownloads = activeDownloads,
        recentlyCompletedTitles = recentlyCompletedTitles,
        onDismissDownload = { downloadManager.removeDownload(it) },
        onRetryDownload = { downloadManager.retryDownload(it) },
        onMarkTitleSeen = { downloadManager.markTitleAsSeen(it) },
        userPhotoUrl = if (currentUser != null) authViewModel.getUserPhotoUrl() else null,
        onSelect = { track ->
            viewModel.selectTrack(track)
            viewModel.setPlayerPage(PlayerPage.NowPlaying)
            Log.d(
                DEBUG_LIBRARY_BUTTON,
                "Library item selected track=${track.title}, navigating to player"
            )
            onNavigateToPlayer()
        },
        onRename = { trackToRename = it },
        onEditArtist = { trackToEditArtist = it },
        onChangeArtwork = { trackForArtwork = it },
        onDelete = { trackToDelete = it },
        onSearchOnline = onNavigateToSearch,
        onAccountClick = {
            if (currentUser == null) {
                authViewModel.openSignInDialog()
            } else {
                authViewModel.openAccountMenu()
            }
        },
        showHeader = showHeader,
        externalSearchQuery = externalSearchQuery
    )
    
    // Settings state
    val aiPrefillEnabled by authViewModel.aiPrefillEnabled.collectAsState()
    val defaultDownloadType by authViewModel.defaultDownloadType.collectAsState()
    val maxVideoQuality by authViewModel.maxVideoQuality.collectAsState()
    
    // Auth dialogs
    if (showSignInDialog) {
        SignInDialog(
            onDismiss = { authViewModel.closeSignInDialog() },
            onLogin = { authViewModel.login() },
            email = email,
            onEmailChange = { authViewModel.updateEmail(it) },
            password = password,
            onPasswordChange = { authViewModel.updatePassword(it) },
            isSigningIn = isSigningIn,
            errorMessage = errorMessage
        )
    }
    
    // Account Sheet
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

    trackToRename?.let { track ->
        var newTitle by remember(track) { mutableStateOf(track.title) }
        AlertDialog(
            onDismissRequest = { trackToRename = null },
            title = { Text("Rename song") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Song title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameTrack(track, newTitle.trim())
                        trackToRename = null
                    },
                    enabled = newTitle.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    trackToEditArtist?.let { track ->
        var newArtist by remember(track) { mutableStateOf(track.artist) }
        AlertDialog(
            onDismissRequest = { trackToEditArtist = null },
            title = { Text("Edit artist") },
            text = {
                OutlinedTextField(
                    value = newArtist,
                    onValueChange = { newArtist = it },
                    label = { Text("Artist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTrackArtist(track, newArtist.trim())
                        trackToEditArtist = null
                    },
                    enabled = newArtist.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToEditArtist = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Delete \"${track.title}\"?") },
            text = {
                Text("This will remove the downloaded file and all associated data. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrack(track)
                        trackToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
