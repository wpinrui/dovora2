package com.wpinrui.dovora.ui.playback.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wpinrui.dovora.ui.playback.LyricsUiState
import com.wpinrui.dovora.ui.playback.MusicTrack

@Composable
internal fun LyricsScreen(
    track: MusicTrack,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPositionLabel: String,
    durationLabel: String,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    lyricsState: LyricsUiState,
    onFetchLyrics: () -> Unit
) {
    // Auto-fetch lyrics when screen is shown and lyrics not loaded
    LaunchedEffect(track.id) {
        if (!lyricsState.isLoading && lyricsState.lyrics == null && !lyricsState.noLyricsAvailable && lyricsState.errorMessage == null) {
            onFetchLyrics()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopStart
        ) {
            when {
                lyricsState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                lyricsState.lyrics != null -> {
                    val formattedLyrics = formatLyrics(lyricsState.lyrics)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = formattedLyrics,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(150.dp))
                    }
                }
                lyricsState.noLyricsAvailable -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No lyrics available for this song",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                lyricsState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = lyricsState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF6B6B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onFetchLyrics,
                            enabled = !lyricsState.isLoading
                        ) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Lyrics not loaded",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onFetchLyrics,
                            enabled = !lyricsState.isLoading
                        ) {
                            Text("Fetch Lyrics")
                        }
                    }
                }
            }
        }
    }
}

private fun formatLyrics(rawLyrics: String): String {
    val startIndex = rawLyrics.indexOf('[')
    val relevantLyrics = if (startIndex >= 0) {
        rawLyrics.substring(startIndex)
    } else {
        rawLyrics
    }

    val headingRegex = Regex("\\[(.+?)]")
    val matches = headingRegex.findAll(relevantLyrics).toList()

    if (matches.isEmpty()) {
        return normalizeWhitespace(relevantLyrics)
    }

    val sections = matches.mapIndexed { index, matchResult ->
        val sectionStart = matchResult.range.last + 1
        val sectionEnd = if (index < matches.lastIndex) {
            matches[index + 1].range.first
        } else {
            relevantLyrics.length
        }

        relevantLyrics.substring(sectionStart, sectionEnd)
    }
        .map { normalizeWhitespace(it) }
        .filter { it.isNotBlank() }

    return if (sections.isNotEmpty()) {
        sections.joinToString(separator = "\n\n")
    } else {
        normalizeWhitespace(relevantLyrics)
    }
}

private fun normalizeWhitespace(section: String): String {
    val collapsedNewlines = section
        .replace("\r", "")
        .replace(Regex("\n{2,}"), "\n")
        .trim()

    return collapsedNewlines
}

