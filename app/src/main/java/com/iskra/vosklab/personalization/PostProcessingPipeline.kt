package com.iskra.vosklab.personalization

import android.content.Context
import com.google.gson.Gson
import com.iskra.vosklab.metrics.TextMetrics
import java.io.File

data class DictionaryReplacement(
    val trigger: String,
    val replacement: String
)

data class ProperNounRule(
    val word: String,
    val capitalizedWord: String
)

data class PersonalizationConfig(
    val enabled: Boolean = true,
    val customDictionary: List<DictionaryReplacement> = listOf(
        DictionaryReplacement("iskra glosowa", "Iskra"),
        DictionaryReplacement("wloncz", "włącz"),
        DictionaryReplacement("wyłoncz", "wyłącz"),
        DictionaryReplacement("zrobie", "zrobię")
    ),
    val properNouns: List<ProperNounRule> = listOf(
        ProperNounRule("iskra", "Iskra"),
        ProperNounRule("samsung", "Samsung"),
        ProperNounRule("galaxy", "Galaxy"),
        ProperNounRule("polska", "Polska"),
        ProperNounRule("vosk", "Vosk"),
        ProperNounRule("android", "Android")
    ),
    val activeContextDomain: String = "general",
    val contextGrammarPhrases: List<String> = emptyList()
)

class PostProcessingPipeline(
    private val context: Context? = null,
    private val storageDir: File? = null,
    var config: PersonalizationConfig = PersonalizationConfig()
) {

    private val gson = Gson()

    init {
        loadFromStorage()
    }

    fun saveToStorage() {
        if (storageDir != null) {
            val file = File(storageDir, "post_processing_config.json")
            val jsonStr = gson.toJson(config)
            file.writeText(jsonStr)
        } else if (context != null) {
            val prefs = context.getSharedPreferences("iskra_postprocessing_prefs", Context.MODE_PRIVATE)
            val jsonStr = gson.toJson(config)
            prefs.edit().putString("config_json", jsonStr).apply()
        }
    }

    fun loadFromStorage() {
        if (storageDir != null) {
            val file = File(storageDir, "post_processing_config.json")
            if (file.exists()) {
                try {
                    val jsonStr = file.readText()
                    val loaded = gson.fromJson(jsonStr, PersonalizationConfig::class.java)
                    if (loaded != null) {
                        config = loaded
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else if (context != null) {
            val prefs = context.getSharedPreferences("iskra_postprocessing_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("config_json", null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val loaded = gson.fromJson(jsonStr, PersonalizationConfig::class.java)
                    if (loaded != null) {
                        config = loaded
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun applyPostProcessing(rawTranscript: String): String {
        if (!config.enabled || rawTranscript.isBlank()) return rawTranscript

        var processed = rawTranscript.trim()

        for (rule in config.customDictionary) {
            val pattern = "(?i)\\b${Regex.escape(rule.trigger)}\\b".toRegex()
            processed = processed.replace(pattern, rule.replacement)
        }

        for (noun in config.properNouns) {
            val pattern = "(?i)\\b${Regex.escape(noun.word)}\\b".toRegex()
            processed = processed.replace(pattern, noun.capitalizedWord)
        }

        if (config.contextGrammarPhrases.isNotEmpty()) {
            val normalizedProc = TextMetrics.normalizeText(processed)
            for (phrase in config.contextGrammarPhrases) {
                val normalizedPhrase = TextMetrics.normalizeText(phrase)
                if (TextMetrics.calculateWordMetrics(normalizedPhrase, normalizedProc).errorRate < 0.25f) {
                    processed = phrase
                    break
                }
            }
        }

        return processed
    }
}
