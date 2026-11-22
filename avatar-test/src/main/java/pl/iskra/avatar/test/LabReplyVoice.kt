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
) {
    companion object {
        fun forTurn(profile: LabPresentation, voices: List<LabVoice.Voice>, handsFree: Boolean): LabReplyVoice? {
            if (!handsFree && !profile.speakTypedReplies) return null
            val available = voices.take(32)
            val selected = available.firstOrNull { it.id == profile.voiceId && it.language == profile.outputLanguage }
                ?: return null
            return LabReplyVoice(available, selected, profile.lengthScale)
        }
    }

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
            .put("synthesis_enabled", true).put("preferred_length_scale", tempo.toDouble().coerceIn(0.8, 1.2))
    }

    data class Speech(val voiceId: String, val tempo: Float)

    /** Called only with a completed response already validated by LabFinality. */
    fun speech(answer: String, final: JSONObject?): Speech? {
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
        return if (scale.toDouble() in 0.8..1.2) Speech(voice.id, scale.toFloat()) else null
    }
}
