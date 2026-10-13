package com.iskra.vosklab.metrics

import kotlin.math.min

data class AlignmentResult(
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val referenceCount: Int,
    val hypothesisCount: Int,
    val errorRate: Float
)

data class DiagnosticTimestamps(
    val micStartMs: Long = 0L,        // MIC_START
    val firstPcmMs: Long = 0L,        // FIRST_PCM
    val speechOnsetMs: Long = 0L,     // SPEECH_ONSET
    val firstPartialMs: Long = 0L,    // FIRST_PARTIAL
    val firstWordMs: Long = 0L,       // FIRST_WORD
    val finalMs: Long = 0L,           // FINAL
    val stopMs: Long = 0L             // STOP
)

data class RecognitionMetrics(
    val timestamps: DiagnosticTimestamps = DiagnosticTimestamps(),
    val micStartupMs: Long = 0L,
    val speechOnsetDelayMs: Long = 0L,
    val firstPartialLatencyMs: Long = 0L,
    val firstWordLatencyMs: Long = 0L,
    val totalRecognitionTimeMs: Long = 0L,
    val audioDurationMs: Long = 0L,
    val rtf: Float = 0f,
    val snrDb: Float = 0f,
    val wer: Float = 0f,
    val cer: Float = 0f
)

object TextMetrics {

    fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9ąćęłńóśźż\n\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    fun calculateWordMetrics(reference: String, hypothesis: String): AlignmentResult {
        val refNormalized = normalizeText(reference)
        val hypNormalized = normalizeText(hypothesis)

        val refWords = if (refNormalized.isEmpty()) emptyList() else refNormalized.split(" ")
        val hypWords = if (hypNormalized.isEmpty()) emptyList() else hypNormalized.split(" ")

        if (refWords.isEmpty()) {
            val ins = hypWords.size
            val rate = if (ins > 0) 1.0f else 0.0f
            return AlignmentResult(
                substitutions = 0,
                deletions = 0,
                insertions = ins,
                referenceCount = 0,
                hypothesisCount = hypWords.size,
                errorRate = rate
            )
        }

        val dp = Array(refWords.size + 1) { IntArray(hypWords.size + 1) }

        for (i in 0..refWords.size) {
            dp[i][0] = i
        }
        for (j in 0..hypWords.size) {
            dp[0][j] = j
        }

        for (i in 1..refWords.size) {
            for (j in 1..hypWords.size) {
                val cost = if (refWords[i - 1] == hypWords[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        var i = refWords.size
        var j = hypWords.size
        var subs = 0
        var dels = 0
        var inss = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && refWords[i - 1] == hypWords[j - 1]) {
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                subs++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                dels++
                i--
            } else {
                inss++
                j--
            }
        }

        val totalErrors = subs + dels + inss
        val wer = totalErrors.toFloat() / refWords.size.toFloat()

        return AlignmentResult(
            substitutions = subs,
            deletions = dels,
            insertions = inss,
            referenceCount = refWords.size,
            hypothesisCount = hypWords.size,
            errorRate = wer
        )
    }

    fun calculateCharacterMetrics(reference: String, hypothesis: String): AlignmentResult {
        return calculateCharMetrics(reference, hypothesis)
    }

    fun calculateCharMetrics(reference: String, hypothesis: String): AlignmentResult {
        val refNormalized = normalizeText(reference)
        val hypNormalized = normalizeText(hypothesis)

        val refChars = refNormalized.toCharArray().toList()
        val hypChars = hypNormalized.toCharArray().toList()

        if (refChars.isEmpty()) {
            val ins = hypChars.size
            val rate = if (ins > 0) 1.0f else 0.0f
            return AlignmentResult(
                substitutions = 0,
                deletions = 0,
                insertions = ins,
                referenceCount = 0,
                hypothesisCount = hypChars.size,
                errorRate = rate
            )
        }

        val dp = Array(refChars.size + 1) { IntArray(hypChars.size + 1) }

        for (i in 0..refChars.size) {
            dp[i][0] = i
        }
        for (j in 0..hypChars.size) {
            dp[0][j] = j
        }

        for (i in 1..refChars.size) {
            for (j in 1..hypChars.size) {
                val cost = if (refChars[i - 1] == hypChars[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        var i = refChars.size
        var j = hypChars.size
        var subs = 0
        var dels = 0
        var inss = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && refChars[i - 1] == hypChars[j - 1]) {
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                subs++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                dels++
                i--
            } else {
                inss++
                j--
            }
        }

        val totalErrors = subs + dels + inss
        val cer = totalErrors.toFloat() / refChars.size.toFloat()

        return AlignmentResult(
            substitutions = subs,
            deletions = dels,
            insertions = inss,
            referenceCount = refChars.size,
            hypothesisCount = hypChars.size,
            errorRate = cer
        )
    }
}
