package pl.iskra.avatar.test

import org.json.JSONArray
import org.json.JSONObject
import pl.iskra.avatar.LabPresentation
import pl.iskra.avatar.LabVoice

/** The same catalogue is used to request speech and admit the resulting plan for this turn. */
internal class LabReplyVoice private constructor(
    private val voices: List<LabVoice.Voice>,
    val selected: LabVoice.Voice,
    val tempo: Float,
    private val expressive: Boolean,
) {
    companion object {
        fun forTurn(profile: LabPresentation, voices: List<LabVoice.Voice>, handsFree: Boolean): LabReplyVoice? {
            if (!handsFree && !profile.speakTypedReplies) return null
            val available = voices.take(32)
            val selected = available.firstOrNull { it.id == profile.voiceId && it.language == profile.outputLanguage }
                ?: return null
            return LabReplyVoice(available, selected, profile.lengthScale, profile.expressiveSpeech)
        }
    }

    private val supportsSentences = expressive && selected.supportsSentenceExpression

    fun context(): JSONObject {
        val packages = JSONArray()
        val defaults = JSONObject()
        for (voice in voices) {
            packages.put(JSONObject().put("voice_package_id", voice.id).put("language", voice.language)
                .put("length_scale_min", 0.8).put("length_scale_max", 1.2))
            if (!defaults.has(voice.language)) defaults.put(voice.language, voice.id)
        }
        defaults.put(selected.language, selected.id)
        return JSONObject().put("schema", "promyk.context/v4").put("voice_identity", "ISKRA")
            .put("packages", packages).put("default_package_id", selected.id).put("language_defaults", defaults)
            .put("active_language", selected.language).put("conversation_mode", "VOICE_CONVERSATION")
            .put("synthesis_enabled", true).put("preferred_length_scale", tempo.toDouble().coerceIn(0.8, 1.2)).apply {
                if (expressive) put("expressive_tempo", true)
                if (supportsSentences) {
                    put("sentence_phrases", true)
                    put("phrase_limits", JSONObject().put("tempo_steps", 4).put("pause_delta_ms", 400).put("attenuation_db", 6))
                }
            }
    }

    data class Speech(val voiceId: String, val tempo: Float, val phrases: List<LabVoice.Phrase> = emptyList())

    // Offsets address the visible UTF-16 reply. Reject the entire phrase plan
    // together and let Piper read the final text using its ordinary prosody.
    private fun phrases(raw: JSONArray?, answer: String): List<LabVoice.Phrase> {
        if (raw == null || raw.length() !in 1..6) return emptyList()
        val boundaries = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).apply { setText(answer) }
        var previous = 0
        return runCatching {
            List(raw.length()) { i ->
                val row = raw.getJSONObject(i)
                val hasPause = row.has("pause_after_ms")
                require(row.keys().asSequence().toSet() == setOf("end","length_scale","gain_db") +
                    if (hasPause) setOf("pause_after_ms") else emptySet())
                fun number(key: String) = (row.get(key) as Number).toString().toBigDecimal()
                val end = number("end").intValueExact()
                require(end in previous+1..answer.length && boundaries.isBoundary(end) && answer.substring(previous,end).isNotBlank())
                previous = end
                val scale = number("length_scale").toDouble()
                val gain = number("gain_db").toDouble()
                require(scale in .8..1.2 && kotlin.math.abs(scale - tempo) <= .200001 && gain in -6.0..0.0)
                val pause = if (hasPause) number("pause_after_ms").intValueExact().also { require(i < raw.length()-1 && it in 0..600) } else null
                LabVoice.Phrase(end, scale.toFloat(), gain.toFloat(), pause)
            }.also { require(previous == answer.length) }
        }.getOrDefault(emptyList())
    }

    /** Called only with a completed response already validated by LabFinality. */
    fun speech(answer: String, final: JSONObject?, phrasesNegotiated: Boolean = false): Speech? {
        if (final == null || final.opt("text") != answer || answer.isBlank() || answer.length > 2000 ||
            answer.any { it.isISOControl() && it != '\n' && it != '\t' } ||
            "[ISKRA_" in answer || "[/ISKRA_" in answer || "<<<ISKRA_" in answer || "<<<END_ISKRA_" in answer) return null
        if (final.optString("schema") == "promyk.final/v1" && final.isNull("voice_plan_candidate")) {
            return Speech(selected.id, tempo)
        }
        if (final.optString("schema") != "promyk.final/v2") return null
        val plan = final.optJSONObject("voice_plan_candidate") ?: return null
        if (plan.optString("schema") != "promyk.voice/v2" || plan.optString("voice_identity") != "ISKRA" ||
            plan.optString("mode") != "SPEAK" || plan.opt("text") != answer) return null
        val voice = voices.firstOrNull { it.id == plan.opt("voice_package_id") && it.language == plan.opt("language") }
            ?: return null
        val scale = plan.optJSONObject("voice_motion")?.opt("length_scale") as? Number ?: return null
        if (scale.toDouble() !in 0.8..1.2) return null
        val styles = if (supportsSentences && phrasesNegotiated && voice == selected)
            phrases(plan.getJSONObject("voice_motion").optJSONArray("phrases"), answer) else emptyList()
        return Speech(voice.id, scale.toFloat(), styles)
    }
}
