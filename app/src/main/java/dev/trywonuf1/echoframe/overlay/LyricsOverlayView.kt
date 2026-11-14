package dev.trywonuf1.echoframe.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import dev.trywonuf1.echoframe.data.FontManager
import dev.trywonuf1.echoframe.data.OverlayStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent, non-touchable lyric surface.
 * Exactly one timestamped lyric is rendered at a time, so lines cannot overlap.
 */
class LyricsOverlayView(context: Context) : View(context) {
    private var style = OverlayStyle.DEFAULT
    private val density = resources.displayMetrics.density
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var currentText: String = ""
    private var currentLayout: StaticLayout? = null
    private var changedAt = SystemClock.elapsedRealtime()
    private var playing = false
    private var customTypeface: Typeface? = null

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        reloadTypeface()
    }

    fun setStyle(newStyle: OverlayStyle) {
        style = newStyle
        reloadTypeface()
        rebuildLayout()
        invalidate()
    }

    private fun reloadTypeface() {
        customTypeface = FontManager.loadTypeface(context)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun setPlaying(value: Boolean) {
        playing = value
        invalidate()
    }

    /** Replaces the previous lyric instead of adding another floating object. */
    fun enqueueLine(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return
        currentText = clean
        changedAt = SystemClock.elapsedRealtime()
        rebuildLayout()
        invalidate()
    }

    fun clearAll() {
        currentText = ""
        currentLayout = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = currentLayout ?: return
        if (width <= 0 || height <= 0) return
        if (style.hideOnPause && !playing) return

        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - changedAt).coerceAtLeast(0L)
        val duration = 520f
        val t = (elapsed / duration).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        val safeTop = dp(36f)
        val safeBottom = dp(56f)
        val centerY = ((height - safeTop - safeBottom) / 2f) + safeTop
        val x = (width - layout.width) / 2f
        val baseY = centerY - layout.height / 2f

        paint.color = style.textColor
        paint.alpha = (255f * style.textOpacity.coerceIn(0.2f, 1f) * eased).toInt()
        paint.textSize = style.textSizeSp * resources.displayMetrics.scaledDensity
        paint.typeface = customTypeface
        if (style.glowRadius > 0f) {
            paint.setShadowLayer(dp(style.glowRadius.coerceIn(0f, 36f)), 0f, 0f, style.glowColor)
        } else {
            paint.clearShadowLayer()
        }

        canvas.save()
        when (style.animationStyle) {
            "slide" -> canvas.translate(0f, dp(24f) * (1f - eased))
            "scale" -> {
                val scale = 0.88f + 0.12f * eased
                canvas.scale(scale, scale, width / 2f, centerY)
            }
            "fade" -> Unit
            else -> canvas.translate(0f, dp(10f) * (1f - eased))
        }
        canvas.translate(x, baseY)
        layout.draw(canvas)
        canvas.restore()
        paint.clearShadowLayer()

        if (t < 1f) postInvalidateOnAnimation()
    }

    private fun rebuildLayout() {
        if (currentText.isBlank() || width <= 0) {
            currentLayout = null
            return
        }
        val maxWidth = (width - dp(36f)).coerceAtLeast(dp(120f)).toInt()
        paint.textSize = style.textSizeSp * resources.displayMetrics.scaledDensity
        paint.typeface = customTypeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val measured = paint.measureText(currentText).toInt() + dp(20f).toInt()
        val layoutWidth = min(maxWidth, max(dp(120f).toInt(), measured))
        currentLayout = StaticLayout.Builder.obtain(currentText, 0, currentText.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    private fun dp(value: Float): Float = value * density
}
