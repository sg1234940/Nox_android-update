package com.iskra.vosklab

import com.iskra.vosklab.metrics.TextMetrics
import org.junit.Assert.assertEquals
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
    fun testNormalizeText() {
        val raw = "  Włącz,  Światło!! w   kuchni? "
        val normalized = TextMetrics.normalizeText(raw)
        assertEquals("włącz światło w kuchni", normalized)
    }
}
