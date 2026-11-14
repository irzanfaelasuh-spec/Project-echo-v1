package dev.trywonuf1.echoframe.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import dev.trywonuf1.echoframe.data.OverlayStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One transparent, full-screen, non-touchable drawing surface. */
class LyricsOverlayView(context: Context) : View(context) {
    private data class FloatText(
        val text: String,
        val layout: StaticLayout,
        val centerX: Float,
        var y: Float,
        val speedPxPerSecond: Float,
        val bornAt: Long
    ) {
        val bounds: RectF
            get() = RectF(centerX - layout.width / 2f, y, centerX + layout.width / 2f, y + layout.height)
    }

    private data class QueuedText(val text: String, var readyAt: Long)
    private data class Particle(
        var x: Float,
        var y: Float,
        val vx: Float,
        val vy: Float,
        val radius: Float,
        val bornAt: Long,
        val lifeMs: Long
    )

    private var style = OverlayStyle.DEFAULT
    private val density = resources.displayMetrics.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeTexts = mutableListOf<FloatText>()
    private val pendingTexts = ArrayDeque<QueuedText>()
    private val particles = mutableListOf<Particle>()
    private var playing = false
    private var lastFrameAt = SystemClock.elapsedRealtime()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setStyle(newStyle: OverlayStyle) {
        style = newStyle
        invalidate()
    }

    fun setPlaying(value: Boolean) {
        playing = value
        invalidate()
    }

    fun clearAll() {
        activeTexts.clear()
        pendingTexts.clear()
        particles.clear()
        invalidate()
    }

