package dev.trywonuf1.echoframe.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuStatus {
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int = 4210): Boolean = runCatching {
        if (!isRunning()) return false
        if (hasPermission()) return true
        Shizuku.requestPermission(requestCode)
        false
    }.getOrDefault(false)
}
