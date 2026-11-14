package dev.trywonuf1.echoframe.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoRuntime {
    private val mutableEngineRunning = MutableStateFlow(false)
    private val mutableTrackTitle = MutableStateFlow("Waiting for a music session")
    private val mutableLyricsStatus = MutableStateFlow("Ready when music starts")

    val engineRunning = mutableEngineRunning.asStateFlow()
    val trackTitle = mutableTrackTitle.asStateFlow()
    val lyricsStatus = mutableLyricsStatus.asStateFlow()

    fun setRunning(value: Boolean) { mutableEngineRunning.value = value }
    fun setTrack(value: String) { mutableTrackTitle.value = value }
    fun setLyricsStatus(value: String) { mutableLyricsStatus.value = value }
}
