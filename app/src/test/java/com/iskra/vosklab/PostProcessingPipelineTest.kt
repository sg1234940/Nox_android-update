package com.iskra.vosklab

import com.iskra.vosklab.personalization.DictionaryReplacement
import com.iskra.vosklab.personalization.PersonalizationConfig
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.personalization.ProperNounRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostProcessingPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun testMechanismAPersistenceAcrossRestart() {
        val storageDir = tempFolder.newFolder("config_test_dir")

        // First instance - add user rules and save
        val pipeline1 = PostProcessingPipeline(storageDir = storageDir)
        val updatedConfig = pipeline1.config.copy(
            customDictionary = pipeline1.config.customDictionary + DictionaryReplacement("siema", "Cześć"),
            properNouns = pipeline1.config.properNouns + ProperNounRule("promyk", "Promyk")
        )
        pipeline1.config = updatedConfig
        pipeline1.saveToStorage()

        // Second instance (simulating app restart) - load from same storage
        val pipeline2 = PostProcessingPipeline(storageDir = storageDir)

        val foundDict = pipeline2.config.customDictionary.any { it.trigger == "siema" && it.replacement == "Cześć" }
        val foundNoun = pipeline2.config.properNouns.any { it.word == "promyk" && it.capitalizedWord == "Promyk" }

        assertEquals(true, foundDict)
        assertEquals(true, foundNoun)

        val processed = pipeline2.applyPostProcessing("siema promyk")
        assertEquals("Cześć Promyk", processed)
    }
}
