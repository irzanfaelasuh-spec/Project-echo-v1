package dev.trywonuf1.echoframe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.trywonuf1.echoframe.data.AppPrefs
import dev.trywonuf1.echoframe.data.OverlayStyle
import dev.trywonuf1.echoframe.media.PlaybackBus
import dev.trywonuf1.echoframe.overlay.EchoRuntime
import dev.trywonuf1.echoframe.overlay.LyricsOverlayService
import dev.trywonuf1.echoframe.util.ShizukuStatus
import kotlinx.coroutines.delay

private val Ink = Color(0xFF080D18)
private val Panel = Color(0xFF111A2A)
private val PanelSoft = Color(0xFF172337)
private val Cyan = Color(0xFF66D9FF)
private val Violet = Color(0xFFAA91FF)
private val Muted = Color(0xFF8B9BB2)
private val Green = Color(0xFF70E0B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 24)
        setContent { EchoFrameRoot() }
    }
}

@Composable
private fun EchoFrameRoot() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var page by rememberSaveable { mutableStateOf("loading") }
    var tab by rememberSaveable { mutableStateOf("home") }
    var permissionPulse by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionPulse++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan,
        secondary = Violet,
        background = Ink,
        surface = Panel,
        onPrimary = Ink,
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1424), Ink, Color(0xFF070A12))))
        ) {
            Crossfade(targetState = page, animationSpec = tween(420), label = "app-page") { current ->
                when (current) {
                    "loading" -> LoadingScreen {
                        page = if (prefs.onboardingComplete) "main" else "verify"
                    }
                    "verify" -> VerificationScreen(
                        pulse = permissionPulse,
                        onRefresh = { permissionPulse++ },
                        onNext = {
                            prefs.onboardingComplete = true
                            page = "main"
                            tab = "home"
                        }
                    )
                    else -> MainShell(
                        selectedTab = tab,
                        onTab = { tab = it },
                        onOpenVerification = { page = "verify" }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(onFinished: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val title = "Welcome To ECHOFRAME"
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "cursor-alpha"
    )
    LaunchedEffect(Unit) {
        title.forEach { character ->
            typed += character
            delay(if (character == ' ') 85L else 42L)
        }
        delay(650L)
        onFinished()
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LogoMark(112.dp)
        Spacer(Modifier.height(32.dp))
        Text(typed + "|", color = Color.White.copy(alpha = blink), fontSize = 25.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("REALTIME FLOATING LYRICS", color = Muted, fontSize = 10.sp, letterSpacing = 2.3.sp)
        Spacer(Modifier.height(52.dp))
        LoadingRail()
        Spacer(Modifier.height(15.dp))
        Text("INITIALIZING VISUAL ENGINE", color = Muted, fontSize = 9.sp, letterSpacing = 1.7.sp)
    }
}

@Composable
private fun LoadingRail() {
    var progress by remember { mutableStateOf(0f) }
    val animated by animateFloatAsState(progress, tween(950, easing = FastOutSlowInEasing), label = "loading-progress")
    LaunchedEffect(Unit) {
        delay(120L)
        progress = 1f
    }
    Box(Modifier.fillMaxWidth(0.68f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(animated).height(3.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Cyan, Violet))))
    }
}

private data class PermissionState(
    val overlay: Boolean,
    val listener: Boolean,
    val notifications: Boolean,
    val shizukuRunning: Boolean,
    val shizukuPermission: Boolean,
    val batteryUnrestricted: Boolean
) {
    val requiredReady: Boolean get() = overlay && listener && notifications
}

private fun readPermissionState(context: android.content.Context): PermissionState {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val listenerAllowed = enabled.split(":").any { it.contains(context.packageName) }
    val notificationAllowed = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(android.os.PowerManager::class.java)
    val unrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) power?.isIgnoringBatteryOptimizations(context.packageName) == true else true
    return PermissionState(
        overlay = Settings.canDrawOverlays(context),
        listener = listenerAllowed,
        notifications = notificationAllowed,
        shizukuRunning = ShizukuStatus.isRunning(),
        shizukuPermission = ShizukuStatus.hasPermission(),
        batteryUnrestricted = unrestricted
    )
}

@Composable
private fun VerificationScreen(pulse: Int, onRefresh: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val state = remember(pulse) { readPermissionState(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("01 / FIRST-RUN CHECK", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("Verification", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Set the required Android access once. ECHOFRAME remembers that setup after you continue.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)

        PermissionCard(
            title = "Display over other apps",
            detail = "Allows floating lyric text above games and other apps.",
            required = true,
            granted = state.overlay,
            actionLabel = if (state.overlay) "Granted" else "Allow",
            onAction = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
            }
        )
        PermissionCard(
            title = "Notification listener access",
            detail = "Reads media-session metadata such as track, artist, duration and playback position. It does not read private app files.",
            required = true,
            granted = state.listener,
            actionLabel = if (state.listener) "Granted" else "Open settings",
            onAction = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
        )
        PermissionCard(
            title = "App notifications",
            detail = "Shows the persistent status notification while the overlay engine is running.",
            required = true,
            granted = state.notifications,
            actionLabel = if (state.notifications) "Granted" else "Allow",
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else onRefresh()
            }
        )

        Text("OPTIONAL ACCESS", color = Violet, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
        PermissionCard(
            title = "Shizuku connection",
            detail = "Optional integration check. The core lyrics overlay does not require Shizuku.",
            required = false,
            granted = state.shizukuPermission,
            actionLabel = when {
                state.shizukuPermission -> "Connected"
                state.shizukuRunning -> "Grant access"
                else -> "Open Shizuku guide"
            },
            onAction = {
                if (state.shizukuRunning) {
                    ShizukuStatus.requestPermission()
                    onRefresh()
                } else {
                    val shizukuLaunch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (shizukuLaunch != null) {
                        runCatching { context.startActivity(shizukuLaunch) }
                    } else {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
                    }
                }
            }
        )
        PermissionCard(
            title = "Background battery allowance",
            detail = "Can reduce background restrictions on some devices. This is optional and device settings differ.",
            required = false,
            granted = state.batteryUnrestricted,
            actionLabel = if (state.batteryUnrestricted) "Unrestricted" else "Battery settings",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        )

        Button(
            onClick = onNext,
            enabled = state.requiredReady,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink, disabledContainerColor = PanelSoft, disabledContentColor = Muted)
        ) {
            Text("NEXT  →", fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        }
        Text(
            if (state.requiredReady) "Required access is ready. Optional permissions can be enabled later." else "Grant all three required permissions to continue.",
            color = if (state.requiredReady) Green else Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    detail: String,
    required: Boolean,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (granted) Green else Color(0xFF65748C)))
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (required) "REQUIRED" else "OPTIONAL", color = if (required) Cyan else Violet, fontSize = 8.sp, letterSpacing = 1.sp)
            }
            Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (granted) Color(0xFF18382F) else PanelSoft,
                        contentColor = if (granted) Green else Color.White
                    )
                ) { Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun MainShell(selectedTab: String, onTab: (String) -> Unit, onOpenVerification: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoMark(35.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("ECHOFRAME", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("FLOATING LYRICS ENGINE", color = Muted, fontSize = 8.sp, letterSpacing = 1.4.sp)
            }
            Text("PHASE 01", color = Cyan, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Panel.copy(alpha = 0.88f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("home" to "HOME", "settings" to "SETTINGS", "about" to "ABOUT", "access" to "ACCESS").forEach { (value, label) ->
                val active = selectedTab == value
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (active) PanelSoft else Color.Transparent)
                        .clickable { if (value == "access") onOpenVerification() else onTab(value) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (active) Cyan else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp) }
            }
        }
        Crossfade(targetState = selectedTab, animationSpec = tween(280), label = "main-tab") { tab ->
            when (tab) {
                "settings" -> SettingsScreen()
                "about" -> AboutScreen()
                else -> HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val running by EchoRuntime.engineRunning.collectAsState()
    val trackTitle by EchoRuntime.trackTitle.collectAsState()
    val lyricsStatus by EchoRuntime.lyricsStatus.collectAsState()
    var booting by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val prefs = remember { AppPrefs(context) }
    var accessPulse by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessPulse++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val access = remember(accessPulse) { readPermissionState(context) }

    LaunchedEffect(booting) {
        if (!booting) return@LaunchedEffect
        logs.clear()
        val steps = listOf(
            "Mounting ECHOFRAME runtime... [SUCCEED]",
            "Checking overlay permission... [SUCCEED]",
            "Connecting media-session listener... [READY]",
            "Loading local lyric cache... [SUCCEED]",
            "Preparing floating renderer... [SUCCEED]",
            "Starting realtime listener... [ACTIVE]"
        )
        steps.forEach { line ->
            logs.add(line)
            delay(440L)
        }
        val startResult = runCatching { LyricsOverlayService.start(context) }
        if (startResult.isFailure) {
            logs.add("Overlay start failed: ${startResult.exceptionOrNull()?.message ?: "system rejected request"} [FAILED]")
        } else {
            logs.add("Foreground overlay start requested... [WAIT]")
            for (attempt in 0 until 18) {
                if (EchoRuntime.engineRunning.value) break
                delay(120L)
            }
            if (EchoRuntime.engineRunning.value) {
                ready = true
                logs.add("Overlay surface attached and running [SUCCEED]")
                delay(2_000L)
                ready = false
            } else {
                logs.add("Engine did not confirm startup. Check ACCESS and system restrictions [CHECK]")
            }
        }
        booting = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(
            top = 24.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 30.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR MUSIC, IN MOTION", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
            Text("Make lyrics\npart of the screen.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ENGINE STATUS", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(CircleShape).background(if (running) Color(0xFF163A31) else PanelSoft).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(if (running) "ACTIVE" else "STANDBY", color = if (running) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (running) "Overlay engine is running" else "Ready to initialize", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${if (running) "LISTENING" else "IDLE"}  /  ANDROID MEDIA SESSION", color = Cyan, fontSize = 9.sp, letterSpacing = 1.1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (readPermissionState(context).requiredReady) booting = true
                            else EchoRuntime.setLyricsStatus("Required permissions need attention — open ACCESS")
                        },
                        enabled = !running && !booting,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink, disabledContainerColor = PanelSoft, disabledContentColor = Muted)
                    ) { Text(if (booting) "STARTING..." else "START  →", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    Button(
                        onClick = { LyricsOverlayService.stop(context) },
                        enabled = running,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PanelSoft, contentColor = Color.White, disabledContainerColor = PanelSoft.copy(alpha = 0.55f), disabledContentColor = Muted)
                    ) { Text("STOP", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                }
                Text("Starting the engine creates a transparent, non-touchable overlay. Your game taps should pass through it.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
            }
        }

        AnimatedVisibility(visible = booting || ready) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF070B12)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Cyan.copy(alpha = 0.28f), RoundedCornerShape(18.dp))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ECHOFRAME / BOOT CONSOLE", color = Cyan, fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                    logs.forEach { line -> Text(line, color = if (line.contains("SUCCEED") || line.contains("ACTIVE") || line.contains("READY")) Green else Muted, fontSize = 10.sp) }
                    if (ready) {
                        Text("LYRICS READY", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.85f)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NOW PLAYING", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
                Text(trackTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Divider(color = Color.White.copy(alpha = 0.07f))
                Text(lyricsStatus, color = Cyan, fontSize = 11.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("PORTRAIT", "AUTO FIT", Modifier.weight(1f))
            InfoTile("LANDSCAPE", "AUTO FIT", Modifier.weight(1f))
        }
        Text("Tip: open Spotify or YouTube Music, start a track, then switch to your game. Media apps that expose an Android playback session can be detected.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
        if (!access.requiredReady) Text("Some required permissions are missing. Open ACCESS before starting.", color = Color(0xFFFFC27D), fontSize = 10.sp)
        if (prefs.overlayEnabled && !running) Text("The previous engine session has ended. Press START to launch it again.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun InfoTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = PanelSoft), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
            Text(value, color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var style by remember { mutableStateOf(prefs.loadStyle()) }

    fun update(newStyle: OverlayStyle) {
        style = newStyle
        prefs.saveStyle(newStyle)
        AppPrefs.notifyStyleChanged(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(
            top = 22.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text("02 / CUSTOMIZATION", color = Cyan, fontSize = 9.sp, letterSpacing = 1.8.sp)
        Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Tune the way every lyric enters, floats and disappears.", color = Muted, fontSize = 12.sp)

        SettingGroup("APPEARANCE") {
            ColorPickerRow("Text color", style.textColor) { update(style.copy(textColor = it)) }
            ColorPickerRow("Glow color", style.glowColor) { update(style.copy(glowColor = it)) }
            ColorPickerRow("Particle color", style.particleColor) { update(style.copy(particleColor = it)) }
            SettingSlider("Text size", style.textSizeSp, 14f..42f, "${style.textSizeSp.toInt()} sp") { update(style.copy(textSizeSp = it)) }
            SettingSlider("Text opacity", style.textOpacity, 0.2f..1f, "${(style.textOpacity * 100).toInt()}%") { update(style.copy(textOpacity = it)) }
            SettingSlider("Glow intensity", style.glowRadius, 0f..36f, "${style.glowRadius.toInt()}") { update(style.copy(glowRadius = it)) }
        }

        SettingGroup("MOTION & PARTICLES") {
            SettingSlider("Floating speed", style.floatSpeed, 0.35f..2.5f, "${String.format("%.2f", style.floatSpeed)}x") { update(style.copy(floatSpeed = it)) }
            SettingSlider("Particle count", style.particleCount.toFloat(), 4f..64f, "${style.particleCount}") { update(style.copy(particleCount = it.toInt())) }
            SettingSlider("Maximum text objects", style.maxObjects.toFloat(), 2f..8f, "${style.maxObjects}") { update(style.copy(maxObjects = it.toInt())) }
            SettingToggle("Balloon-pop exit", "Burst effect when a text object leaves the screen", style.popEffect) { update(style.copy(popEffect = it)) }
            SettingToggle("Particle burst", "Emit particles after the pop animation", style.particlesEnabled) { update(style.copy(particlesEnabled = it)) }
            SettingToggle("Random positioning", "Select a different safe position for each phrase", style.randomPosition) { update(style.copy(randomPosition = it)) }
            SettingToggle("Collision avoidance", "Try alternate positions when text blocks overlap", style.collisionAvoidance) { update(style.copy(collisionAvoidance = it)) }
            SettingToggle("Split long lyric lines", "Turn long lines into smaller floating phrases", style.splitLongLines) { update(style.copy(splitLongLines = it)) }
            SettingToggle("Hide while music is paused", "Stop displaying lyrics until playback resumes", style.hideOnPause) { update(style.copy(hideOnPause = it)) }
        }

        SettingGroup("SYSTEM") {
            Text("Orientation adapts to the current display size automatically. The overlay is always non-touchable; Android security rules and some game engines may still vary by device.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
            Button(onClick = { update(OverlayStyle.DEFAULT) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PanelSoft, contentColor = Color.White)) {
                Text("RESET ALL VISUAL SETTINGS", fontSize = 10.sp, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Violet, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ColorPickerRow(title: String, selected: Int, onSelect: (Int) -> Unit) {
    val palette = listOf(0xFFF4FAFF.toInt(), 0xFF66D9FF.toInt(), 0xFFB39AFF.toInt(), 0xFFFF88B8.toInt(), 0xFFFFD27A.toInt(), 0xFF83E6B4.toInt(), 0xFFFFFFFF.toInt())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            palette.forEach { colorValue ->
                val active = selected == colorValue
                Box(
                    Modifier.size(29.dp).clip(CircleShape).background(Color(colorValue))
                        .border(if (active) 2.dp else 1.dp, if (active) Cyan else Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { onSelect(colorValue) },
                    contentAlignment = Alignment.Center
                ) { if (active) Text("✓", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 12.sp)
            Text(detail, color = Muted, fontSize = 9.sp, lineHeight = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AboutScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(
            top = 22.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("03 / PROJECT INFORMATION", color = Cyan, fontSize = 9.sp, letterSpacing = 1.8.sp)
        Text("About ECHOFRAME", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoMark(92.dp)
                Text("ECHOFRAME", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("WORDS THAT MOVE WITH YOUR MUSIC", color = Cyan, fontSize = 8.sp, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
                FloatingPreview()
            }
        }
        AboutSection("WHAT IT DOES", "ECHOFRAME is an experimental Android floating-lyrics engine. It watches supported Android media sessions, looks up line-timestamped lyrics, then animates lyric phrases across the screen while other apps are in use.")
        AboutSection("PHASE 01 · DEMO", "This build focuses on first-run permissions, media-session detection, LRCLIB lookup, local lyric caching, a transparent touch-through overlay, random phrase placement, orientation adaptation and visual settings. It is an early development phase, not a finished public release.")
        AboutSection("LYRICS & ACCURACY", "Synced LRC data is preferred. Plain lyrics may be shown with estimated timing and are labelled as estimated. Some tracks have no available lyrics, are instrumental, or may not expose enough media metadata. ECHOFRAME does not promise complete coverage for every track or app.")
        AboutSection("CREATOR", "Created by TRyWonUF1. Further development is planned for word-level timing, richer particle presets, manual track matching, more media-player compatibility, and additional accessibility and performance tuning.")
        Text("VERSION 0.1.0 · PHASE 01 · ANDROID", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun FloatingPreview() {
    Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF090F1C))) {
        Text("HOW TO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart).padding(start = 19.dp, top = 17.dp))
        Text("REMEMBER", color = Color(0xFF9CCFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp))
        Text("HELLO", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).shadow(12.dp, CircleShape))
        Text("TONIGHT", color = Violet, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 15.dp))
        Text("↑", color = Cyan, fontSize = 17.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 25.dp, bottom = 15.dp))
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Violet, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun LogoMark(diameter: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(diameter)) {
        val canvasSize = this.size
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val radius = canvasSize.minDimension * 0.34f
        val stroke = canvasSize.minDimension
        drawCircle(Brush.sweepGradient(listOf(Cyan, Violet, Cyan)), radius, center, style = Stroke(width = stroke * 0.045f))
        drawArc(
            color = Violet,
            startAngle = 25f,
            sweepAngle = 225f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.25f, center.y - radius * 0.72f),
            size = androidx.compose.ui.geometry.Size(radius * 2.5f, radius * 1.45f),
            style = Stroke(width = stroke * 0.035f, cap = StrokeCap.Round)
        )
        drawLine(Cyan, Offset(center.x - radius * 0.34f, center.y), Offset(center.x - radius * 0.34f, center.y + radius * 0.52f), stroke * 0.07f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(center.x - radius * 0.34f, center.y), Offset(center.x + radius * 0.34f, center.y + radius * 0.52f), stroke * 0.07f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(center.x + radius * 0.34f, center.y + radius * 0.52f), Offset(center.x + radius * 0.34f, center.y - radius * 0.46f), stroke * 0.07f, cap = StrokeCap.Round)
        drawCircle(Cyan, stroke * 0.045f, Offset(center.x + radius * 1.02f, center.y - radius * 0.88f))
    }
}
