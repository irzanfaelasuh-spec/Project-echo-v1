package dev.trywonuf1.echoframe.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

sealed class LyricsResult {
    data class Found(
        val lines: List<LyricLine>,
        val trackName: String,
        val artistName: String,
        val syncKind: SyncKind,
        val instrumental: Boolean = false
    ) : LyricsResult()

    data object NotFound : LyricsResult()
    data object Instrumental : LyricsResult()
    data class Failed(val message: String) : LyricsResult()
}

enum class SyncKind { TIMESTAMPED, ESTIMATED }

data class TrackRequest(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
) {
    val cacheKey: String
        get() = listOf(title, artist, album, durationMs.toString())
            .joinToString("|") { it.trim().lowercase() }
}

class LyricsRepository(context: Context) {
    private val dao = EchoDatabase.get(context).lyricsCache()

    suspend fun findLyrics(track: TrackRequest): LyricsResult = withContext(Dispatchers.IO) {
        if (track.title.isBlank() || track.artist.isBlank()) return@withContext LyricsResult.NotFound

        runCatching {
            dao.find(track.cacheKey)?.let { cached ->
                if (cached.instrumental) return@withContext LyricsResult.Instrumental
                val timed = LrcParser.parse(cached.syncedLyrics)
                if (timed.isNotEmpty()) {
                    return@withContext LyricsResult.Found(
                        timed, cached.trackName, cached.artistName, SyncKind.TIMESTAMPED
                    )
                }
                val estimated = LrcParser.estimateFromPlainLyrics(cached.plainLyrics, track.durationMs)
                if (estimated.isNotEmpty()) {
                    return@withContext LyricsResult.Found(
                        estimated, cached.trackName, cached.artistName, SyncKind.ESTIMATED
                    )
                }
            }

            val record = fetchExact(track) ?: fetchSearch(track)
                ?: return@withContext LyricsResult.NotFound

            val title = record.optString("trackName", track.title)
            val artist = record.optString("artistName", track.artist)
            val album = record.optString("albumName", track.album)
            val durationSeconds = record.optDouble("duration", track.durationMs / 1000.0)
            val durationMs = (durationSeconds * 1000.0).toLong().coerceAtLeast(track.durationMs)
            val instrumental = record.optBoolean("instrumental", false)
            val synced = record.optString("syncedLyrics", "").takeUnless { it == "null" }.orEmpty()
            val plain = record.optString("plainLyrics", "").takeUnless { it == "null" }.orEmpty()

            dao.put(
                LyricsCacheEntity(
                    cacheKey = track.cacheKey,
                    trackName = title,
                    artistName = artist,
                    albumName = album,
                    durationMs = durationMs,
                    syncedLyrics = synced,
                    plainLyrics = plain,
                    instrumental = instrumental,
                    syncKind = if (synced.isNotBlank()) "TIMESTAMPED" else "ESTIMATED",
                    savedAtMs = System.currentTimeMillis()
                )
            )

            when {
                instrumental -> LyricsResult.Instrumental
                LrcParser.parse(synced).isNotEmpty() -> LyricsResult.Found(
                    LrcParser.parse(synced), title, artist, SyncKind.TIMESTAMPED
                )
                LrcParser.estimateFromPlainLyrics(plain, track.durationMs).isNotEmpty() -> LyricsResult.Found(
                    LrcParser.estimateFromPlainLyrics(plain, track.durationMs), title, artist, SyncKind.ESTIMATED
                )
                else -> LyricsResult.NotFound
            }
        }.getOrElse { error ->
            LyricsResult.Failed(error.message ?: "Lyrics lookup failed")
        }
    }

    private fun fetchExact(track: TrackRequest): JSONObject? {
        if (track.durationMs <= 0L) return null
        val query = buildString {
            append("track_name=").append(enc(track.title))
            append("&artist_name=").append(enc(track.artist))
            if (track.album.isNotBlank()) append("&album_name=").append(enc(track.album))
            append("&duration=").append((track.durationMs / 1000L).coerceAtLeast(1L))
        }
        val response = request("https://lrclib.net/api/get?$query") ?: return null
        val record = runCatching { JSONObject(response) }.getOrNull() ?: return null
        // Reject an unexpected provider match rather than displaying another track's lyrics.
        if (record.optString("trackName").normalized() != track.title.normalized()) return null
        if (record.optString("artistName").normalized() != track.artist.normalized()) return null
        return record
    }

    private fun fetchSearch(track: TrackRequest): JSONObject? {
        val query = "track_name=${enc(track.title)}&artist_name=${enc(track.artist)}"
        val response = request("https://lrclib.net/api/search?$query") ?: return null
        val array = runCatching { org.json.JSONArray(response) }.getOrNull() ?: return null
        if (array.length() == 0) return null
        val wantedTitle = track.title.normalized()
        val wantedArtist = track.artist.normalized()
        val ranked = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { candidate ->
                val titleScore = if (candidate.optString("trackName").normalized() == wantedTitle) 2 else 0
                val artistScore = if (candidate.optString("artistName").normalized() == wantedArtist) 2 else 0
                val wantedDuration = track.durationMs / 1000.0
                val foundDuration = candidate.optDouble("duration", -1.0)
                val durationMatches = wantedDuration <= 0 || foundDuration <= 0 || kotlin.math.abs(wantedDuration - foundDuration) <= 5.0
                if (titleScore != 2 || artistScore != 2 || !durationMatches) return@mapNotNull null
                val durationScore = if (wantedDuration > 0 && foundDuration > 0) 2 else 0
                Triple(candidate, titleScore + artistScore + durationScore, index)
            }
        }
        return ranked.maxWithOrNull(compareBy<Triple<JSONObject, Int, Int>> { it.second }.thenBy { -it.third })
            ?.first
    }

    private fun request(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 9_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ECHOFRAME/0.1 (https://github.com/TRyWonUF1/ECHOFRAME)")
        }
        return try {
            val status = connection.responseCode
            if (status == 429) {
                val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                throw IllegalStateException("LRCLIB rate limited this client${retryAfter?.let { "; retry after ${it}s" } ?: ""}")
            }
            if (status !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun String.normalized(): String = lowercase().replace(Regex("[^a-z0-9]"), "")
}
