package dev.trywonuf1.echoframe.data

/** One line in a line-synchronized LRC lyric file. */
data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    private val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(input: String?): List<LyricLine> {
        if (input.isNullOrBlank()) return emptyList()
        val parsed = mutableListOf<LyricLine>()
        input.lineSequence().forEach { rawLine ->
            val matches = timestampRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = timestampRegex.replace(rawLine, "").trim()
            if (text.isBlank()) return@forEach
            matches.forEach timestampLoop@{ match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@timestampLoop
                val seconds = match.groupValues[2].toLongOrNull() ?: return@timestampLoop
                val fractionRaw = match.groupValues.getOrNull(3).orEmpty()
                val fractionMs = when (fractionRaw.length) {
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                parsed += LyricLine((minutes * 60_000L) + (seconds * 1_000L) + fractionMs, text)
            }
        }
        return parsed.sortedBy { it.timeMs }
    }

    fun estimateFromPlainLyrics(plainLyrics: String?, durationMs: Long): List<LyricLine> {
        val lines = plainLyrics.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val safeDuration = durationMs.coerceAtLeast(lines.size * 1_000L)
        val interval = safeDuration.toDouble() / lines.size.toDouble()
        return lines.mapIndexed { index, line -> LyricLine((index * interval).toLong(), line) }
    }

    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
