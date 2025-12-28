package com.wpinrui.dovora.ui.playback.nowplaying

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wpinrui.dovora.ui.playback.MusicTrack
import com.wpinrui.dovora.ui.playback.components.TrackArtwork
import kotlinx.coroutines.launch

@Composable
internal fun UpNextScreen(
    track: MusicTrack,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPositionLabel: String,
    durationLabel: String,
    durationMs: Long,
    fullQueue: List<MusicTrack>,
    currentQueueIndex: Int,
    currentTrackHighlightColor: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onQueueItemClick: (MusicTrack) -> Unit,
    onShuffleUpNext: () -> Unit,
    onDeleteQueueItem: ((MusicTrack) -> Unit)? = null,
    getCurrentQueue: () -> List<MusicTrack> = { fullQueue }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${fullQueue.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            TextButton(
                onClick = onShuffleUpNext,
                enabled = fullQueue.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reshuffle", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            contentAlignment = if (fullQueue.isEmpty()) Alignment.Center else Alignment.TopStart
        ) {
            if (fullQueue.isEmpty()) {
                Text(
                    text = "Queue will appear here after you pick a song.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                                UpNextList(
                                    modifier = Modifier.fillMaxSize(),
                                    queue = fullQueue,
                                    currentTrackId = track.id,
                                    currentQueueIndex = currentQueueIndex,
                                    currentTrackHighlightColor = currentTrackHighlightColor,
                                    onMoveQueueItem = onMoveQueueItem,
                                    onQueueItemClick = onQueueItemClick,
                                    onDeleteQueueItem = onDeleteQueueItem,
                                    getCurrentQueue = getCurrentQueue
                                )
            }
        }
    }
}

@Composable
private fun UpNextList(
    modifier: Modifier = Modifier,
    queue: List<MusicTrack>,
    currentTrackId: String,
    currentQueueIndex: Int,
    currentTrackHighlightColor: Color,
    onMoveQueueItem: (Int, Int) -> Unit,
    onQueueItemClick: (MusicTrack) -> Unit,
    onDeleteQueueItem: ((MusicTrack) -> Unit)? = null,
    getCurrentQueue: () -> List<MusicTrack> = { queue }
) {
    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val draggedIndex = if (draggedTrackId != null) {
        queue.indexOfFirst { it.id == draggedTrackId }
    } else {
        -1
    }

    val targetIndex = if (draggedIndex >= 0 && itemHeightPx > 0f) {
        // Use asymmetric threshold: 20% to move up, 70% to move down
        // This makes it easier to drag items upward
        val threshold = if (dragOffset < 0) 0.2f else 0.7f
        val positions = ((dragOffset / itemHeightPx) + threshold).toInt()
        (draggedIndex + positions).coerceIn(0, queue.lastIndex)
    } else {
        -1
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        itemsIndexed(queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
            val isDragging = draggedTrackId == track.id

            if (itemHeightPx == 0f && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                if (firstItem != null) {
                    itemHeightPx = firstItem.size.toFloat() + with(density) { 6.dp.toPx() }
                }
            }

            val visualIndex = if (draggedTrackId == null || isDragging) {
                index
            } else if (draggedIndex >= 0 && targetIndex >= 0) {
                val indexWithoutDragged = if (index > draggedIndex) index - 1 else index
                if (indexWithoutDragged >= targetIndex) {
                    indexWithoutDragged + 1
                } else {
                    indexWithoutDragged
                }
            } else {
                index
            }

            val displacement = remember(track.id) { Animatable(0f) }
            val targetOffset = if (visualIndex >= 0 && itemHeightPx > 0f) {
                (visualIndex - index) * itemHeightPx
            } else {
                0f
            }

            LaunchedEffect(targetOffset) {
                if (targetOffset != displacement.value) {
                    if (targetOffset == 0f) {
                        displacement.snapTo(0f)
                    } else {
                        displacement.animateTo(
                            targetOffset,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh
                            )
                        )
                    }
                }
            }

            val finalOffset = if (isDragging) {
                dragOffset
            } else {
                displacement.value
            }

            UpNextListItem(
                track = track,
                isDragging = isDragging,
                offset = finalOffset,
                isCurrentTrack = index == currentQueueIndex,
                currentTrackHighlightColor = currentTrackHighlightColor,
                onDragStart = {
                    draggedTrackId = track.id
                    dragOffset = 0f
                    val currentQueue = getCurrentQueue()
                    val actualCurrentIndex = currentQueue.indexOfFirst { it.id == track.id }
                    Log.d("DRAG_DEBUG", "=== DRAG START ===")
                    Log.d("DRAG_DEBUG", "Track: ${track.title}")
                    Log.d("DRAG_DEBUG", "Param Index: $index, Actual Queue Index: $actualCurrentIndex")
                    Log.d("DRAG_DEBUG", "Current Queue: ${currentQueue.map { it.title }}")
                },
                onDrag = { delta ->
                    if (draggedTrackId == track.id && itemHeightPx > 0f) {
                        dragOffset += delta
                        val threshold = if (dragOffset < 0) 0.2f else 0.7f
                        val positions = ((dragOffset / itemHeightPx) + threshold).toInt()
                        Log.d(
                            "DRAG_DEBUG",
                            "delta=$delta, dragOffset=$dragOffset, positions=$positions, targetIndex=$targetIndex"
                        )

                        if (targetIndex >= 0) {
                            scope.launch {
                                val layoutInfo = listState.layoutInfo
                                if (layoutInfo.visibleItemsInfo.isEmpty()) return@launch

                                val firstVisible = layoutInfo.visibleItemsInfo.first()
                                val lastVisible = layoutInfo.visibleItemsInfo.last()

                                val scrollAmount = when {
                                    targetIndex <= firstVisible.index && targetIndex > 0 -> {
                                        -itemHeightPx * 0.5f
                                    }
                                    targetIndex >= lastVisible.index && targetIndex < queue.lastIndex -> {
                                        itemHeightPx * 0.5f
                                    }
                                    else -> 0f
                                }

                                if (scrollAmount != 0f) {
                                    listState.scroll {
                                        scrollBy(scrollAmount)
                                    }
                                }
                            }
                        }
                    }
                },
                onDragEnd = {
                    if (draggedTrackId == track.id && itemHeightPx > 0f) {
                        val currentQueue = getCurrentQueue()
                        val oldIndex = currentQueue.indexOfFirst { it.id == track.id }

                        if (oldIndex >= 0) {
                            val threshold = if (dragOffset < 0) 0.2f else 0.7f
                            val positions = ((dragOffset / itemHeightPx) + threshold).toInt()
                            val newIndex = (oldIndex + positions).coerceIn(0, currentQueue.lastIndex)

                            Log.d("DRAG_DEBUG", "=== DRAG END ===")
                            Log.d("DRAG_DEBUG", "Track: ${track.title}")
                            Log.d("DRAG_DEBUG", "Drag Offset: $dragOffset")
                            Log.d("DRAG_DEBUG", "Item Height: $itemHeightPx")
                            Log.d("DRAG_DEBUG", "Positions Moved: $positions")
                            Log.d("DRAG_DEBUG", "OLD Index: $oldIndex")
                            Log.d("DRAG_DEBUG", "NEW Index: $newIndex")
                            Log.d("DRAG_DEBUG", "Queue: ${currentQueue.map { it.title }}")

                            if (newIndex != oldIndex) {
                                Log.d("DRAG_DEBUG", "Moving from $oldIndex to $newIndex")
                                onMoveQueueItem(oldIndex, newIndex)
                            } else {
                                Log.d("DRAG_DEBUG", "No move needed (same index)")
                            }
                        }
                    }

                    draggedTrackId = null
                    dragOffset = 0f
                },
                onSelect = { onQueueItemClick(track) },
                onDelete = { if (track.id != currentTrackId && onDeleteQueueItem != null) onDeleteQueueItem(track) }
            )
        }
    }
}