    fun enqueueLine(line: String) {
        if (line.isBlank()) return
        val chunks = if (style.splitLongLines) splitIntoPhrases(line) else listOf(line.trim())
        val now = SystemClock.elapsedRealtime()
        chunks.forEachIndexed { index, chunk ->
            pendingTexts.addLast(QueuedText(chunk, now + index * 330L))
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw > 0 && oldh > 0 && (w != oldw || h != oldh)) {
            // Reflow visible phrases on rotation so no old portrait coordinates remain clipped.
            val carried = activeTexts.map { it.text }
            activeTexts.clear()
            particles.clear()
            val now = SystemClock.elapsedRealtime()
            carried.asReversed().forEach { pendingTexts.addFirst(QueuedText(it, now)) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastFrameAt).coerceAtMost(40L)).toFloat() / 1000f
        lastFrameAt = now

        if (style.hideOnPause && !playing) {
            return
        }

        spawnQueued(now)
        updateAndDrawTexts(canvas, dt, now)
        updateAndDrawParticles(canvas, now, dt)

        if (activeTexts.isNotEmpty() || pendingTexts.isNotEmpty() || particles.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun spawnQueued(now: Long) {
        while (pendingTexts.isNotEmpty() && pendingTexts.first().readyAt <= now) {
            if (activeTexts.size >= style.maxObjects) return
            val item = pendingTexts.first()
            if (trySpawn(item.text, now)) {
                pendingTexts.removeFirst()
            } else {
                item.readyAt = now + 180L
                return
            }
        }
    }

    private fun trySpawn(text: String, now: Long): Boolean {
        val maxWidth = (width * 0.78f).toInt().coerceAtLeast(dp(130f))
        val desiredTextWidth = textPaint.apply {
            textSize = style.textSizeSp * resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }.measureText(text)
        val layoutWidth = min(maxWidth.toFloat(), max(dp(90f), desiredTextWidth + dp(14f))).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        val safeMargin = dp(18f)
        val minY = (height * 0.58f).coerceAtMost((height - layout.height - safeMargin).coerceAtLeast(safeMargin))
        val maxY = (height - layout.height - safeMargin).coerceAtLeast(minY)
        val candidateCount = if (style.randomPosition) 28 else 1

        repeat(candidateCount) {
            val x = if (style.randomPosition) {
                Random.nextFloat() * (width - layout.width - safeMargin * 2f).coerceAtLeast(1f) + layout.width / 2f + safeMargin
            } else width / 2f
            val y = if (style.randomPosition) {
                minY + Random.nextFloat() * (maxY - minY).coerceAtLeast(0f)
            } else maxY
            val candidate = FloatText(
                text = text,
                layout = layout,
                centerX = x.coerceIn(layout.width / 2f + safeMargin, width - layout.width / 2f - safeMargin),
                y = y,
                speedPxPerSecond = dp((34f + Random.nextFloat() * 18f) * style.floatSpeed),
                bornAt = now
            )
            if (!style.collisionAvoidance || activeTexts.none { overlaps(candidate.bounds, it.bounds, dp(16f)) }) {
                activeTexts += candidate
                return true
            }
        }
        return false
    }

    private fun updateAndDrawTexts(canvas: Canvas, dt: Float, now: Long) {
        val iterator = activeTexts.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (playing) item.y -= item.speedPxPerSecond * dt
            textPaint.textSize = style.textSizeSp * resources.displayMetrics.scaledDensity
            textPaint.color = style.textColor
            textPaint.alpha = (255f * style.textOpacity.coerceIn(0.15f, 1f)).toInt()
            textPaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            if (style.glowRadius > 0f) {
                textPaint.setShadowLayer(dp(style.glowRadius.coerceIn(0f, 36f)), 0f, 0f, style.glowColor)
            } else {
                textPaint.clearShadowLayer()
            }
            canvas.save()
            canvas.translate(item.centerX - item.layout.width / 2f, item.y)
            item.layout.draw(canvas)
            canvas.restore()

            if (item.y + item.layout.height < -dp(4f)) {
                if (style.popEffect) createBurst(item.centerX, dp(16f), now)
                iterator.remove()
            }
        }
        textPaint.clearShadowLayer()
    }

    private fun updateAndDrawParticles(canvas: Canvas, now: Long, dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            val age = now - particle.bornAt
            val progress = age.toFloat() / particle.lifeMs.toFloat()
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            particlePaint.color = style.particleColor
            particlePaint.alpha = (255f * (1f - progress)).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.radius * (1f - progress * 0.45f), particlePaint)
        }
    }

    private fun createBurst(x: Float, y: Float, now: Long) {
        if (!style.particlesEnabled) return
        val count = style.particleCount.coerceIn(4, 64)
        repeat(count) { index ->
            val angle = (Math.PI * 2.0 * index / count) + Random.nextDouble(-0.15, 0.15)
            val velocity = dp(72f + Random.nextFloat() * 168f)
            particles += Particle(
                x = x,
                y = y,
                vx = (kotlin.math.cos(angle) * velocity).toFloat(),
                vy = (kotlin.math.sin(angle) * velocity).toFloat(),
                radius = dp(1.2f + Random.nextFloat() * 2.2f),
                bornAt = now,
                lifeMs = 420L + Random.nextLong(0L, 260L)
            )
        }
    }

    private fun splitIntoPhrases(line: String): List<String> {
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 3) return listOf(line.trim())
        val result = mutableListOf<String>()
        val buffer = mutableListOf<String>()
        words.forEach { word ->
            val tentative = (buffer + word).joinToString(" ")
            if (buffer.isNotEmpty() && (buffer.size >= 3 || textPaint.measureText(tentative) > dp(215f))) {
                result += buffer.joinToString(" ")
                buffer.clear()
            }
            buffer += word
        }
        if (buffer.isNotEmpty()) result += buffer.joinToString(" ")
        return result.ifEmpty { listOf(line.trim()) }
    }

    private fun overlaps(a: RectF, b: RectF, gap: Float): Boolean {
        val expanded = RectF(b.left - gap, b.top - gap, b.right + gap, b.bottom + gap)
        return RectF.intersects(a, expanded)
    }

    private fun dp(value: Float): Float = value * density
}
