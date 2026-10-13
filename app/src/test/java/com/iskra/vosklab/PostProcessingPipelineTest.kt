package com.iskra.vosklab

import com.iskra.vosklab.personalization.DictionaryReplacement
import com.iskra.vosklab.personalization.PersonalizationConfig
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.personalization.ProperNounRule
import org.junit.Assert.assertEquals
import org.junit.Test

class PostProcessingPipelineTest {

    @Test
    fun testDictionarySubstitutionAndProperNoun() {
        val config = PersonalizationConfig(
            enabled = true,
            customDictionary = listOf(DictionaryReplacement("wloncz", "włącz")),
            properNouns = listOf(ProperNounRule("iskra", "Iskra"))
        )
        val pipeline = PostProcessingPipeline(config)

        val raw = "wloncz iskra glosowa"
        val processed = pipeline.applyPostProcessing(raw)

        assertEquals("włącz Iskra glosowa", processed)
    }

    @Test
    fun testDisabledPipeline() {
        val config = PersonalizationConfig(enabled = false)
        val pipeline = PostProcessingPipeline(config)

        val raw = "wloncz iskra"
        val processed = pipeline.applyPostProcessing(raw)

        assertEquals("wloncz iskra", processed)
    }
}
