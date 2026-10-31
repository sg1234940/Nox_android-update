package pl.iskra.avatar

import org.junit.Assert.*
import org.junit.Test

class AvatarControllerTest {
    private fun fresh() = AvatarController().apply { bindConversation("a"); beginTurn(1) }
    private fun AvatarController.send(seq: Long, command: AvatarController.Command, id: String = "a", turn: Long = 1) =
        dispatch(AvatarController.Event(id, turn, seq, command))
    private val started = AvatarController.Command.SpeechStarted("p")
    private val speaking = AvatarController.Command.SpeechFrame("p", 10, 1f, "viseme_A")
    private fun assertClosed(c: AvatarController) {
        val s = c.frame(.1f)
        assertNull(s.playbackId)
        for (n in AvatarController.VISEMES + "jawOpen") assertEquals(n, 0f, s.weights[AvatarController.MORPHS.indexOf(n)], 0f)
    }
    @Test fun foreignConversationCannotChangeFace() {
        val c = fresh()
        assertEquals(AvatarController.Result.STALE, c.send(0, AvatarController.Command.Weight("jawOpen", 1f), "b"))
        assertClosed(c)
    }
    @Test fun oldTurnCannotChangeCurrentTurn() {
        val c = fresh(); c.beginTurn(2)
        assertEquals(AvatarController.Result.STALE, c.send(99, started))
        assertClosed(c)
    }
    @Test fun unboundControllerRejectsEvents() {
        assertEquals(AvatarController.Result.STALE, AvatarController().send(0, started))
    }
    @Test fun duplicateAndOutOfOrderEventsCannotOverrideState() {
        val c = fresh(); c.send(3, AvatarController.Command.Weight("mouthSmile", .5f))
        for (seq in listOf(2L, 3L)) assertEquals(AvatarController.Result.STALE, c.send(seq, AvatarController.Command.Reset))
        assertEquals(.5f, c.snapshot().weights[10], 0f)
    }
    @Test fun invalidWeightsAreAtomicAndDoNotConsumeSequence() {
        val c = fresh()
        for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f))
            assertEquals(AvatarController.Result.INVALID, c.send(0, AvatarController.Command.Weight("jawOpen", v)))
        assertEquals(AvatarController.Result.INVALID, c.send(0, AvatarController.Command.Weight("unknown", 1f)))
        assertEquals(AvatarController.Result.APPLIED, c.send(0, AvatarController.Command.Weight("jawOpen", .3f)))
        assertEquals(.3f, c.snapshot().weights[9], 0f)
    }
    @Test fun wrongPlaybackAndPositionAreRejected() {
        val c = fresh(); c.send(0, started); c.send(1, speaking)
        assertEquals(AvatarController.Result.STALE, c.send(2, AvatarController.Command.SpeechFrame("old", 50, .5f)))
        assertEquals(AvatarController.Result.INVALID, c.send(2, AvatarController.Command.SpeechFrame("p", 9, .5f)))
        assertEquals(10L, c.snapshot().positionMs)
        assertEquals(AvatarController.Result.APPLIED, c.send(2, AvatarController.Command.SpeechFrame("p", 20, .5f)))
    }
    @Test fun speechStopClosesMouthImmediatelyEvenWithoutFurtherAudioFrames() {
        val c = fresh(); c.send(0, started); c.send(1, speaking); c.frame(.1f)
        c.send(2, AvatarController.Command.SpeechStopped("p")); assertClosed(c)
    }
    @Test fun interruptionClosesMouthAndRejectsLateAudio() {
        val c = fresh(); c.send(0, started); c.send(1, speaking); c.frame(.1f)
        c.send(2, AvatarController.Command.Interrupted); assertClosed(c)
        assertEquals(AvatarController.Result.STALE, c.send(3, speaking))
    }
    @Test fun backgroundClosesMouthAndRejectsLateAudio() {
        val c = fresh(); c.send(0, started); c.send(1, speaking); c.frame(.1f)
        c.suspendPlayback(); assertClosed(c)
        assertEquals(AvatarController.Result.STALE, c.send(2, speaking))
    }
    @Test fun backgroundRejectsLateStartAndAllStateEventsUntilNewTurn() {
        val c = fresh(); c.suspendPlayback()
        assertEquals(AvatarController.Result.STALE, c.send(10, started))
        assertEquals(AvatarController.Result.STALE, c.send(11, AvatarController.Command.Weight("mouthSmile", 1f)))
        c.beginTurn(2)
        assertEquals(AvatarController.Result.APPLIED, c.send(0, started, turn = 2))
    }
    @Test fun newTurnAndConversationRevokePlayback() {
        val c = fresh(); c.send(0, started); c.send(1, speaking); c.beginTurn(2); assertClosed(c)
        c.send(0, started, turn = 2); c.bindConversation("b"); assertClosed(c)
        assertEquals(AvatarController.Result.STALE, c.send(99, started, turn = 2))
    }
    @Test fun replacingPlaybackRejectsLateStopOfPreviousPlayback() {
        val c = fresh(); c.send(0, started); c.send(1, AvatarController.Command.SpeechStarted("q"))
        assertEquals(AvatarController.Result.STALE, c.send(2, AvatarController.Command.SpeechStopped("p")))
        assertEquals("q", c.snapshot().playbackId)
    }
    @Test fun phaseChangeClearsSpeech() {
        val c = fresh(); c.send(0, started); c.send(1, speaking)
        c.send(2, AvatarController.Command.PhaseChanged(AvatarController.Phase.LISTENING)); assertClosed(c)
        assertEquals(AvatarController.Phase.LISTENING, c.snapshot().phase)
    }
    @Test fun visemesCrossFadeAndRemainBounded() {
        val c = fresh(); c.send(0, started); c.send(1, speaking); c.frame(.1f)
        c.send(2, AvatarController.Command.SpeechFrame("p", 20, 1f, "viseme_O"))
        val s = c.frame(.02f)
        assertTrue(s.weights[AvatarController.MORPHS.indexOf("viseme_A")] > 0)
        assertTrue(s.weights[AvatarController.MORPHS.indexOf("viseme_O")] > 0)
        assertTrue(s.weights.all { it.isFinite() && it in 0f..1f })
    }
    @Test fun snapshotsDoNotExposeInternalArrays() {
        val c = fresh(); c.snapshot().weights.fill(1f); c.frame(.1f).weights.fill(1f)
        assertTrue(c.snapshot().weights.all { it == 0f })
    }
    @Test fun allSevenBodyClipsAnd21MorphsReachController() {
        val c = fresh(); var seq = 0L
        for (name in AvatarController.ANIMATIONS) {
            assertEquals(AvatarController.Result.APPLIED, c.send(seq++, AvatarController.Command.Animation(name)))
            assertEquals(name, c.snapshot().animation)
        }
        for (name in AvatarController.MORPHS) {
            assertEquals(AvatarController.Result.APPLIED, c.send(seq++, AvatarController.Command.Weight(name, .75f)))
            assertEquals(.75f, c.snapshot().weights[AvatarController.MORPHS.indexOf(name)], 0f)
        }
        assertEquals(AvatarController.Result.INVALID, c.send(seq, AvatarController.Command.Animation("unknown")))
    }
    @Test fun badSpeechFrameDoesNotAlterPreviousFrame() {
        val c = fresh(); c.send(0, started); c.send(1, speaking)
        for (e in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, 2f))
            assertEquals(AvatarController.Result.INVALID, c.send(2, AvatarController.Command.SpeechFrame("p", 20, e)))
        assertEquals(AvatarController.Result.INVALID, c.send(2, AvatarController.Command.SpeechFrame("p", 20, 1f, "unknown")))
        assertEquals(.45f, c.snapshot().weights[9], 0f)
    }
    @Test fun resetClearsAllMorphs() {
        val c = fresh(); c.send(0, AvatarController.Command.Weight("mouthSmile", 1f))
        c.send(1, AvatarController.Command.Reset); assertTrue(c.frame(.1f).weights.all { it == 0f })
    }
    @Test(expected = IllegalArgumentException::class) fun negativeDeltaRejected() { fresh().frame(-1f) }
    @Test(expected = IllegalArgumentException::class) fun nanDeltaRejected() { fresh().frame(Float.NaN) }
    @Test(expected = IllegalArgumentException::class) fun cannotReopenOldTurn() { fresh().beginTurn(1) }
    @Test fun combinedOpeningIsCappedWithoutLosingTheIndividualControls() {
        val c = fresh(); var seq = 0L
        for (name in AvatarController.OPENING_GAINS.keys) c.send(seq++, AvatarController.Command.Weight(name, 1f))
        repeat(20) { c.frame(.1f) }
        val s = c.frame(.1f)
        val gain = AvatarController.OPENING_GAINS.entries.sumOf { (name,g) -> (s.weights[AvatarController.MORPHS.indexOf(name)]*g).toDouble() }
        assertTrue(gain <= 1.000001)
        assertTrue(AvatarController.OPENING_GAINS.keys.all { s.weights[AvatarController.MORPHS.indexOf(it)] > 0f })
    }

}
