
package dev.trywonuf1.echoframe.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.trywonuf1.echoframe.MainActivity
import dev.trywonuf1.echoframe.R
import dev.trywonuf1.echoframe.data.AppPrefs
import dev.trywonuf1.echoframe.data.LyricsRepository
import dev.trywonuf1.echoframe.data.LyricsResult
import dev.trywonuf1.echoframe.data.SyncKind
import dev.trywonuf1.echoframe.data.TrackRequest
import dev.trywonuf1.echoframe.media.PlaybackBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LyricsOverlayService : Service() {

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var repository: LyricsRepository

    private var overlayView: LyricsOverlayView? = null
    private var currentTrackKey: String? = null
    private var currentLines =
        emptyList<dev.trywonuf1.echoframe.data.LyricLine>()

    private var lastLineIndex = -1
    private var playbackJob: Job? = null
    private var tickerJob: Job? = null

    private val styleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlayView?.setStyle(
                AppPrefs(this@LyricsOverlayService).loadStyle()
            )
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WindowManager::class.java)
        repository = LyricsRepository(applicationContext)

        ContextCompat.registerReceiver(
            this,
            styleReceiver,
            IntentFilter(AppPrefs.ACTION_STYLE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            EchoRuntime.setRunning(false)
            AppPrefs(this).overlayEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        if (!attachOverlay()) {
            EchoRuntime.setRunning(false)
            AppPrefs(this).overlayEnabled = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        EchoRuntime.setRunning(true)
        AppPrefs(this).overlayEnabled = true

        startPlaybackObservers()

        return START_STICKY
    }

    private fun startAsForeground() {
        val openIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            11,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_echo)
                .setContentTitle("ECHOFRAME is active")
                .setContentText(
                    "Listening for music sessions · floating text overlay enabled"
                )
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            type
        )
    }

    private fun attachOverlay(): Boolean {
        if (overlayView != null) return true

        val view = LyricsOverlayView(this).apply {
            setStyle(
                AppPrefs(this@LyricsOverlayService).loadStyle()
            )
        }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                LayoutParams.TYPE_PHONE
            }

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            type,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCHABLE or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.72f
            title = "ECHOFRAME floating lyrics"
        }

        return runCatching {
            windowManager.addView(view, params)
            overlayView = view
            true
        }.getOrElse {
            EchoRuntime.setLyricsStatus(
                "Overlay could not be attached: ${it.message ?: "system rejected it"}"
            )
            false
        }
    }

    private fun startPlaybackObservers() {
        if (playbackJob?.isActive == true) return

        playbackJob = serviceScope.launch {
            PlaybackBus.playback.collect { snapshot ->

                if (snapshot == null) {
                    EchoRuntime.setTrack("Waiting for a music session")
                    EchoRuntime.setLyricsStatus(
                        "Open Spotify or another music app and play a track"
                    )

                    currentTrackKey = null
                    currentLines = emptyList()
                    lastLineIndex = -1

                    overlayView?.clearAll()

                    return@collect
                }

                EchoRuntime.setTrack(
                    "${snapshot.title} · ${snapshot.artist}"
                )

                overlayView?.setPlaying(snapshot.isPlaying)

                if (snapshot.stableTrackKey != currentTrackKey) {
                    currentTrackKey = snapshot.stableTrackKey
                    currentLines = emptyList()
                    lastLineIndex = -1

                    overlayView?.clearAll()

                    EchoRuntime.setLyricsStatus(
                        "Searching timestamped lyrics…"
                    )

                    val result = repository.findLyrics(
                        TrackRequest(
                            snapshot.title,
                            snapshot.artist,
                            snapshot.album,
                            snapshot.durationMs
                        )
                    )

                    when (result) {
                        is LyricsResult.Found -> {
                            currentLines = result.lines

                            EchoRuntime.setLyricsStatus(
                                if (result.syncKind == SyncKind.TIMESTAMPED) {
                                    "Synced lyrics ready"
                                } else {
                                    "Lyrics found · estimated timing"
                                }
                            )
                        }

                        LyricsResult.Instrumental -> {
                            EchoRuntime.setLyricsStatus(
                                "Instrumental track · no vocal lyrics"
                            )
                        }

                        LyricsResult.NotFound -> {
                            EchoRuntime.setLyricsStatus(
                                "Lyrics not found for this track"
                            )
                        }

                        is LyricsResult.Failed -> {
                            EchoRuntime.setLyricsStatus(
                                "Lyrics lookup unavailable · ${result.message}"
                            )
                        }
                    }
                }
            }
        }

        tickerJob = serviceScope.launch {
            while (true) {
                val snapshot = PlaybackBus.playback.value

                overlayView?.setPlaying(
                    snapshot?.isPlaying == true
                )

                if (
                    snapshot != null &&
                    currentLines.isNotEmpty() &&
                    snapshot.isPlaying
                ) {
                    val index =
                        dev.trywonuf1.echoframe.data.LrcParser.indexAt(
                            currentLines,
                            snapshot.currentPositionMs()
                        )

                    if (index >= 0 && index != lastLineIndex) {
                        lastLineIndex = index
                        overlayView?.enqueueLine(
                            currentLines[index].text
                        )
                    }
                }

                delay(90L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ECHOFRAME overlay engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Keeps the user-started floating lyrics engine active"
                setShowBadge(false)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        playbackJob?.cancel()
        tickerJob?.cancel()
        serviceScope.cancel()

        runCatching {
            unregisterReceiver(styleReceiver)
        }

        overlayView?.let { view ->
            runCatching {
                windowManager.removeView(view)
            }
        }

        overlayView = null

        EchoRuntime.setRunning(false)
        AppPrefs(this).overlayEnabled = false

        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP =
            "dev.trywonuf1.echoframe.STOP_OVERLAY"

        private const val CHANNEL_ID = "echoframe_overlay"
        private const val NOTIFICATION_ID = 7301

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LyricsOverlayService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LyricsOverlayService::class.java)
                    .setAction(ACTION_STOP)
            )
        }
    }
}
