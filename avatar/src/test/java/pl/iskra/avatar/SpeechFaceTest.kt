package pl.iskra.avatar

import org.junit.Assert.*
import org.junit.Test

class SpeechFaceTest {
    private val face = AvatarController().apply { bindConversation("face"); beginTurn(0) }
    private val brow = AvatarController.MORPHS.indexOf("browInnerUp")
    private var seq = 0L
    private var ms = 0L

    private fun send(command: AvatarController.Command) =
        face.dispatch(AvatarController.Event("face", 0, seq++, command))

    private fun play(frames: Int, energy: Float): AvatarController.Snapshot {
        var state = face.frame(0f)
        repeat(frames) {
            ms += 20
            send(AvatarController.Command.SpeechFrame("pcm", ms, energy))
            state = face.frame(.02f)
        }
        return state
    }

    @Test fun phrasesLiftTheUpperFaceAndNodThenSettleOnSustainedAudioAndPauses() {
        send(AvatarController.Command.SpeechStarted("pcm"))
        val silent = play(30, 0f)
        assertEquals(0f, silent.weights[brow], 0f)
        assertEquals(0f, silent.headPitchDegrees, 0f)
        val onset = play(15, .85f)
        assertTrue(onset.weights[brow] > .22f)
        assertTrue(onset.headPitchDegrees in 2f..4f)
        val held = play(100, .85f)
        assertTrue(held.headPitchDegrees < onset.headPitchDegrees * .6f)
        assertTrue(held.weights[brow] < onset.weights[brow])
        val pause = play(60, 0f)
        assertTrue(pause.headPitchDegrees < .01f && pause.weights[brow] < .01f)
        assertTrue(play(15, .85f).headPitchDegrees > 2f)
    }

    @Test fun interruptionRestoresTheCommandedFaceAndRejectsOldSamples() {
        send(AvatarController.Command.Weight("browInnerUp", .4f))
        repeat(20) { face.frame(.1f) }
        send(AvatarController.Command.SpeechStarted("pcm"))
        assertTrue(play(15, 1f).weights[brow] > .5f)
        send(AvatarController.Command.Interrupted)
        val rest = face.frame(.02f)
        assertEquals(.4f, rest.weights[brow], .0001f)
        assertEquals(0f, rest.headPitchDegrees, 0f)
        assertEquals(AvatarController.Result.STALE, send(AvatarController.Command.SpeechFrame("pcm", 500, 1f)))
    }

    @Test fun frozenBodyAndDeliberatelyLoweredBrowsRetainTheirPose() {
        send(AvatarController.Command.BodyPaused(true))
        send(AvatarController.Command.Weight("browDownLeft", 1f))
        repeat(20) { face.frame(.1f) }
        send(AvatarController.Command.SpeechStarted("pcm"))
        val state = play(15, .9f)
        assertEquals(0f, state.weights[brow], .0001f)
        assertEquals(0f, state.headPitchDegrees, 0f)
        assertTrue(state.weights[AvatarController.MORPHS.indexOf("jawOpen")] > .3f)
    }

    @Test fun stalledPlaybackClockDoesNotSustainFacialEmphasis() {
        send(AvatarController.Command.SpeechStarted("pcm"))
        assertTrue(play(12, .9f).headPitchDegrees > 1f)
        repeat(80) {
            send(AvatarController.Command.SpeechFrame("pcm", ms, .9f))
            face.frame(.02f)
        }
        assertTrue(face.frame(0f).headPitchDegrees < .01f)
    }

    @Test fun jointPitchKeepsThePivotAndIsRelativeToTheSampledClip() {
        val base = floatArrayOf(0f,1f,0f,0f, -1f,0f,0f,0f, 0f,0f,1f,0f, 4f,5f,6f,1f)
        val out = FloatArray(16)
        repeat(150) { pitchHead(base, 90f, out) }
        assertArrayEquals(floatArrayOf(0f,1f,0f,0f, 0f,0f,1f,0f, 1f,0f,0f,0f, 4f,5f,6f,1f), out, .00001f)
        pitchHead(base, 0f, out)
        assertArrayEquals(base, out, 0f)
    }
}
