package com.iskra.vosklab.metrics

import android.os.SystemClock

data class MemoryStats(
    val usedMemoryMb: Float,
    val maxMemoryMb: Float,
    val totalMemoryMb: Float,
    val freeMemoryMb: Float
)

object PerformanceProfiler {

    private var micStartTime: Long = 0L
    private var listeningReadyTime: Long = 0L
    private var sttStartTime: Long = 0L
    private var sttEndTime: Long = 0L

    fun markMicStart() {
        micStartTime = SystemClock.elapsedRealtime()
    }

    fun markListeningReady(): Long {
        listeningReadyTime = SystemClock.elapsedRealtime()
        return listeningReadyTime - micStartTime
    }

    fun markSttProcessingStart() {
        sttStartTime = SystemClock.elapsedRealtime()
    }

    fun markSttProcessingEnd(): Long {
        sttEndTime = SystemClock.elapsedRealtime()
        return sttEndTime - sttStartTime
    }

    fun calculateRealTimeFactor(processingTimeMs: Long, audioDurationMs: Long): Float {
        if (audioDurationMs <= 0) return 0.0f
        return processingTimeMs.toFloat() / audioDurationMs.toFloat()
    }

    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemory = totalMemory - freeMemory

        val mbFactor = 1024f * 1024f
        return MemoryStats(
            usedMemoryMb = usedMemory / mbFactor,
            maxMemoryMb = maxMemory / mbFactor,
            totalMemoryMb = totalMemory / mbFactor,
            freeMemoryMb = freeMemory / mbFactor
        )
    }
}
