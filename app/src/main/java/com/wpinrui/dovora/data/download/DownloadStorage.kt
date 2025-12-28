package com.wpinrui.dovora.data.download

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Central place for resolving the directories that store downloaded media.
 */
object DownloadStorage {

    private const val AUDIO_FOLDER = "Dovora/Audio"
    private const val VIDEO_FOLDER = "Dovora/Video"
    private const val THUMBNAIL_FOLDER = "Dovora/Thumbnails"

    fun audioDirectory(context: Context): File = resolveDirectory(
        context = context,
        modernDirectory = Environment.DIRECTORY_MUSIC,
        subfolder = AUDIO_FOLDER
    )

    fun videoDirectory(context: Context): File = resolveDirectory(
        context = context,
        modernDirectory = Environment.DIRECTORY_MOVIES,
        subfolder = VIDEO_FOLDER
    )

    fun thumbnailDirectory(context: Context): File = resolveDirectory(
        context = context,
        modernDirectory = Environment.DIRECTORY_PICTURES,
        subfolder = THUMBNAIL_FOLDER
    )

    private fun resolveDirectory(
        context: Context,
        modernDirectory: String,
        subfolder: String
    ): File {
        val dir = File(context.getExternalFilesDir(modernDirectory), subfolder)
        dir.mkdirs()
        return dir
    }
}


