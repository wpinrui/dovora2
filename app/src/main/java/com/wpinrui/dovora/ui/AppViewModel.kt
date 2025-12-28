package com.wpinrui.dovora.ui

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.wpinrui.dovora.data.download.DownloadStorage
import com.wpinrui.dovora.data.download.MediaKind
import com.wpinrui.dovora.data.download.VideoMetadataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Represents the current mode of the app - Music or Video
 */
enum class AppMode {
    MUSIC, VIDEO
}

private const val PREFS_NAME = "dovora_prefs"
private const val KEY_APP_MODE = "app_mode"

/**
 * App-level ViewModel that manages global state like the current mode (Music/Video)
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // TODO: Replace with backend API when implementing issue #38
    // private val librarySync = LibrarySyncService.getInstance(application)
    
    private val _appMode = MutableStateFlow(loadSavedAppMode())
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()
    
    private fun loadSavedAppMode(): AppMode {
        val savedMode = prefs.getString(KEY_APP_MODE, AppMode.MUSIC.name)
        return try {
            AppMode.valueOf(savedMode ?: AppMode.MUSIC.name)
        } catch (_: Exception) {
            AppMode.MUSIC
        }
    }
    
    private fun saveAppMode(mode: AppMode) {
        prefs.edit().putString(KEY_APP_MODE, mode.name).apply()
    }
    
    private val _videoLibrary = MutableStateFlow<List<VideoItem>>(emptyList())
    val videoLibrary: StateFlow<List<VideoItem>> = _videoLibrary.asStateFlow()
    
    private val _isRefreshingVideos = MutableStateFlow(false)
    val isRefreshingVideos: StateFlow<Boolean> = _isRefreshingVideos.asStateFlow()
    
    // Currently playing video (if any)
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()
    
    // Video dominant color (extracted from thumbnail)
    private val _videoDominantColor = MutableStateFlow(Color(0xFF1A1A2E))
    val videoDominantColor: StateFlow<Color> = _videoDominantColor.asStateFlow()
    
    init {
        refreshVideoLibrary()
    }
    
    /**
     * Switch to a new app mode.
     * @param mode The new mode to switch to
     * @param forceSwitchIfPlaying If true, switch even if music is playing (for manual user action)
     * @param isMusicPlaying Whether music is currently playing
     */
    fun setAppMode(mode: AppMode, forceSwitchIfPlaying: Boolean = false, isMusicPlaying: Boolean = false) {
        // Only block automatic switches when music is playing
        // Manual switches (forceSwitchIfPlaying=true) always work
        if (!forceSwitchIfPlaying && isMusicPlaying && mode == AppMode.VIDEO) {
            Log.d("AppViewModel", "Blocked automatic switch to VIDEO mode while music is playing")
            return
        }
        _appMode.value = mode
        saveAppMode(mode)
    }
    
    /**
     * Called when a new video download completes - auto-switch to video mode if not playing music
     */
    fun onVideoDownloadComplete(isMusicPlaying: Boolean) {
        setAppMode(AppMode.VIDEO, forceSwitchIfPlaying = false, isMusicPlaying = isMusicPlaying)
        refreshVideoLibrary()
    }
    
    /**
     * Called when a new audio download completes - auto-switch to music mode if not playing video
     */
    fun onAudioDownloadComplete(isVideoPlaying: Boolean) {
        if (!isVideoPlaying) {
            setAppMode(AppMode.MUSIC, forceSwitchIfPlaying = false, isMusicPlaying = false)
        }
    }
    
    fun refreshVideoLibrary() {
        if (_isRefreshingVideos.value) return
        viewModelScope.launch {
            _isRefreshingVideos.value = true
            val videos = loadVideoFiles()
            _videoLibrary.value = videos
            _isRefreshingVideos.value = false
        }
    }
    
    fun playVideo(video: VideoItem) {
        _currentVideo.value = video
        updateVideoDominantColor(video)
    }
    
    private fun updateVideoDominantColor(video: VideoItem?) {
        viewModelScope.launch {
            val color = withContext(Dispatchers.IO) {
                extractDominantColor(video?.thumbnailPath)
            }
            _videoDominantColor.value = color ?: Color(0xFF1A1A2E)
        }
    }
    
    private fun extractDominantColor(imagePath: String?): Color? {
        if (imagePath == null) return null
        return try {
            val file = File(imagePath.replace('\\', '/'))
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
     */
    private fun ensureDarkEnough(color: Color): Color {
        val maxLuminance = 0.3f
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
        if (hsl[2] > maxLuminance) {
            hsl[2] = maxLuminance
        }
        if (hsl[1] > 0.7f && hsl[2] > 0.2f) {
            hsl[1] = hsl[1] * 0.8f
        }
        val darkened = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
        return Color(darkened)
    }
    
    fun stopVideoPlayback() {
        _currentVideo.value = null
    }
    
    fun deleteVideo(video: VideoItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(video.absolutePath).takeIf { it.exists() }?.delete()
                VideoMetadataStore.deleteMetadata(File(video.absolutePath))
                video.thumbnailPath?.let { path ->
                    File(path).takeIf { it.exists() }?.delete()
                }
            }

            // TODO: Sync deletion to backend API when implementing issue #38

            if (_currentVideo.value?.id == video.id) {
                stopVideoPlayback()
            }
            refreshVideoLibrary()
        }
    }
    
    fun renameVideo(video: VideoItem, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val trimmed = newTitle.trim()
            withContext(Dispatchers.IO) {
                val videoFile = File(video.absolutePath)
                VideoMetadataStore.updateMetadata(videoFile, title = trimmed)
            }
            // TODO: Sync rename to backend API when implementing issue #38
            refreshVideoLibrary()
        }
    }
    
    private suspend fun loadVideoFiles(): List<VideoItem> = withContext(Dispatchers.IO) {
        val directory = DownloadStorage.videoDirectory(getApplication())
        directory
            .listFiles()
            ?.filter { file ->
                file.isFile && !file.name.endsWith(VideoMetadataStore.METADATA_FILE_SUFFIX)
            }
            ?.map { it.toVideoItem() }
            ?.sortedByDescending { it.addedTimestamp }
            ?: emptyList()
    }
}

data class VideoItem(
    val id: String,
    val title: String,
    val durationMs: Long,
    val absolutePath: String,
    val addedTimestamp: Long,
    val thumbnailPath: String? = null
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

private fun File.toVideoItem(): VideoItem {
    val retriever = MediaMetadataRetriever()
    var duration = 0L
    var title = nameWithoutExtension.ifBlank { name }
    var thumbnailPath: String? = null
    
    try {
        retriever.setDataSource(absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.let { duration = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?.let { title = it }
    } catch (_: Exception) {
        // fall back to defaults
    } finally {
        retriever.release()
    }
    
    VideoMetadataStore.readMetadata(this)?.let { metadata ->
        metadata.title?.takeIf { it.isNotBlank() }?.let { title = it }
        metadata.thumbnailPath?.takeIf { it.isNotBlank() }?.let { thumbnailPath = it }
    }

    val video = VideoItem(
        id = absolutePath,
        title = title,
        durationMs = duration,
        absolutePath = absolutePath,
        addedTimestamp = lastModified(),
        thumbnailPath = thumbnailPath
    )
    Log.d("VideoItem", "Created video: $title, thumbnailPath: $thumbnailPath")
    return video
}


