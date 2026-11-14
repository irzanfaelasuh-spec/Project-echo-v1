package dev.trywonuf1.echoframe.data

import android.content.Context
import android.content.Intent

/** Persistent preferences. Onboarding completion survives process death and app relaunch. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("echoframe_settings", Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean("overlay_enabled", false)
        set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()

    var lastTrackTitle: String
        get() = prefs.getString("last_track_title", "") ?: ""
        set(value) = prefs.edit().putString("last_track_title", value).apply()

    fun loadStyle(): OverlayStyle = OverlayStyle(
        textColor = prefs.getInt(KEY_TEXT_COLOR, OverlayStyle.DEFAULT.textColor),
        glowColor = prefs.getInt(KEY_GLOW_COLOR, OverlayStyle.DEFAULT.glowColor),
        particleColor = prefs.getInt(KEY_PARTICLE_COLOR, OverlayStyle.DEFAULT.particleColor),
        textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, OverlayStyle.DEFAULT.textSizeSp),
        glowRadius = prefs.getFloat(KEY_GLOW_RADIUS, OverlayStyle.DEFAULT.glowRadius),
        particleCount = prefs.getInt(KEY_PARTICLES, OverlayStyle.DEFAULT.particleCount),
        floatSpeed = prefs.getFloat(KEY_SPEED, OverlayStyle.DEFAULT.floatSpeed),
        textOpacity = prefs.getFloat(KEY_OPACITY, OverlayStyle.DEFAULT.textOpacity),
        randomPosition = prefs.getBoolean(KEY_RANDOM, true),
        collisionAvoidance = prefs.getBoolean(KEY_COLLISION, true),
        popEffect = prefs.getBoolean(KEY_POP, true),
        particlesEnabled = prefs.getBoolean(KEY_PARTICLES_ON, true),
        hideOnPause = prefs.getBoolean(KEY_HIDE_PAUSE, false),
        splitLongLines = prefs.getBoolean(KEY_SPLIT, true),
        maxObjects = prefs.getInt(KEY_MAX_OBJECTS, OverlayStyle.DEFAULT.maxObjects),
        animationStyle = prefs.getString(KEY_ANIMATION, OverlayStyle.DEFAULT.animationStyle) ?: OverlayStyle.DEFAULT.animationStyle
    )

    fun saveStyle(style: OverlayStyle) {
        prefs.edit()
            .putInt(KEY_TEXT_COLOR, style.textColor)
            .putInt(KEY_GLOW_COLOR, style.glowColor)
            .putInt(KEY_PARTICLE_COLOR, style.particleColor)
            .putFloat(KEY_TEXT_SIZE, style.textSizeSp)
            .putFloat(KEY_GLOW_RADIUS, style.glowRadius)
            .putInt(KEY_PARTICLES, style.particleCount)
            .putFloat(KEY_SPEED, style.floatSpeed)
            .putFloat(KEY_OPACITY, style.textOpacity)
            .putBoolean(KEY_RANDOM, style.randomPosition)
            .putBoolean(KEY_COLLISION, style.collisionAvoidance)
            .putBoolean(KEY_POP, style.popEffect)
            .putBoolean(KEY_PARTICLES_ON, style.particlesEnabled)
            .putBoolean(KEY_HIDE_PAUSE, style.hideOnPause)
            .putBoolean(KEY_SPLIT, style.splitLongLines)
            .putInt(KEY_MAX_OBJECTS, style.maxObjects)
            .putString(KEY_ANIMATION, style.animationStyle)
            .apply()
    }

    companion object {
        const val ACTION_STYLE_CHANGED = "dev.trywonuf1.echoframe.STYLE_CHANGED"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_GLOW_COLOR = "glow_color"
        private const val KEY_PARTICLE_COLOR = "particle_color"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_GLOW_RADIUS = "glow_radius"
        private const val KEY_PARTICLES = "particle_count"
        private const val KEY_SPEED = "float_speed"
        private const val KEY_OPACITY = "text_opacity"
        private const val KEY_RANDOM = "random_position"
        private const val KEY_COLLISION = "collision_avoidance"
        private const val KEY_POP = "pop_effect"
        private const val KEY_PARTICLES_ON = "particles_enabled"
        private const val KEY_HIDE_PAUSE = "hide_on_pause"
        private const val KEY_SPLIT = "split_long_lines"
        private const val KEY_MAX_OBJECTS = "max_objects"
        private const val KEY_ANIMATION = "animation_style"

        fun notifyStyleChanged(context: Context) {
            context.sendBroadcast(Intent(ACTION_STYLE_CHANGED).setPackage(context.packageName))
        }
    }
}

data class OverlayStyle(
    val textColor: Int,
    val glowColor: Int,
    val particleColor: Int,
    val textSizeSp: Float,
    val glowRadius: Float,
    val particleCount: Int,
    val floatSpeed: Float,
    val textOpacity: Float,
    val randomPosition: Boolean,
    val collisionAvoidance: Boolean,
    val popEffect: Boolean,
    val particlesEnabled: Boolean,
    val hideOnPause: Boolean,
    val splitLongLines: Boolean,
    val maxObjects: Int,
    val animationStyle: String = "slide"
) {
    companion object {
        val DEFAULT = OverlayStyle(
            textColor = 0xFFF4FAFF.toInt(),
            glowColor = 0xFF63D9FF.toInt(),
            particleColor = 0xFFB29CFF.toInt(),
            textSizeSp = 24f,
            glowRadius = 15f,
            particleCount = 16,
            floatSpeed = 1f,
            textOpacity = 1f,
            randomPosition = true,
            collisionAvoidance = true,
            popEffect = true,
            particlesEnabled = true,
            hideOnPause = false,
            splitLongLines = true,
            maxObjects = 5,
            animationStyle = "slide"
        )
    }
}