@Composable
private fun UpNextListItem(
    track: MusicTrack,
    isDragging: Boolean,
    offset: Float,
    isCurrentTrack: Boolean,
    currentTrackHighlightColor: Color,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var horizontalSwipeOffset by remember { mutableFloatStateOf(0f) }
    var isSwipeDeleting by remember { mutableStateOf(false) }
    val swipeThreshold = 200f // pixels to swipe before deleting
    
    // Animate deletion
    LaunchedEffect(isSwipeDeleting) {
        if (isSwipeDeleting) {
            horizontalSwipeOffset = -1000f // Animate off screen
            kotlinx.coroutines.delay(300) // Wait for animation
            onDelete()
        }
    }
    
    Box(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offset
                translationX = horizontalSwipeOffset
                shadowElevation = if (isDragging) 8f else 0f
                alpha = if (isSwipeDeleting) 0f else 1f
            }
            .zIndex(if (isDragging || isSwipeDeleting) 1f else 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isCurrentTrack) {
                        // Use the brightened highlight color for current track
                        currentTrackHighlightColor
                    } else if (isDragging) {
                        Color.White.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrackArtwork(
                artworkPath = track.artworkPath,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentDescription = track.title
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
                    .pointerInput(track.id, isCurrentTrack) {
                        if (!isCurrentTrack) {
                            // Horizontal swipe for delete
                            detectHorizontalDragGestures(
                                onDragStart = { /* Don't interfere with vertical drag */ },
                                onDragEnd = {
                                    if (horizontalSwipeOffset < -swipeThreshold) {
                                        isSwipeDeleting = true
                                    } else {
                                        horizontalSwipeOffset = 0f
                                    }
                                },
                                onDragCancel = {
                                    horizontalSwipeOffset = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount < 0) { // Only allow left swipe
                                        horizontalSwipeOffset = (horizontalSwipeOffset + dragAmount).coerceAtMost(0f)
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(track.id) {
                        // Vertical drag for reordering
                        detectDragGestures(
                            onDragStart = {
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )
        }
    }
}

