package com.wpinrui.dovora.data.api

import com.wpinrui.dovora.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LyricsStatus { OK, NO_LYRICS }

data class LyricsResult(
    val status: LyricsStatus,
    val lyrics: String?,
    val title: String?,
    val artist: String?,
    val source: String?,
    val url: String?
)

class LyricsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLyrics(artist: String, title: String): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(BuildConfig.BACKEND_BASE_URL.trimEnd('/'))
                append("/lyrics")
                append("?artist=").append(java.net.URLEncoder.encode(artist, "UTF-8"))
                append("&title=").append(java.net.URLEncoder.encode(title, "UTF-8"))
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", BuildConfig.BACKEND_API_KEY)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to fetch lyrics: ${response.code}")
                }

                val body = response.body?.string() ?: throw IllegalStateException("Empty response")
                val json = JSONObject(body)

                when (json.getString("status")) {
                    "ok" -> {
                        LyricsResult(
                            status = LyricsStatus.OK,
                            lyrics = json.optStringOrNull("lyrics"),
                            title = json.optStringOrNull("title"),
                            artist = json.optStringOrNull("artist"),
                            source = json.optStringOrNull("source"),
                            url = json.optStringOrNull("url")
                        )
                    }
                    "no_lyrics" -> {
                        LyricsResult(
                            status = LyricsStatus.NO_LYRICS,
                            lyrics = null,
                            title = json.optStringOrNull("title"),
                            artist = json.optStringOrNull("artist"),
                            source = null,
                            url = null
                        )
                    }
                    else -> throw IllegalStateException("Backend returned error")
                }
            }
        }
    }
}
