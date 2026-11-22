package pl.iskra.avatar.test

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.iskra.avatar.LabPresentation
import pl.iskra.avatar.LabVoice

class LabReplyVoiceTest {
    private val voice = LabVoice.Voice("local-" + "a".repeat(32), "Żeński", "pl")
    private val profile = LabPresentation(voiceId = voice.id)
    private fun final(scale: Any = 1.1) = JSONObject().put("schema", "promyk.final/v2").put("text", "Cześć").put("voice_plan_candidate",
        JSONObject().put("schema", "promyk.voice/v2").put("voice_identity", "ISKRA").put("mode", "SPEAK")
            .put("voice_package_id", voice.id).put("language", "pl").put("text", "Cześć")
            .put("voice_motion", JSONObject().put("length_scale", scale)))

    @Test fun typingCanSpeakWithoutMicrophoneOrRecognizer() {
        val turn = requireNotNull(LabReplyVoice.forTurn(profile, listOf(voice), handsFree = false))
        assertTrue(turn.context().getBoolean("synthesis_enabled"))
        assertEquals(voice.id, turn.context().getString("default_package_id"))
        assertEquals("VOICE_CONVERSATION", turn.context().getString("conversation_mode"))
        assertEquals(LabReplyVoice.Speech(voice.id, 1.1f), turn.speech("Cześć", final()))
    }
    @Test fun mutedTextMissingVoiceAndUnconfirmedSpeechStaySilent() {
        val muted = profile.copy(speakTypedReplies = false)
        assertNull(LabReplyVoice.forTurn(muted, listOf(voice), false))
        assertNull(LabReplyVoice.forTurn(profile, emptyList(), false))
        val turn = requireNotNull(LabReplyVoice.forTurn(muted, listOf(voice), true))
        assertNull(turn.speech("Cześć", null))
        assertNull(turn.speech("Inny tekst", final()))
        assertNull(turn.speech("Cześć", final().put("schema", "promyk.final/v1")))
        assertNull(turn.speech("Cześć", final(1.21)))
        assertNull(turn.speech("Cześć", final("1")))
    }
    @Test fun ordinaryFinalReplyUsesChosenVoiceButDirectivesAndMismatchedFinalsStaySilent() {
        val turn = requireNotNull(LabReplyVoice.forTurn(profile, listOf(voice), false))
        val plain = JSONObject().put("schema", "promyk.final/v1").put("text", "Cześć")
        assertEquals(LabReplyVoice.Speech(voice.id, 1f), turn.speech("Cześć", plain))
        assertNull(turn.speech("Inna tura", plain))
        for (text in listOf("[ISKRA_TERMUX] szczegóły", "a".repeat(2001), "a\u0000b")) {
            assertNull(turn.speech(text, plain.put("text", text)))
        }
    }
    @Test fun upperTempoEndpointStaysInsideServerRange() {
        val turn = requireNotNull(LabReplyVoice.forTurn(profile.copy(lengthScale = 1.2f), listOf(voice), false))
        assertEquals(1.2, turn.context().getDouble("preferred_length_scale"), 0.0)
    }
}
