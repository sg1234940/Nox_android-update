package com.iskra.vosklab

import com.iskra.vosklab.personalization.DictionaryReplacement
import com.iskra.vosklab.personalization.PersonalizationConfig
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.personalization.ProperNounRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PostProcessingPipelineTest {

    @Test
    fun testDictionarySubstitutionAndProperNoun() {
        val config = PersonalizationConfig(
            enabled = true,
            customDictionary = listOf(DictionaryReplacement("wloncz", "włącz")),
            properNouns = listOf(ProperNounRule("iskra", "Iskra"))
        )
        val pipeline = PostProcessingPipeline(config = config)

        val rawTranscript = "wloncz iskra glosowa"
        val processedText = pipeline.applyPostProcessing(rawTranscript)

        // RAW transcript remains untouched, processed text receives post-corrections
        assertEquals("wloncz iskra glosowa", rawTranscript)
        assertEquals("włącz Iskra glosowa", processedText)
        assertNotEquals(rawTranscript, processedText)
    }

    @Test
    fun testDisabledPipeline() {
        val config = PersonalizationConfig(enabled = false)
        val pipeline = PostProcessingPipeline(config = config)

        val rawTranscript = "wloncz iskra"
        val processed = pipeline.applyPostProcessing(rawTranscript)

        assertEquals("wloncz iskra", processed)
    }
}
