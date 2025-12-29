package com.wpinrui.dovora.ui.playback.nowplaying

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpinrui.dovora.ui.playback.MusicTrack
import com.wpinrui.dovora.ui.playback.components.TrackArtwork

enum class PlayerPage(val label: String) {
    NowPlaying("Now Playing"), // Only used for bottom sheet, not in tabs
    UpNext("UP NEXT"),
    Lyrics("LYRICS");

    companion object {
        val entriesList: List<PlayerPage> = entries
        // Tabs only show Up Next and Lyrics (Now Playing is accessed via miniplayer)
        val tabPages: List<PlayerPage> = listOf(UpNext, Lyrics)
    }
}

enum class RepeatMode { OFF, ALL, ONE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingScreen(
    track: MusicTrack,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPositionLabel: String,
    durationLabel: String,
    durationMs: Long,
    dominantColor: Color,
    shuffleEnabled: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit = {},
    onRepeatToggle: () -> Unit = {},
    onUpNextClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

            TrackArtwork(
                artworkPath = track.artworkPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentDescription = track.title
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Slider(
                value = playbackProgress,
                onValueChange = onSeek,
                modifier = Modifier.fillMaxWidth(),
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = playbackPositionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        ControlRow(
            isPlaying = isPlaying,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // UP NEXT and LYRICS buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onUpNextClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = "LYRICS",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onLyricsClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ControlRow(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle button
        IconButton(
            onClick = onShuffleToggle,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Outlined.Pause,
                    contentDescription = "Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Repeat button
        IconButton(
            onClick = onRepeatToggle,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                tint = when (repeatMode) {
                    RepeatMode.OFF -> Color.White.copy(alpha = 0.5f)
                    else -> Color(0xFF4CAF50)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

