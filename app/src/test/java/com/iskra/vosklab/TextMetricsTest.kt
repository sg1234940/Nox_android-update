package com.iskra.vosklab

import com.iskra.vosklab.metrics.RecordingSessionState
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
    fun testRecordingSessionStateLifecycleContractAndOrdering() {
        val state = RecordingSessionState()

        val t0 = 1000L
        val t1 = 1050L
        val t2 = 1200L
        val t3 = 1400L
        val t4 = 1500L
        val t5 = 2200L
        val t6 = 2250L

        state.recordMicStart(t0)
        state.recordFirstPcm(t1)
        state.recordSpeechOnset(t2)
        state.recordFirstPartial(t3)
        state.recordFirstWord(t4)
        state.recordStop(t5)
        state.recordFinal(t6)

        val ts = state.getDiagnosticTimestamps()

        // Contract check: MIC_START <= FIRST_PCM <= SPEECH_ONSET <= FIRST_PARTIAL <= FIRST_WORD <= STOP <= FINAL
        assertTrue(ts.micStartMs <= ts.firstPcmMs)
        assertTrue(ts.firstPcmMs <= ts.speechOnsetMs)
        assertTrue(ts.speechOnsetMs <= ts.firstPartialMs)
        assertTrue(ts.firstPartialMs <= ts.firstWordMs)
        assertTrue(ts.firstWordMs <= ts.stopMs)
        assertTrue(ts.stopMs <= ts.finalMs)

        assertEquals(t0, ts.micStartMs)
        assertEquals(t1, ts.firstPcmMs)
        assertEquals(t2, ts.speechOnsetMs)
        assertEquals(t3, ts.firstPartialMs)
        assertEquals(t4, ts.firstWordMs)
        assertEquals(t5, ts.stopMs)
        assertEquals(t6, ts.finalMs)
    }

    @Test
    fun testRecordingSessionStateFirstTimestampsSetOnlyOnce() {
        val state = RecordingSessionState()

        state.recordFirstPartial(100L)
        state.recordFirstPartial(200L)

        state.recordFirstWord(150L)
        state.recordFirstWord(300L)

        val ts = state.getDiagnosticTimestamps()
        assertEquals(100L, ts.firstPartialMs)
        assertEquals(150L, ts.firstWordMs)
    }

    @Test
    fun testRecordingSessionStateUnobservedRemainZero() {
        val state = RecordingSessionState()

        state.recordMicStart(1000L)
        state.recordFirstPcm(1050L)
        state.recordStop(2000L)
        state.recordFinal(2050L)

        val ts = state.getDiagnosticTimestamps()

        assertEquals(0L, ts.speechOnsetMs)
        assertEquals(0L, ts.firstPartialMs)
        assertEquals(0L, ts.firstWordMs)

        val partialLat = if (ts.firstPartialMs > 0 && ts.micStartMs > 0) "${ts.firstPartialMs - ts.micStartMs} ms" else "UNKNOWN"
        val wordLat = if (ts.firstWordMs > 0 && ts.micStartMs > 0) "${ts.firstWordMs - ts.micStartMs} ms" else "UNKNOWN"

        assertEquals("UNKNOWN", partialLat)
        assertEquals("UNKNOWN", wordLat)
    }

    @Test
    fun testFirstPartialFirstWordPreservedEvenIfUiUpdateIsDelayed() {
        val state = RecordingSessionState()

        // Simulate audio recognition thread finding partial and word
        state.recordMicStart(1000L)
        state.recordFirstPcm(1050L)
        state.recordFirstPartial(1300L)
        state.recordFirstWord(1400L)

        // Simulate UI delay (e.g., UI observer hasn't read or UI thread mainHandler.post was queued)
        // UI snapshot taken later
        val snapshot = state.getDiagnosticTimestamps()
        val uiSnapshotFirstPartial = snapshot.firstPartialMs
        val uiSnapshotFirstWord = snapshot.firstWordMs

        assertEquals(1300L, uiSnapshotFirstPartial)
        assertEquals(1400L, uiSnapshotFirstWord)
    }
}
