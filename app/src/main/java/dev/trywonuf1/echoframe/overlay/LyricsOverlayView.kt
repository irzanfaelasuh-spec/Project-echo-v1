package dev.trywonuf1.echoframe.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import dev.trywonuf1.echoframe.data.OverlayStyle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Single-focus kinetic lyric renderer.
 * Only one phrase is drawn at a time, so phrases from the same line can never overlap.
 */
class LyricsOverlayView(context: Context) : View(context) {
    private data class PhraseSegment(
        val text: String,
        val startsAt: Long,
        val endsAt: Long
    )

    private var style: OverlayStyle = OverlayStyle.DEFAULT
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 8, 10, 13)
        style = Paint.Style.FILL
    }
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private var segments: List<PhraseSegment> = emptyList()
    private var playing = false
    private var pausedAt: Long? = null
    private var cachedLayoutText: String? = null
    private var cachedLayoutWidth = -1
    private var cachedTextSize = -1f
    private var cachedLayout: StaticLayout? = null

    init {
        // Keep Android hardware acceleration enabled; this renderer uses no particle or blur layers.
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setStyle(newStyle: OverlayStyle) {
        style = newStyle
        cachedLayout = null
        invalidate()
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return
        val now = SystemClock.elapsedRealtime()
        if (!value) {
            pausedAt = now
        } else {
            val pauseStart = pausedAt
            if (pauseStart != null) {
                val pauseDuration = (now - pauseStart).coerceAtLeast(0L)
                segments = segments.map { segment ->
                    segment.copy(
                        startsAt = segment.startsAt + pauseDuration,
                        endsAt = segment.endsAt + pauseDuration
                    )
                }
            }
            pausedAt = null
        }
        playing = value
        invalidate()
    }

    fun clearAll() {
        segments = emptyList()
        pausedAt = null
        invalidate()
    }

    /**
     * Receives a timestamped lyric line and the time until the next LRC line.
     * The line is split into a few readable phrases and shown sequentially.
     */
    fun enqueueLine(line: String, durationMs: Long = 4_200L) {
        val cleanLine = line.replace(Regex("\\s+"), " ").trim()
        if (cleanLine.isBlank()) return

        val safeDuration = durationMs.coerceIn(900L, 12_000L)
        val maxParts = (safeDuration / 900L).toInt().coerceIn(1, 5)
        val phrases = splitIntoPhrases(cleanLine, maxParts)
        val segmentDuration = max(620L, safeDuration / phrases.size)
        val now = SystemClock.elapsedRealtime()

        // Replacing the timeline prevents stale phrases from a previous line or seek.
        segments = phrases.mapIndexed { index, phrase ->
            val start = now + index * segmentDuration
            PhraseSegment(phrase, start, start + segmentDuration)
        }
        pausedAt = if (playing) null else now
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedLayout = null
        cachedLayoutText = null
        cachedLayoutWidth = -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || segments.isEmpty()) return
        if (style.hideOnPause && !playing) return

        val now = if (!playing) pausedAt ?: SystemClock.elapsedRealtime() else SystemClock.elapsedRealtime()
        val active = segments.firstOrNull { now >= it.startsAt && now < it.endsAt }

        if (active != null) {
            drawPhrase(canvas, active, now)
            if (playing) postInvalidateOnAnimation()
        } else if (playing && segments.any { it.startsAt > now }) {
            // Keep the animation clock alive between consecutive phrases.
            postInvalidateOnAnimation()
        }
    }

    private fun drawPhrase(canvas: Canvas, segment: PhraseSegment, now: Long) {
        val textSize = (style.textSizeSp.coerceIn(18f, 42f) * scaledDensity)
        val maxTextWidth = (width * 0.82f).roundToInt().coerceAtLeast(dp(120f).roundToInt())
        val layout = obtainLayout(segment.text, maxTextWidth, textSize)

        val horizontalPadding = dp(18f)
        val verticalPadding = dp(11f)
        val cardWidth = layout.width + horizontalPadding * 2f
        val cardHeight = layout.height + verticalPadding * 2f
        val cardLeft = (width - cardWidth) / 2f
        val desiredTop = height * 0.68f - cardHeight / 2f
        val topSafe = dp(80f)
        val bottomSafe = dp(92f)
        val cardTop = desiredTop.coerceIn(
            topSafe,
            (height - cardHeight - bottomSafe).coerceAtLeast(topSafe)
        )

        val elapsed = (now - segment.startsAt).coerceAtLeast(0L).toFloat()
        val remaining = (segment.endsAt - now).coerceAtLeast(0L).toFloat()
        val fadeIn = (elapsed / 240f).coerceIn(0f, 1f)
        val fadeOut = (remaining / 260f).coerceIn(0f, 1f)
        val alpha = min(fadeIn, fadeOut)
        val slideY = dp(13f) * (1f - fadeIn)

        val panelRect = RectF(
            cardLeft,
            cardTop + slideY,
            cardLeft + cardWidth,
            cardTop + slideY + cardHeight
        )
        val corner = dp(17f)

        panelPaint.alpha = (150f * alpha).roundToInt().coerceIn(0, 150)
        panelStrokePaint.alpha = (42f * alpha).roundToInt().coerceIn(0, 42)
        canvas.drawRoundRect(panelRect, corner, corner, panelPaint)
        canvas.drawRoundRect(panelRect, corner, corner, panelStrokePaint)

        textPaint.textSize = textSize
        textPaint.color = Color.WHITE
        textPaint.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        textPaint.setShadowLayer(dp(6f), 0f, dp(2f), Color.argb(210, 0, 0, 0))

        canvas.save()
        canvas.translate(
            cardLeft + horizontalPadding,
            cardTop + verticalPadding + slideY
        )
        layout.draw(canvas)
        canvas.restore()
        textPaint.clearShadowLayer()

        panelPaint.alpha = 150
        panelStrokePaint.alpha = 42
        textPaint.alpha = 255
    }

    private fun obtainLayout(text: String, maxWidth: Int, textSize: Float): StaticLayout {
        val current = cachedLayout
        if (current != null && cachedLayoutText == text && cachedLayoutWidth == maxWidth && cachedTextSize == textSize) {
            return current
        }

        textPaint.textSize = textSize
        val measuredWidth = ceil(textPaint.measureText(text)).toInt()
        val layoutWidth = min(maxWidth, max(dp(90f).roundToInt(), measuredWidth))
        val created = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

        cachedLayoutText = text
        cachedLayoutWidth = maxWidth
        cachedTextSize = textSize
        cachedLayout = created
        return created
    }

    private fun splitIntoPhrases(line: String, maxParts: Int): List<String> {
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 4 || maxParts <= 1) return listOf(line)

        val partCount = min(maxParts, ceil(words.size / 3.5f).toInt()).coerceAtLeast(1)
        val targetWords = ceil(words.size.toFloat() / partCount).toInt().coerceIn(2, 5)
        val result = mutableListOf<String>()
        val buffer = mutableListOf<String>()

        words.forEachIndexed { index, word ->
            buffer += word
            val punctuationBreak = word.lastOrNull()?.let { it in listOf(',', ';', ':', '.', '!', '?') } == true
            val wordsRemain = words.size - index - 1
            val shouldBreak = buffer.size >= targetWords ||
                (punctuationBreak && buffer.size >= 2 && wordsRemain >= 2)
            if (shouldBreak && wordsRemain > 0 && result.size < partCount - 1) {
                result += buffer.joinToString(" ")
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) result += buffer.joinToString(" ")
        return result.ifEmpty { listOf(line) }
    }

    private fun dp(value: Float): Float = value * density
}
