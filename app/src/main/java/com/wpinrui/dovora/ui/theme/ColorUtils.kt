package com.wpinrui.dovora.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import java.io.File

/**
 * Extracts the dominant color from an image file.
 * Returns null if the file doesn't exist or color extraction fails.
 */
fun extractDominantColor(imagePath: String?): Color? {
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
 * If the color is too bright, it darkens it while preserving the hue.
 */
fun ensureDarkEnough(color: Color): Color {
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

    // Reduce saturation slightly for very saturated bright colors
    if (hsl[1] > 0.7f && hsl[2] > 0.2f) {
        hsl[1] = hsl[1] * 0.8f
    }

    val darkened = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    return Color(darkened)
}
