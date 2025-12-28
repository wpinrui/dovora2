package com.wpinrui.dovora.ui.playback

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.palette.graphics.Palette
import androidx.lifecycle.viewModelScope
import com.wpinrui.dovora.data.api.LyricsService
import com.wpinrui.dovora.data.api.LyricsStatus
import com.wpinrui.dovora.data.download.DownloadStorage
import com.wpinrui.dovora.data.download.TrackMetadataStore
import com.wpinrui.dovora.ui.playback.nowplaying.PlayerPage
import com.wpinrui.dovora.ui.playback.nowplaying.RepeatMode
import com.wpinrui.dovora.ui.playback.service.PlaybackServiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MusicPlaybackViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MusicPlaybackUiState())
    val uiState: StateFlow<MusicPlaybackUiState> = _uiState.asStateFlow()
    private val playbackConnection = PlaybackServiceConnection(application)
    private val lyricsService = LyricsService()
    // TODO: Replace with backend API when implementing issue #29
    // private val librarySync = LibrarySyncService.getInstance(application)
    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    private val lyricsCache = mutableMapOf<String, LyricsUiState>()
    private val _playerPage = MutableStateFlow(PlayerPage.NowPlaying)
    val playerPage: StateFlow<PlayerPage> = _playerPage.asStateFlow()
    
    private val _dominantColor = MutableStateFlow(Color(0xFF1A1A1A))
    val dominantColor: StateFlow<Color> = _dominantColor.asStateFlow()
    
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var lastManualQueueUpdateMs = 0L

    init {
        refreshDownloads()
        observePlaybackUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        playbackConnection.release()
    }

    fun refreshDownloads() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val files = loadAudioFiles()
            var queueToSync: List<MusicTrack>? = null
            _uiState.update { current ->
                val currentTrack = current.currentTrack?.let { track ->
                    files.find { it.id == track.id }
                }
                val preservedQueue = current.upNext.mapNotNull { queued ->
                    files.find { it.id == queued.id }
                }
                val shouldSeedQueue = currentTrack != null && !current.queueInitialized
                val nextQueue = when {
                    shouldSeedQueue -> buildShuffledQueue(files, currentTrack?.id)
                    current.queueInitialized -> preservedQueue
                    else -> emptyList()
                }
                val queueNeedsSync = shouldSeedQueue ||
                    (current.queueInitialized && nextQueue != current.upNext)
                if (queueNeedsSync) {
                    queueToSync = nextQueue
                }
                current.copy(
                    library = files,
                    isRefreshing = false,
                    currentTrack = currentTrack,
                    upNext = nextQueue,
                    queueInitialized = current.queueInitialized || shouldSeedQueue
                )
            }
            queueToSync?.let { playbackConnection.setQueue(it) }
        }
    }

    fun selectTrack(track: MusicTrack) {
        val librarySnapshot = _uiState.value.library
        val newQueue = buildShuffledQueue(librarySnapshot, track.id)
        playbackConnection.setQueue(newQueue)
        resetLyricsState()
        updateDominantColor(track)

        // Build initial fullQueue: track + upcoming
        val initialFullQueue = listOf(track) + newQueue

        _uiState.update {
            it.copy(
                currentTrack = track,
                upNext = newQueue,
                fullQueue = initialFullQueue,
                currentQueueIndex = 0,
                isPlaying = true,
                playbackPositionMillis = 0L,
                playbackDurationMillis = track.durationMs,
                queueInitialized = true
            )
        }
        playbackConnection.play(track)
    }

    fun exitNowPlaying() {
        _uiState.update {
            it.copy(
                currentTrack = null,
                isPlaying = false,
                playbackPositionMillis = 0L,
                playbackDurationMillis = 0L,
                upNext = emptyList()
            )
        }
        playbackConnection.pause()
    }

    fun togglePlayback() {
        val hasTrack = uiState.value.currentTrack != null
        if (!hasTrack) return
        if (uiState.value.isPlaying) {
            playbackConnection.pause()
        } else {
            playbackConnection.play()
        }
    }

    fun seekTo(fraction: Float) {
        playbackConnection.seekToFraction(fraction)
    }

    fun skipToNext() {
        playbackConnection.playNextFromQueue()
        resetLyricsState()
    }

    fun skipToPrevious() {
        playbackConnection.playPrevious()
        resetLyricsState()
    }

    fun shuffleUpNext() {
        val current = _uiState.value
        val referenceTrack = current.currentTrack ?: return
        val baseQueue = if (current.queueInitialized && current.upNext.isNotEmpty()) {
            current.upNext
        } else {
            buildShuffledQueue(current.library, referenceTrack.id)
        }
        val shuffled = baseQueue.shuffled()
        playbackConnection.setQueue(shuffled)
        _uiState.update {
            it.copy(upNext = shuffled, queueInitialized = true)
        }
    }

    fun renameTrack(track: MusicTrack, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val trimmed = newTitle.trim()
            withContext(Dispatchers.IO) {
                val audioFile = File(track.absolutePath)
                TrackMetadataStore.updateMetadata(audioFile, title = trimmed)
            }
            // TODO: Sync rename to backend API when implementing issue #29
            refreshDownloads()
        }
    }

    fun updateTrackArtist(track: MusicTrack, newArtist: String) {
        if (newArtist.isBlank()) return
        viewModelScope.launch {
            val trimmed = newArtist.trim()
            withContext(Dispatchers.IO) {
                val audioFile = File(track.absolutePath)
                TrackMetadataStore.updateMetadata(audioFile, artist = trimmed)
            }
            // TODO: Sync artist update to backend API when implementing issue #29
            refreshDownloads()
        }
    }

    fun updateTrackArtwork(track: MusicTrack, imageUri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val thumbnailsDir = DownloadStorage.thumbnailDirectory(context)
                    val extension = resolveExtension(context, imageUri)
                    val baseName = sanitizeFileName("${track.title}-${System.currentTimeMillis()}")
                    val destination = File(thumbnailsDir, "$baseName.$extension")

                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Unable to open selected image")

                    TrackMetadataStore.updateMetadata(
                        audioFile = File(track.absolutePath),
                        thumbnailPath = destination.absolutePath
                    )
                    destination.absolutePath
                }
            }
            result.onSuccess {
                // TODO: Sync artwork update to backend API when implementing issue #29
                refreshDownloads()
            }.onFailure { error ->
                Log.e("MusicPlaybackViewModel", "Failed to update artwork", error)
            }
        }
    }

    fun fetchLyricsForCurrentTrack() {
        val track = _uiState.value.currentTrack ?: return
        lyricsCache[track.id]?.let {
            _lyricsState.value = it
            return
        }
        if (_lyricsState.value.isLoading) return

        viewModelScope.launch {
            _lyricsState.value = LyricsUiState(isLoading = true)
            lyricsService.fetchLyrics(track.artist, track.title)
                .onSuccess { result ->
                    val newState = LyricsUiState(
                        lyrics = result.lyrics,
                        source = result.source,
                        url = result.url,
                        noLyricsAvailable = result.status == LyricsStatus.NO_LYRICS
                    )
                    _lyricsState.value = newState
                    lyricsCache[track.id] = newState
                }
                .onFailure { error ->
                    _lyricsState.value = LyricsUiState(
                        errorMessage = error.message ?: "Failed to load lyrics"
                    )
                }
        }
    }

    fun deleteTrack(track: MusicTrack) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(track.absolutePath).takeIf { it.exists() }?.delete()
                TrackMetadataStore.deleteMetadata(File(track.absolutePath))
                track.artworkPath?.let { path ->
                    File(path).takeIf { it.exists() }?.delete()
                }
            }

            // TODO: Sync deletion to backend API when implementing issue #29

            val isCurrent = _uiState.value.currentTrack?.id == track.id
            if (isCurrent) {
                exitNowPlaying()
            } else {
                _uiState.update {
                    it.copy(upNext = it.upNext.filterNot { queued -> queued.id == track.id })
                }
            }
            lyricsCache.remove(track.id)
            refreshDownloads()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value
        val fullQueue = current.fullQueue
        val currentIndex = current.currentQueueIndex
        
        // Validate indices
        if (fromIndex !in fullQueue.indices) return
        if (toIndex !in fullQueue.indices) return
        if (fromIndex == toIndex) return
        
        // Move item in full queue
        val mutableFullQueue = fullQueue.toMutableList()
        val item = mutableFullQueue.removeAt(fromIndex)
        
        // Insert at target position - no adjustment needed
        // toIndex represents where the item should END UP in the final list
        mutableFullQueue.add(toIndex, item)
        
        // Update current index if it was affected by the move
        val newCurrentIndex = when {
            fromIndex == currentIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        
        // Rebuild history and upNext from full queue
        val currentTrack = mutableFullQueue.getOrNull(newCurrentIndex) ?: current.currentTrack
        val history = mutableFullQueue.take(newCurrentIndex)
        val upNext = mutableFullQueue.drop(newCurrentIndex + 1)
        
        lastManualQueueUpdateMs = System.currentTimeMillis()
        
        // Update service
        playbackConnection.setHistory(history)
        playbackConnection.setQueue(upNext)
        
        // Update UI state
        _uiState.update {
            it.copy(
                fullQueue = mutableFullQueue,
                currentQueueIndex = newCurrentIndex,
                currentTrack = currentTrack,
                upNext = upNext
            )
        }
    }

    fun playQueuedTrack(track: MusicTrack) {
        // Simply play the track - no queue manipulation
        // The queue structure remains the same, only current index changes
        playbackConnection.play(track)
        resetLyricsState()
        updateDominantColor(track)
        
        _uiState.update {
            val newIndex = it.fullQueue.indexOfFirst { t -> t.id == track.id }
            it.copy(
                currentTrack = track,
                currentQueueIndex = newIndex.coerceAtLeast(0),
                isPlaying = true,
                playbackDurationMillis = track.durationMs,
                playbackPositionMillis = 0L
            )
        }
    }

    fun setPlayerPage(page: PlayerPage) {
        _playerPage.value = page
    }
    
    fun toggleShuffle() {
        val newValue = !_shuffleEnabled.value
        _shuffleEnabled.value = newValue
        if (newValue) {
            // When enabling shuffle, reshuffle the queue
            shuffleUpNext()
        }
    }
    
    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }
    
    fun deleteQueueItem(track: MusicTrack) {
        val current = _uiState.value
        if (track.id == current.currentTrack?.id) {
            // Don't delete the currently playing track
            return
        }
        
        // Remove from full queue
        val updatedFullQueue = current.fullQueue.filterNot { it.id == track.id }
        val currentIndex = current.currentQueueIndex
        val currentTrack = current.currentTrack
        
        // Rebuild history and upNext from updated full queue
        val newCurrentIndex = updatedFullQueue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        val history = updatedFullQueue.take(newCurrentIndex)
        val upNext = updatedFullQueue.drop(newCurrentIndex + 1)
        
        lastManualQueueUpdateMs = System.currentTimeMillis()
        
        // Update service
        playbackConnection.setHistory(history)
        playbackConnection.setQueue(upNext)
        
        // Update UI state
        _uiState.update {
            it.copy(
                fullQueue = updatedFullQueue,
                currentQueueIndex = newCurrentIndex,
                upNext = upNext
            )
        }
    }

    private fun buildShuffledQueue(
        library: List<MusicTrack>,
        excludeId: String?
    ): List<MusicTrack> =
        library.filter { it.id != excludeId }.shuffled()

    private suspend fun loadAudioFiles(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val directory = DownloadStorage.audioDirectory(getApplication())
        directory
            .listFiles()
            ?.filter { file ->
                file.isFile && !file.name.endsWith(TrackMetadataStore.METADATA_FILE_SUFFIX)
            }
            ?.map { it.toMusicTrack() }
            ?.sortedByDescending { it.addedTimestamp }
            ?: emptyList()
    }

    private fun observePlaybackUpdates() {
        viewModelScope.launch {
            playbackConnection.service.collectLatest { service ->
                service ?: return@collectLatest
                service.playbackState.collect { snapshot ->
                    _uiState.update { current ->
                        val activeTrack = snapshot.currentTrack ?: current.currentTrack
                        val trackChanged = activeTrack?.id != current.currentTrack?.id
                        val serviceQueue = snapshot.queue
                        val serviceHistory = snapshot.history
                        
                        // Only sync queue from service when track changes (natural progression/auto-play)
                        // BUT: ignore for 500ms after manual queue update to prevent feedback loop
                        val timeSinceManualUpdate = System.currentTimeMillis() - lastManualQueueUpdateMs
                        val recentManualUpdate = timeSinceManualUpdate < 500
                        
                        val updatedQueue = if (trackChanged && serviceQueue.isNotEmpty() && !recentManualUpdate) {
                            Log.d("DRAG_DEBUG", "VM Observer: Track changed, syncing queue from service")
                            Log.d("DRAG_DEBUG", "VM Observer: Service queue: ${serviceQueue.map { it.title }}")
                            serviceQueue
                        } else {
                            if (recentManualUpdate) {
                                Log.d("DRAG_DEBUG", "VM Observer: Ignoring service queue (${timeSinceManualUpdate}ms since manual update)")
                            }
                            current.upNext
                        }
                        
                        // Keep full queue stable - only update current index when track changes
                        // The full queue should only be rebuilt when explicitly changed (selectTrack, moveQueueItem)
                        val fullQueue = if (current.fullQueue.isEmpty() && activeTrack != null) {
                            // Initial build: history (reversed) + current + upcoming
                            val historyReversed = serviceHistory.reversed()
                            historyReversed + activeTrack + updatedQueue
                        } else {
                            // Keep existing fullQueue stable
                            current.fullQueue
                        }
                        
                        // Find current track index in fullQueue
                        // If track changed naturally (song ended), find the new track in existing queue
                        val currentQueueIndex = if (activeTrack != null && fullQueue.isNotEmpty()) {
                            val index = fullQueue.indexOfFirst { it.id == activeTrack.id }
                            if (index >= 0) {
                                index
                            } else {
                                // Track not found - this shouldn't happen, but fallback to current index
                                current.currentQueueIndex
                            }
                        } else {
                            -1
                        }
                        
                        val updatedState = current.copy(
                            currentTrack = activeTrack,
                            isPlaying = snapshot.isPlaying && activeTrack != null,
                            playbackPositionMillis = snapshot.positionMs,
                            playbackDurationMillis = snapshot.durationMs.takeIf { it > 0 }
                                ?: activeTrack?.durationMs
                                ?: current.playbackDurationMillis,
                            upNext = updatedQueue,
                            fullQueue = fullQueue,
                            currentQueueIndex = currentQueueIndex
                        )
                        if (trackChanged) {
                            resetLyricsState()
                            updateDominantColor(activeTrack)
                        }
                        updatedState
                    }
                }
            }
        }
    }

    private fun resetLyricsState() {
        _lyricsState.value = LyricsUiState()
    }
    
    private fun updateDominantColor(track: MusicTrack?) {
        viewModelScope.launch {
            val color = withContext(Dispatchers.IO) {
                extractDominantColor(track?.artworkPath)
            }
            _dominantColor.value = color ?: Color(0xFF1A1A1A)
        }
    }
    
    private fun extractDominantColor(artworkPath: String?): Color? {
        if (artworkPath == null) return null
        return try {
            val file = File(artworkPath.replace('\\', '/'))
            if (!file.exists()) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val palette = Palette.from(bitmap).generate()
            val swatch = palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: palette.mutedSwatch
            swatch?.let { ensureDarkEnough(Color(it.rgb)) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Ensures the color is dark enough for white text readability.
     * If the color is too bright, it darkens it while preserving the hue.
     */
    private fun ensureDarkEnough(color: Color): Color {
        val maxLuminance = 0.3f // Maximum allowed luminance for good white text contrast
        
        // Convert to HSL-like representation using Android's ColorUtils
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(
            android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            ),
            hsl
        )
        
        // If luminance is too high, reduce it
        if (hsl[2] > maxLuminance) {
            hsl[2] = maxLuminance
        }
        
        // Also reduce saturation slightly for very saturated bright colors
        // to make them feel more premium/muted
        if (hsl[1] > 0.7f && hsl[2] > 0.2f) {
            hsl[1] = hsl[1] * 0.8f
        }
        
        val darkened = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
        return Color(darkened)
    }
}

data class MusicPlaybackUiState(
    val library: List<MusicTrack> = emptyList(),
    val upNext: List<MusicTrack> = emptyList(),
    val fullQueue: List<MusicTrack> = emptyList(), // History + Current + Upcoming
    val currentQueueIndex: Int = -1, // Index of current track in fullQueue
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isRefreshing: Boolean = false,
    val playbackPositionMillis: Long = 0L,
    val playbackDurationMillis: Long = 0L,
    val queueInitialized: Boolean = false
) {
    val playbackProgress: Float
        get() {
            val duration = currentTrack?.durationMs ?: playbackDurationMillis
            if (duration <= 0) return 0f
            return (playbackPositionMillis.toFloat() / duration).coerceIn(0f, 1f)
        }

    val effectiveDurationMs: Long
        get() = currentTrack?.durationMs?.takeIf { it > 0 } ?: playbackDurationMillis
}

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val absolutePath: String,
    val addedTimestamp: Long,
    val artworkPath: String? = null
) {
    val durationLabel: String
        get() = if (durationMs <= 0) {
            "—:—"
        } else {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = (totalSeconds % 60).toInt()
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
}

data class LyricsUiState(
    val lyrics: String? = null,
    val source: String? = null,
    val url: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val noLyricsAvailable: Boolean = false
)

private fun File.toMusicTrack(): MusicTrack {
    val retriever = MediaMetadataRetriever()
    var duration = 0L
    var title = nameWithoutExtension.ifBlank { name }
    var artist = "Unknown artist"
    var artworkPath: String? = null
    try {
        retriever.setDataSource(absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.let { duration = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?.let { title = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?.let { artist = it }
    } catch (_: Exception) {
        // fall back to defaults
    } finally {
        retriever.release()
    }
    TrackMetadataStore.readMetadata(this)?.let { metadata ->
        metadata.title?.takeIf { it.isNotBlank() }?.let { title = it }
        metadata.artist?.takeIf { it.isNotBlank() }?.let { artist = it }
        metadata.thumbnailPath?.takeIf { it.isNotBlank() }?.let { artworkPath = it }
    }

    val track = MusicTrack(
        id = absolutePath,
        title = title,
        artist = artist,
        durationMs = duration,
        absolutePath = absolutePath,
        addedTimestamp = lastModified(),
        artworkPath = artworkPath
    )
    Log.d("MusicTrack", "Created track: $title, artworkPath: $artworkPath")
    return track
}

private fun resolveExtension(context: Application, uri: Uri): String {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)
    val mimeTypeMap = MimeTypeMap.getSingleton()
    return mimeTypeMap.getExtensionFromMimeType(mime)?.lowercase()
        ?: when {
            mime?.contains("png", ignoreCase = true) == true -> "png"
            mime?.contains("webp", ignoreCase = true) == true -> "webp"
            else -> "jpg"
        }
}

private fun sanitizeFileName(value: String): String =
    value.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)

