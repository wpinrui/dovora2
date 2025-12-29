package com.wpinrui.dovora.ui.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wpinrui.dovora.ui.VideoItem
import kotlin.math.min

@Composable
fun VideoLibraryScreen(
    videos: List<VideoItem>,
    activeDownloads: Map<String, com.wpinrui.dovora.data.download.ActiveDownload> = emptyMap(),
    recentlyCompletedTitles: Set<String> = emptySet(),
    onPlayVideo: (VideoItem) -> Unit,
    onRename: (VideoItem, String) -> Unit,
    onDelete: (VideoItem) -> Unit,
    showHeader: Boolean = true,
    externalSearchQuery: String = "",
    onMarkTitleSeen: (String) -> Unit = {},
    onDismissDownload: (String) -> Unit = {},
    onSearchOnline: ((String) -> Unit)? = null,
    onAccountClick: () -> Unit = {}
) {
    var isSearching by remember { mutableStateOf(false) }
    var internalSearchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var videoToRename by remember { mutableStateOf<VideoItem?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    
    // Use external search query when header is hidden
    val searchQuery = if (showHeader) internalSearchQuery else externalSearchQuery
    
    // Filter to show only in-progress downloads
    val pendingDownloads = activeDownloads.filterValues { 
        it.state !is com.wpinrui.dovora.data.download.DownloadState.Completed 
    }
    
    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) {
            videos
        } else {
            videos.filter { video ->
                fuzzyMatch(video.title, searchQuery)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header with search (only shown when showHeader is true)
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = isSearching,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "search_transition"
                ) { searching ->
                    if (searching) {
                        // Search bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                            text = "Search videos...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    BasicTextField(
                                        value = internalSearchQuery,
                                        onValueChange = { internalSearchQuery = it },
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { focusManager.clearFocus() }
                                        )
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        internalSearchQuery = ""
                                        isSearching = false
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close search",
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Videos",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onAccountClick) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Account",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isSearching = true
                                    }
                                ) {
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
            filteredVideos.isEmpty() && searchQuery.isNotBlank() -> {
                NoSearchResultsState(
                    query = searchQuery,
                    modifier = Modifier.fillMaxSize(),
                    onSearchOnline = onSearchOnline?.let { { it(searchQuery) } }
                )
            }
            // Only show empty state when library is empty AND user is not searching
            videos.isEmpty() && pendingDownloads.isEmpty() -> {
                EmptyVideoLibraryState(
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToSearch = { onSearchOnline?.invoke("") }
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
                            VideoDownloadProgressCard(
                                download = download,
                                onDismiss = { onDismissDownload(download.id) }
                            )
                        }
                        if (filteredVideos.isNotEmpty()) {
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
                    
                    items(filteredVideos, key = { it.id }) { video ->
                        val shouldShimmer = video.title in recentlyCompletedTitles
                        VideoCard(
                            video = video,
                            onClick = { onPlayVideo(video) },
                            onRename = { videoToRename = video },
                            onDelete = { videoToDelete = video },
                            showShimmer = shouldShimmer,
                            onShimmerComplete = { onMarkTitleSeen(video.title) }
                        )
                    }
                }
            }
        }
    }
    
    // Rename dialog
    videoToRename?.let { video ->
        var newTitle by remember(video) { mutableStateOf(video.title) }
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("Rename video") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Video title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(video, newTitle.trim())
                        videoToRename = null
                    },
                    enabled = newTitle.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete confirmation dialog
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Delete \"${video.title}\"?") },
            text = {
                Text("This will remove the downloaded video file. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(video)
                        videoToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VideoDownloadProgressCard(
    download: com.wpinrui.dovora.data.download.ActiveDownload,
    onDismiss: () -> Unit
) {
    val isFailed = download.state is com.wpinrui.dovora.data.download.DownloadState.Failed
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder thumbnail
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 45.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (isFailed) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFFCF6679),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = when (download.state) {
                        is com.wpinrui.dovora.data.download.DownloadState.Preparing -> "Preparing..."
                        is com.wpinrui.dovora.data.download.DownloadState.Downloading -> "${download.progress}%"
                        is com.wpinrui.dovora.data.download.DownloadState.Finalizing -> "Finalizing..."
                        is com.wpinrui.dovora.data.download.DownloadState.Completed -> "Complete"
                        is com.wpinrui.dovora.data.download.DownloadState.Failed -> "Failed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (download.state) {
                        is com.wpinrui.dovora.data.download.DownloadState.Failed -> Color(0xFFCF6679)
                        is com.wpinrui.dovora.data.download.DownloadState.Completed -> Color(0xFF81C784)
                        else -> Color.White.copy(alpha = 0.5f)
                    }
                )
                
                // Progress bar
                if (!isFailed && download.state !is com.wpinrui.dovora.data.download.DownloadState.Completed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.LinearProgressIndicator(
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
            
            // Dismiss button for failed downloads
            if (isFailed) {
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
private fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    showShimmer: Boolean = false,
    onShimmerComplete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Shimmer effect - mark as seen after display
    LaunchedEffect(showShimmer) {
        if (showShimmer) {
            kotlinx.coroutines.delay(1500)
            onShimmerComplete()
        }
    }
    
    // Shimmer animation
    val shimmerModifier = if (showShimmer) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_progress"
        )
        
        Modifier.drawBehind {
            val shimmerWidth = size.width * 0.3f
            val shimmerOffset = shimmerProgress * (size.width + shimmerWidth) - shimmerWidth
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    start = androidx.compose.ui.geometry.Offset(shimmerOffset, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmerOffset + shimmerWidth, size.height)
                )
            )
        }
    } else {
        Modifier
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(shimmerModifier),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail with play overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
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
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            
            // Title and menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF1E1E2E), RoundedCornerShape(16.dp)),
                        offset = DpOffset((-8).dp, 0.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVideoLibraryState(
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {}
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No videos yet",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Download videos from the search tab and they'll appear here.",
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
    modifier: Modifier = Modifier,
    onSearchOnline: (() -> Unit)? = null
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
        if (onSearchOnline != null) {
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
}

/**
 * Fuzzy match using Levenshtein distance with a threshold based on string length.
 */
private fun fuzzyMatch(text: String, query: String): Boolean {
    val textLower = text.lowercase()
    val queryLower = query.lowercase()
    
    // Direct substring match
    if (textLower.contains(queryLower)) return true
    
    // Word-start match
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
    val threshold = (queryLower.length * 0.4).toInt().coerceAtLeast(1)
    return levenshteinDistance(textLower, queryLower) <= threshold
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    
    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)
    
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(
                previous[j] + 1,      // deletion
                current[j - 1] + 1,   // insertion
                previous[j - 1] + cost // substitution
            )
        }
        previous.indices.forEach { previous[it] = current[it] }
    }
    return current[b.length]
}

