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
    fun testTimestampSequenceAndLatenciesOrdering() {
        val now = System.currentTimeMillis()
        val timestamps = DiagnosticTimestamps(
            micStartMs = now,
            firstPcmMs = now + 50,
            speechOnsetMs = now + 200,
            firstPartialMs = now + 400,
            firstWordMs = now + 500,
            finalMs = now + 1200,
            stopMs = now + 1500
        )

        val metrics = RecognitionMetrics(
            timestamps = timestamps,
            micStartupMs = timestamps.firstPcmMs - timestamps.micStartMs,
            speechOnsetDelayMs = timestamps.speechOnsetMs - timestamps.micStartMs,
            firstPartialLatencyMs = timestamps.firstPartialMs - timestamps.micStartMs,
            firstWordLatencyMs = timestamps.firstWordMs - timestamps.micStartMs,
            totalRecognitionTimeMs = timestamps.finalMs - timestamps.micStartMs,
            audioDurationMs = 1500L,
            rtf = (timestamps.finalMs - timestamps.micStartMs).toFloat() / 1500f
        )

        assertTrue(timestamps.micStartMs <= timestamps.firstPcmMs)
        assertTrue(timestamps.firstPcmMs <= timestamps.speechOnsetMs)
        assertTrue(timestamps.speechOnsetMs <= timestamps.firstPartialMs)
        assertTrue(timestamps.firstPartialMs <= timestamps.firstWordMs)
        assertTrue(timestamps.firstWordMs <= timestamps.finalMs)
        assertTrue(timestamps.finalMs <= timestamps.stopMs)

        assertEquals(50L, metrics.micStartupMs)
        assertEquals(200L, metrics.speechOnsetDelayMs)
        assertEquals(400L, metrics.firstPartialLatencyMs)
        assertEquals(500L, metrics.firstWordLatencyMs)
        assertEquals(1200L, metrics.totalRecognitionTimeMs)
        assertEquals(0.80f, metrics.rtf, 0.001f)
    }
}
