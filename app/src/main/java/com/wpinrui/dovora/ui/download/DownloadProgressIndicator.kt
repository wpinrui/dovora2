package com.wpinrui.dovora.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpinrui.dovora.data.download.ActiveDownload
import com.wpinrui.dovora.data.download.DownloadProgress
import com.wpinrui.dovora.data.download.DownloadState

@Composable
fun DownloadProgressIndicator(
    progress: DownloadProgress?,
    modifier: Modifier = Modifier
) {
    if (progress != null && progress.progress >= 0) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progress.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (progress.progress < 100) {
                        Text(
                            text = "${progress.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                LinearProgressIndicator(
                    progress = { progress.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else if (progress != null && progress.progress == -1) {
        // Error state
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Shows active downloads as a floating indicator.
 * Supports multiple concurrent downloads.
 */
@Composable
fun ActiveDownloadsIndicator(
    downloads: Map<String, ActiveDownload>,
    onDismiss: (String) -> Unit,
    onRetry: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = downloads.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            downloads.values.forEach { download ->
                DownloadItem(
                    download = download,
                    onDismiss = { onDismiss(download.id) },
                    onRetry = { onRetry(download.id) }
                )
            }
        }
    }
}

@Composable
private fun DownloadItem(
    download: ActiveDownload,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val backgroundColor = when (download.state) {
        is DownloadState.Failed -> Color(0xFF2D1F1F)
        is DownloadState.Completed -> Color(0xFF1F2D1F)
        else -> Color.White.copy(alpha = 0.1f)
    }
    
    val statusColor = when (download.state) {
        is DownloadState.Failed -> Color(0xFFCF6679)
        is DownloadState.Completed -> Color(0xFF81C784)
        else -> Color.White.copy(alpha = 0.7f)
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
            // Progress indicator or status icon
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                when (download.state) {
                    is DownloadState.Completed -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is DownloadState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failed",
                            tint = Color(0xFFCF6679),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "${download.progress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Title and status
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (download.state) {
                        is DownloadState.Preparing -> "Preparing..."
                        is DownloadState.Downloading -> "Downloading..."
                        is DownloadState.Finalizing -> "Finalizing..."
                        is DownloadState.Completed -> "Complete"
                        is DownloadState.Failed -> download.state.message.removePrefix("Error: ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Retry button for failed downloads
            if (download.state is DownloadState.Failed) {
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
            }
            
            // Dismiss button for completed/failed
            if (download.state is DownloadState.Completed || download.state is DownloadState.Failed) {
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
