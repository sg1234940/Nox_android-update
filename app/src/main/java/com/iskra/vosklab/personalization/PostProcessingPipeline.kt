package com.iskra.vosklab.personalization

import android.content.Context
import com.iskra.vosklab.metrics.TextMetrics
import org.json.JSONArray
import org.json.JSONObject

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
    var config: PersonalizationConfig = PersonalizationConfig()
) {

    init {
        context?.let { loadFromStorage(it) }
    }

    fun saveToStorage(context: Context) {
        val prefs = context.getSharedPreferences("iskra_postprocessing_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean("enabled", config.enabled)

        val dictArray = JSONArray()
        config.customDictionary.forEach { rule ->
            dictArray.put(JSONObject().apply {
                put("trigger", rule.trigger)
                put("replacement", rule.replacement)
            })
        }
        editor.putString("customDictionary", dictArray.toString())

        val nounArray = JSONArray()
        config.properNouns.forEach { noun ->
            nounArray.put(JSONObject().apply {
                put("word", noun.word)
                put("capitalizedWord", noun.capitalizedWord)
            })
        }
        editor.putString("properNouns", nounArray.toString())

        editor.apply()
    }

    fun loadFromStorage(context: Context) {
        val prefs = context.getSharedPreferences("iskra_postprocessing_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("enabled")) return

        val enabled = prefs.getBoolean("enabled", true)

        val dictList = mutableListOf<DictionaryReplacement>()
        val dictJsonStr = prefs.getString("customDictionary", null)
        if (!dictJsonStr.isNullOrBlank()) {
            val dictArray = JSONArray(dictJsonStr)
            for (i in 0 until dictArray.length()) {
                val obj = dictArray.getJSONObject(i)
                dictList.add(DictionaryReplacement(obj.getString("trigger"), obj.getString("replacement")))
            }
        } else {
            dictList.addAll(config.customDictionary)
        }

        val nounList = mutableListOf<ProperNounRule>()
        val nounJsonStr = prefs.getString("properNouns", null)
        if (!nounJsonStr.isNullOrBlank()) {
            val nounArray = JSONArray(nounJsonStr)
            for (i in 0 until nounArray.length()) {
                val obj = nounArray.getJSONObject(i)
                nounList.add(ProperNounRule(obj.getString("word"), obj.getString("capitalizedWord")))
            }
        } else {
            nounList.addAll(config.properNouns)
        }

        config = config.copy(
            enabled = enabled,
            customDictionary = dictList,
            properNouns = nounList
        )
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
