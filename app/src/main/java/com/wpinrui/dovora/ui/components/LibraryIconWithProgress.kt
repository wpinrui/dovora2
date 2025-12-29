package com.wpinrui.dovora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Library icon with a badge that shows:
 * - A "pizza" progress indicator (white to green sweep) when downloads are in progress
 * - A red badge with count when new songs are ready
 * - Red takes priority if both are present
 */
@Composable
fun LibraryIconWithProgress(
    downloadProgress: Float, // 0.0 to 1.0, overall download progress
    activeDownloads: Int,    // number of downloads in progress
    readyCount: Int,         // number of newly completed songs ready to view
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val baseColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
    val showBadge = activeDownloads > 0 || readyCount > 0

    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = "Library",
            tint = baseColor,
            modifier = Modifier.size(size)
        )

        if (showBadge) {
            val badgeSize = 16.dp
            
            if (readyCount > 0) {
                // Red badge with count - takes priority
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (readyCount > 9) "9+" else readyCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 9.sp
                    )
                }
            } else if (activeDownloads > 0) {
                // Pizza progress indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(badgeSize),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(badgeSize)) {
                        val strokeWidth = 3.dp.toPx()
                        val radius = (this.size.minDimension - strokeWidth) / 2
                        
                        // Background circle (dark/unfilled)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = radius,
                            style = Stroke(width = strokeWidth)
                        )
                        
                        // Progress arc (green sweep)
                        val sweepAngle = downloadProgress * 360f
                        drawArc(
                            color = Color(0xFF4CAF50), // Green
                            startAngle = -90f, // Start from top
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }
            }
        }
    }
}
