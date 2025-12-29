package com.wpinrui.dovora.ui.playback.components

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wpinrui.dovora.ui.playback.MusicTrack
import kotlinx.coroutines.delay
import java.io.File

@Composable
internal fun SongRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onRename: (MusicTrack) -> Unit,
    onEditArtist: (MusicTrack) -> Unit,
    onChangeArtwork: (MusicTrack) -> Unit,
    onDelete: (MusicTrack) -> Unit,
    showShimmer: Boolean = false,
    onShimmerComplete: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isShimmering by remember { mutableStateOf(showShimmer) }
    
    // Auto-stop shimmer after animation completes
    LaunchedEffect(showShimmer) {
        if (showShimmer) {
            isShimmering = true
            delay(1500) // Shimmer duration
            isShimmering = false
            onShimmerComplete()
        }
    }
    
    val shimmerModifier = if (isShimmering) {
        Modifier.shimmerEffect()
    } else {
        Modifier
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(shimmerModifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            artworkPath = track.artworkPath,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${track.durationLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                offset = DpOffset((-8).dp, 0.dp),
                modifier = Modifier
                    .background(
                        color = Color(0xFF1E1E2E),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                SongMenuItem(
                    icon = Icons.Default.Edit,
                    text = "Rename",
                    onClick = {
                        menuExpanded = false
                        onRename(track)
                    }
                )
                SongMenuItem(
                    icon = Icons.Default.Person,
                    text = "Edit artist",
                    onClick = {
                        menuExpanded = false
                        onEditArtist(track)
                    }
                )
                SongMenuItem(
                    icon = Icons.Default.Image,
                    text = "Change artwork",
                    onClick = {
                        menuExpanded = false
                        onChangeArtwork(track)
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
                SongMenuItem(
                    icon = Icons.Default.Delete,
                    text = "Delete",
                    tint = Color(0xFFEF5350),
                    onClick = {
                        menuExpanded = false
                        onDelete(track)
                    }
                )
            }
        }
    }
}

@Composable
private fun SongMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint
        )
    }
}

@Composable
private fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    return this.drawWithContent {
        drawContent()
        
        val shimmerWidth = size.width * 0.4f
        val startX = translateAnim * size.width
        
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.3f),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + shimmerWidth, size.height)
            )
        )
    }
}

@Composable
fun TrackArtwork(
    artworkPath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    var showPlaceholder = artworkPath == null
    
    if (artworkPath != null) {
        // Normalize path separators for cross-platform compatibility
        val normalizedPath = artworkPath.replace('\\', '/')
        val file = File(normalizedPath)
        
        if (file.exists()) {
            Log.d("TrackArtwork", "Loading artwork from: $normalizedPath (size: ${file.length()} bytes)")
            
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { error ->
                        Log.e("TrackArtwork", "Failed to load artwork: ${error.result.throwable.message}")
                    }
                )
            }
            return
        } else {
            Log.w("TrackArtwork", "Artwork file does not exist: $normalizedPath")
            showPlaceholder = true
        }
    }
    
    if (showPlaceholder) {
        PlaceholderArtwork(modifier = modifier)
    }
}

@Composable
internal fun PlaceholderArtwork(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
