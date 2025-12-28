package com.wpinrui.dovora.data.download

import android.content.Context
import android.os.Build
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
        legacyDirectory = Environment.getExternalStorageDirectory(),
        subfolder = AUDIO_FOLDER
    )

    fun videoDirectory(context: Context): File = resolveDirectory(
        context = context,
        modernDirectory = Environment.DIRECTORY_MOVIES,
        legacyDirectory = Environment.getExternalStorageDirectory(),
        subfolder = VIDEO_FOLDER
    )

    fun thumbnailDirectory(context: Context): File = resolveDirectory(
        context = context,
        modernDirectory = Environment.DIRECTORY_PICTURES,
        legacyDirectory = Environment.getExternalStorageDirectory(),
        subfolder = THUMBNAIL_FOLDER
    )

    private fun resolveDirectory(
        context: Context,
        modernDirectory: String,
        legacyDirectory: File,
        subfolder: String
    ): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(modernDirectory), subfolder)
        } else {
            File(legacyDirectory, subfolder)
        }
        dir.mkdirs()
        return dir
    }
}


