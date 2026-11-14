package dev.trywonuf1.echoframe.media

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackSnapshot(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val updatedAtElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    fun currentPositionMs(): Long {
        if (!isPlaying || speed <= 0f) return positionMs.coerceAtLeast(0L)
        val elapsed = (SystemClock.elapsedRealtime() - updatedAtElapsedMs).coerceAtLeast(0L)
        val projected = positionMs + (elapsed * speed).toLong()
        return if (durationMs > 0L) projected.coerceIn(0L, durationMs) else projected.coerceAtLeast(0L)
    }

    val stableTrackKey: String
        get() = "$packageName|${title.trim()}|${artist.trim()}|$durationMs"
}

object PlaybackBus {
    private val mutablePlayback = MutableStateFlow<PlaybackSnapshot?>(null)
    val playback = mutablePlayback.asStateFlow()

    fun publish(snapshot: PlaybackSnapshot?) {
        mutablePlayback.value = snapshot
    }
}
