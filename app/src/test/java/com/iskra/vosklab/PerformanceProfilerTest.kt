package com.iskra.vosklab

import com.iskra.vosklab.metrics.PerformanceProfiler
import com.iskra.vosklab.metrics.RecordingSessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceProfilerTest {

    @Test
    fun testCalculateRealTimeFactor() {
        val processingTimeMs = 150L
        val audioDurationMs = 1000L
        val rtf = PerformanceProfiler.calculateRealTimeFactor(processingTimeMs, audioDurationMs)
        assertEquals(0.15f, rtf, 0.0001f)
    }

    @Test
    fun testStreamingProcessingRtfCalculationWithSessionState() {
        val state = RecordingSessionState()

        // Simulate 3 streaming calls to acceptWaveform each taking 10ms CPU processing time
        state.addVoskProcessingTime(10L)
        state.addVoskProcessingTime(10L)
        state.addVoskProcessingTime(10L)

        // Simulate getFinalResult taking 20ms CPU processing time
        state.addVoskProcessingTime(20L)

        val totalProcessingMs = state.voskProcessingTimeMs
        assertEquals(50L, totalProcessingMs)

        val audioDurationMs = 2000L
        val streamingRtf = PerformanceProfiler.calculateRealTimeFactor(totalProcessingMs, audioDurationMs)

        // 50ms processing / 2000ms audio = 0.025 RTF
        assertEquals(0.025f, streamingRtf, 0.0001f)
    }
}
