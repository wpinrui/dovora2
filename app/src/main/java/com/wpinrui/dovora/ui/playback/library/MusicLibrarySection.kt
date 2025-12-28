package com.wpinrui.dovora.ui.playback.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wpinrui.dovora.data.download.ActiveDownload
import com.wpinrui.dovora.data.download.DownloadState
import com.wpinrui.dovora.ui.playback.MusicTrack
import com.wpinrui.dovora.ui.playback.components.PlaceholderArtwork
import com.wpinrui.dovora.ui.playback.components.SongRow
import kotlin.math.min

@Composable
internal fun MusicLibraryList(
    tracks: List<MusicTrack>,
    activeDownloads: Map<String, ActiveDownload> = emptyMap(),
    recentlyCompletedTitles: Set<String> = emptySet(),
    onDismissDownload: (String) -> Unit = {},
    onRetryDownload: (String) -> Unit = {},
    onMarkTitleSeen: (String) -> Unit = {},
    onSelect: (MusicTrack) -> Unit,
    onRename: (MusicTrack) -> Unit,
    onEditArtist: (MusicTrack) -> Unit,
    onChangeArtwork: (MusicTrack) -> Unit,
    onDelete: (MusicTrack) -> Unit,
    onSearchOnline: (String) -> Unit = {},
    onAccountClick: () -> Unit = {},
    userPhotoUrl: String? = null,
    showHeader: Boolean = true,
    externalSearchQuery: String = ""
) {
    var isSearching by remember { mutableStateOf(false) }
    var internalSearchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Use external search query when header is hidden
    val searchQuery = if (showHeader) internalSearchQuery else externalSearchQuery
    
    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                fuzzyMatch(track.title, searchQuery) || fuzzyMatch(track.artist, searchQuery)
            }.sortedBy { track ->
                min(
                    levenshteinDistance(track.title.lowercase(), searchQuery.lowercase()),
                    levenshteinDistance(track.artist.lowercase(), searchQuery.lowercase())
                )
            }
        }
    }
    
    // Filter to show only in-progress downloads (not completed)
    val pendingDownloads = activeDownloads.filterValues { 
        it.state !is DownloadState.Completed 
    }
    
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = isSearching,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.weight(1f),
                    label = "search_transition"
                ) { searching ->
                if (searching) {
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
                                    if (internalSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Search music...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    BasicTextField(
                                        value = internalSearchQuery,
                                        onValueChange = { internalSearchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                        ),
                                        cursorBrush = SolidColor(Color.White),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { focusManager.clearFocus() }
                                        )
                                    )
                                }
                                if (internalSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { internalSearchQuery = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserProfileButton(
                                photoUrl = userPhotoUrl,
                                onClick = onAccountClick
                                )
                            IconButton(
                                onClick = {
                                    isSearching = false
                                    internalSearchQuery = ""
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Music",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserProfileButton(
                                photoUrl = userPhotoUrl,
                                onClick = onAccountClick
                                )
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
            }
            }
        }

        when {
            // Show search results state if user is searching (even in empty library)
            filteredTracks.isEmpty() && searchQuery.isNotBlank() -> {
                NoSearchResultsState(
                    query = searchQuery,
                    onSearchOnline = { onSearchOnline(searchQuery) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Only show empty state when library is empty AND user is not searching
            tracks.isEmpty() && pendingDownloads.isEmpty() -> {
                EmptyLibraryState(
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToSearch = { onSearchOnline("") }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Show downloading items first (only when not searching)
                    if (searchQuery.isBlank() && pendingDownloads.isNotEmpty()) {
                        item(key = "downloads_header") {
                            Text(
                                text = "Downloading",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(
                            pendingDownloads.values.toList(),
                            key = { "download_${it.id}" }
                        ) { download ->
                            DownloadProgressCard(
                                download = download,
                                onDismiss = { onDismissDownload(download.id) },
                                onRetry = { onRetryDownload(download.id) }
                            )
                        }
                        if (filteredTracks.isNotEmpty()) {
                            item(key = "library_header") {
                                Text(
                                    text = "Library",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                    
                    items(filteredTracks, key = { it.id }) { track ->
                        val shouldShimmer = track.title in recentlyCompletedTitles
                        SongRow(
                            track = track,
                            onClick = { onSelect(track) },
                            onRename = onRename,
                            onEditArtist = onEditArtist,
                            onChangeArtwork = onChangeArtwork,
                            onDelete = onDelete,
                            showShimmer = shouldShimmer,
                            onShimmerComplete = { onMarkTitleSeen(track.title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(
    download: ActiveDownload,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val isFailed = download.state is DownloadState.Failed
    val backgroundColor = if (isFailed) {
        Color(0xFF2D1F1F)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Download icon with circular progress
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background circle
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(48.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )
                // Progress circle
                if (!isFailed) {
                    CircularProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF4CAF50),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
                // Icon in center
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isFailed) Color(0xFFCF6679) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Title and status
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (download.state) {
                            is DownloadState.Preparing -> "Preparing..."
                            is DownloadState.Downloading -> "${download.progress}%"
                            is DownloadState.Finalizing -> "Finalizing..."
                            is DownloadState.Completed -> "Complete"
                            is DownloadState.Failed -> "Failed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (download.state) {
                            is DownloadState.Failed -> Color(0xFFCF6679)
                            is DownloadState.Completed -> Color(0xFF81C784)
                            else -> Color.White.copy(alpha = 0.5f)
                        }
                    )
                    
                    if (download.artist != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                        Text(
                            text = download.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Progress bar
                if (!isFailed && download.state !is DownloadState.Completed) {
                    LinearProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
            
            // Retry and dismiss buttons for failed downloads
            if (isFailed) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color(0xFFCF6679),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlaceholderArtwork(modifier = Modifier.size(96.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Grab songs from the search tab and they'll appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onNavigateToSearch)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Go to Search",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun NoSearchResultsState(
    query: String,
    onSearchOnline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "\"$query\" not in library",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onSearchOnline)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Find a download",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Fuzzy match using Levenshtein distance with a threshold based on string length.
 */
private fun fuzzyMatch(text: String, query: String): Boolean {
    val textLower = text.lowercase()
    val queryLower = query.lowercase()
    
    // Direct substring match
    if (textLower.contains(queryLower)) return true
    
    // Word-start match (e.g., "bt" matches "Bluetooth")
    val words = textLower.split(" ", "-", "_")
    val queryChars = queryLower.toList()
    var charIndex = 0
    for (word in words) {
        if (charIndex < queryChars.size && word.startsWith(queryChars[charIndex].toString())) {
            charIndex++
        }
    }
    if (charIndex == queryChars.size) return true
    
    // Levenshtein distance for typo tolerance
    val distance = levenshteinDistance(textLower, queryLower)
    val threshold = when {
        queryLower.length <= 3 -> 1
        queryLower.length <= 6 -> 2
        else -> 3
    }
    return distance <= threshold
}

/**
 * Standard Levenshtein distance implementation.
 */
private fun levenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length
    
    if (m == 0) return n
    if (n == 0) return m
    
    val dp = Array(m + 1) { IntArray(n + 1) }
    
    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j
    
    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,      // deletion
                dp[i][j - 1] + 1,      // insertion
                dp[i - 1][j - 1] + cost // substitution
            )
        }
    }
    
    return dp[m][n]
}

@Composable
private fun UserProfileButton(
    photoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp)
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Account",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Account",
                tint = Color.White
            )
        }
    }
}
