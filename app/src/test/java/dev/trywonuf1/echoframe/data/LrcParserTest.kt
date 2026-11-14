package dev.trywonuf1.echoframe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test fun parsesHundredthSecondsAndMultipleTimestamps() {
        val result = LrcParser.parse("[00:01.25][00:02.50]First line\n[01:03.2]Second line")
        assertEquals(3, result.size)
        assertEquals(1_250L, result[0].timeMs)
        assertEquals(2_500L, result[1].timeMs)
        assertEquals(63_200L, result[2].timeMs)
    }

    @Test fun ignoresMetadataAndEmptyLines() {
        val result = LrcParser.parse("[ti:Demo]\n[ar:Artist]\n[00:00.00]\n[00:03.00]Text")
        assertEquals(1, result.size)
        assertEquals("Text", result.first().text)
    }

    @Test fun indexAtFindsLatestLineAtOrBeforePosition() {
        val lines = listOf(LyricLine(1_000, "A"), LyricLine(2_000, "B"), LyricLine(5_000, "C"))
        assertEquals(-1, LrcParser.indexAt(lines, 999))
        assertEquals(1, LrcParser.indexAt(lines, 2_500))
        assertEquals(2, LrcParser.indexAt(lines, 9_000))
    }

    @Test fun sortsOutOfOrderTimestampsAndDropsExactDuplicates() {
        val result = LrcParser.parse("[00:04.00]Later\n[00:01.00]First\n[00:01.00]First")
        assertEquals(listOf("First", "Later"), result.map { it.text })
    }

    @Test fun rejectsInvalidSecondsField() {
        val result = LrcParser.parse("[00:75.00]Invalid\n[00:12.00]Valid")
        assertEquals(listOf("Valid"), result.map { it.text })
    }

    @Test fun plainLyricsFallbackCreatesMonotonicTimestamps() {
        val lines = LrcParser.estimateFromPlainLyrics("one\ntwo\nthree", 9_000)
        assertEquals(3, lines.size)
        assertTrue(lines[0].timeMs < lines[1].timeMs)
        assertTrue(lines[1].timeMs < lines[2].timeMs)
    }
}
