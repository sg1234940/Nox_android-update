package com.iskra.vosklab.personalization

import com.iskra.vosklab.metrics.TextMetrics

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

class PostProcessingPipeline(var config: PersonalizationConfig = PersonalizationConfig()) {

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
