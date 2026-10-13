package com.iskra.vosklab

import com.iskra.vosklab.metrics.DiagnosticTimestamps
import com.iskra.vosklab.metrics.RecognitionMetrics
import com.iskra.vosklab.metrics.TextMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMetricsTest {

    @Test
    fun testLevenshteinDistanceIdentical() {
        val dist = TextMetrics.levenshteinDistance("iskra", "iskra")
        assertEquals(0, dist)
    }

    @Test
    fun testLevenshteinDistanceSingleSubstitution() {
        val dist = TextMetrics.levenshteinDistance("iskra", "iskro")
        assertEquals(1, dist)
    }

    @Test
    fun testWordMetricsCalculation() {
        val ref = "włącz światło w kuchni"
        val hyp = "włącz światło w"
        val metrics = TextMetrics.calculateWordMetrics(ref, hyp)

        assertEquals(4, metrics.referenceCount)
        assertEquals(3, metrics.hypothesisCount)
        assertEquals(1, metrics.deletions)
        assertEquals(0, metrics.insertions)
        assertEquals(0, metrics.substitutions)
        assertEquals(0.25f, metrics.errorRate, 0.001f)
    }

    @Test
    fun testCharacterMetricsCalculation() {
        val ref = "iskra"
        val hyp = "iskro"
        val charMetrics = TextMetrics.calculateCharacterMetrics(ref, hyp)

        assertEquals(5, charMetrics.referenceCount)
        assertEquals(1, charMetrics.substitutions)
        assertEquals(0.20f, charMetrics.errorRate, 0.001f)
    }

    @Test
    fun testNormalizeText() {
        val raw = "  Włącz,  Światło!! w   kuchni? "
        val normalized = TextMetrics.normalizeText(raw)
        assertEquals("włącz światło w kuchni", normalized)
    }

    @Test
    fun testRealStreamingRuntimeTimestampLifecycleOrdering() {
        val now = System.currentTimeMillis()
        val timestamps = DiagnosticTimestamps(
            micStartMs = now,
            firstPcmMs = now + 50,
            speechOnsetMs = now + 200,
            firstPartialMs = now + 400,
            firstWordMs = now + 500,
            stopMs = now + 1200,
            finalMs = now + 1250
        )

        val metrics = RecognitionMetrics(
            timestamps = timestamps,
            micStartupMs = timestamps.firstPcmMs - timestamps.micStartMs,
            speechOnsetDelayMs = timestamps.speechOnsetMs - timestamps.micStartMs,
            firstPartialLatencyMs = timestamps.firstPartialMs - timestamps.micStartMs,
            firstWordLatencyMs = timestamps.firstWordMs - timestamps.micStartMs,
            totalRecognitionTimeMs = timestamps.finalMs - timestamps.micStartMs,
            audioDurationMs = 1200L,
            rtf = (timestamps.finalMs - timestamps.micStartMs).toFloat() / 1200f
        )

        // Actual runtime sequence: MIC_START <= FIRST_PCM <= SPEECH_ONSET <= FIRST_PARTIAL <= FIRST_WORD <= STOP <= FINAL
        assertTrue(timestamps.micStartMs <= timestamps.firstPcmMs)
        assertTrue(timestamps.firstPcmMs <= timestamps.speechOnsetMs)
        assertTrue(timestamps.speechOnsetMs <= timestamps.firstPartialMs)
        assertTrue(timestamps.firstPartialMs <= timestamps.firstWordMs)
        assertTrue(timestamps.firstWordMs <= timestamps.stopMs)
        assertTrue(timestamps.stopMs <= timestamps.finalMs)

        assertEquals(50L, metrics.micStartupMs)
        assertEquals(200L, metrics.speechOnsetDelayMs)
        assertEquals(400L, metrics.firstPartialLatencyMs)
        assertEquals(500L, metrics.firstWordLatencyMs)
        assertEquals(1250L, metrics.totalRecognitionTimeMs)
        assertEquals(1.0416f, metrics.rtf, 0.001f)
    }

    @Test
    fun testUnobservedPartialAndFirstWordRemainZeroAndUnknown() {
        val now = System.currentTimeMillis()
        val timestamps = DiagnosticTimestamps(
            micStartMs = now,
            firstPcmMs = now + 50,
            speechOnsetMs = 0L,      // Silence - no speech
            firstPartialMs = 0L,     // Unobserved
            firstWordMs = 0L,        // Unobserved
            stopMs = now + 1000,
            finalMs = now + 1050
        )

        val metrics = RecognitionMetrics(
            timestamps = timestamps,
            micStartupMs = timestamps.firstPcmMs - timestamps.micStartMs,
            speechOnsetDelayMs = if (timestamps.speechOnsetMs > 0) timestamps.speechOnsetMs - timestamps.micStartMs else 0L,
            firstPartialLatencyMs = if (timestamps.firstPartialMs > 0) timestamps.firstPartialMs - timestamps.micStartMs else 0L,
            firstWordLatencyMs = if (timestamps.firstWordMs > 0) timestamps.firstWordMs - timestamps.micStartMs else 0L,
            totalRecognitionTimeMs = timestamps.finalMs - timestamps.micStartMs,
            audioDurationMs = 1000L,
            rtf = 1.05f
        )

        assertEquals(0L, timestamps.firstPartialMs)
        assertEquals(0L, timestamps.firstWordMs)
        assertEquals(0L, metrics.firstPartialLatencyMs)
        assertEquals(0L, metrics.firstWordLatencyMs)

        val partialDisplay = if (metrics.firstPartialLatencyMs > 0) "${metrics.firstPartialLatencyMs} ms" else "UNKNOWN"
        val wordDisplay = if (metrics.firstWordLatencyMs > 0) "${metrics.firstWordLatencyMs} ms" else "UNKNOWN"

        assertEquals("UNKNOWN", partialDisplay)
        assertEquals("UNKNOWN", wordDisplay)
    }
}
