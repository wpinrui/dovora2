package com.wpinrui.dovora.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.wpinrui.dovora.data.api.MetadataService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wpinrui.dovora.data.download.MediaKind
import com.wpinrui.dovora.data.model.SearchResult
import com.wpinrui.dovora.ui.auth.DefaultDownloadType

data class ExistingTrack(
    val title: String,
    val artist: String
)

@Composable
fun DownloadDialog(
    result: SearchResult,
    onDismiss: () -> Unit,
    onDownloadAudio: (title: String?, artist: String?) -> Unit,
    onDownloadVideo: (title: String?) -> Unit = {},
    existingTracks: List<ExistingTrack> = emptyList(),
    existingVideos: List<String> = emptyList(), // video titles
    aiPrefillEnabled: Boolean = true,
    defaultDownloadType: DefaultDownloadType = DefaultDownloadType.MUSIC,
    searchQuery: String? = null // Search query for LLM context
) {
    // Initialize fields based on media kind and parsed metadata
    var titleInput by remember(result.id) { mutableStateOf("") }
    var artistInput by remember(result.id) { mutableStateOf("") }
    var selectedKind by remember { 
        mutableStateOf(
            if (defaultDownloadType == DefaultDownloadType.VIDEO) MediaKind.VIDEO else MediaKind.AUDIO
        )
    }
    var isLLMLoading by remember { mutableStateOf(false) }
    var llmError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Initialize fields when dialog opens or result changes
    LaunchedEffect(result.id, selectedKind) {
        val metadata = result.parsedMetadata
        
        android.util.Log.d("DownloadDialog", "Dialog init: id=${result.id}, metadata=${metadata?.let { "title=${it.songTitle}, artist=${it.artist}" } ?: "null"}")
        
        if (selectedKind == MediaKind.AUDIO) {
            // For audio, always use crowd-sourced metadata if available
            if (metadata != null) {
                // Always use the suggestion if available, regardless of confidence
                android.util.Log.d("DownloadDialog", "PREFILLING: title=${metadata.songTitle}, artist=${metadata.artist}")
                titleInput = metadata.songTitle ?: ""
                artistInput = metadata.artist ?: ""
            } else {
                android.util.Log.d("DownloadDialog", "No metadata found for this URL")
                // No metadata available, leave empty
                titleInput = ""
                artistInput = ""
            }
        } else {
            // For video, use original title by default, but allow clearing
            titleInput = result.title
            artistInput = ""
        }
    }
    
    // Check for collision
    val effectiveTitle = titleInput.trim().takeIf { it.isNotBlank() } ?: result.title
    val effectiveArtist = artistInput.trim().takeIf { it.isNotBlank() } ?: ""
    
    val hasCollision = remember(effectiveTitle, effectiveArtist, selectedKind, existingTracks, existingVideos) {
        if (selectedKind == MediaKind.AUDIO) {
            existingTracks.any { track ->
                track.title.equals(effectiveTitle, ignoreCase = true) &&
                track.artist.equals(effectiveArtist, ignoreCase = true)
            }
        } else {
            existingVideos.any { it.equals(effectiveTitle, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Download ${result.title}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Media type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaTypeChip(
                        label = "Audio",
                        icon = Icons.Default.MusicNote,
                        selected = selectedKind == MediaKind.AUDIO,
                        onClick = { selectedKind = MediaKind.AUDIO },
                        modifier = Modifier.weight(1f)
                    )
                    MediaTypeChip(
                        label = "Video",
                        icon = Icons.Default.Videocam,
                        selected = selectedKind == MediaKind.VIDEO,
                        onClick = { selectedKind = MediaKind.VIDEO },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // File name field with clear button
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("File name (optional)") },
                    singleLine = true,
                    supportingText = { 
                        Text(
                            if (titleInput.isBlank()) 
                                "Leave empty to use video title: ${result.title}"
                            else 
                                "Used for the saved file and in your library."
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    trailingIcon = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // LLM Sparkle button (only for audio)
                            if (selectedKind == MediaKind.AUDIO) {
                                IconButton(
                                    onClick = {
                                        isLLMLoading = true
                                        llmError = null
                                        scope.launch {
                                            val youtubeUrl = "https://www.youtube.com/watch?v=${result.id}"
                                            MetadataService.getInstance()
                                                .suggestMetadataWithLLM(
                                                    youtubeUrl = youtubeUrl,
                                                    videoTitle = result.title,
                                                    searchQuery = searchQuery,
                                                    channel = result.channel
                                                )
                                                .onSuccess { metadata ->
                                                    if (metadata.songTitle != null) {
                                                        titleInput = metadata.songTitle
                                                    }
                                                    if (metadata.artist != null) {
                                                        artistInput = metadata.artist
                                                    }
                                                    isLLMLoading = false
                                                }
                                                .onFailure { error ->
                                                    llmError = error.message ?: "Failed to get AI suggestion"
                                                    isLLMLoading = false
                                                }
                                        }
                                    },
                                    modifier = Modifier.size(24.dp),
                                    enabled = !isLLMLoading
                                ) {
                                    if (isLLMLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Get AI suggestion",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            // Clear button
                            if (titleInput.isNotBlank()) {
                                IconButton(
                                    onClick = { titleInput = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Artist field only for audio with clear button and LLM sparkle
                AnimatedVisibility(visible = selectedKind == MediaKind.AUDIO) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = artistInput,
                            onValueChange = { artistInput = it },
                            label = { Text("Artist (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words
                            ),
                            trailingIcon = {
                                if (artistInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { artistInput = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // LLM Error message
                        AnimatedVisibility(visible = llmError != null) {
                            Text(
                                text = llmError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                
                // Collision warning
                AnimatedVisibility(visible = hasCollision) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (selectedKind == MediaKind.AUDIO) {
                                "A song with this name and artist already exists in your library"
                            } else {
                                "A video with this name already exists in your library"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sanitizedTitle = titleInput.trim().takeIf { it.isNotBlank() }
                    if (selectedKind == MediaKind.AUDIO) {
                        val sanitizedArtist = artistInput.trim().takeIf { it.isNotBlank() }
                        onDownloadAudio(sanitizedTitle, sanitizedArtist)
                    } else {
                        onDownloadVideo(sanitizedTitle)
                    }
                    onDismiss()
                }
            ) {
                Text(if (selectedKind == MediaKind.AUDIO) "Download audio" else "Download video")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MediaTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
