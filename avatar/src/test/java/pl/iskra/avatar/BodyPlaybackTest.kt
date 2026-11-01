package pl.iskra.avatar
import org.junit.Assert.*
import org.junit.Test

class BodyPlaybackTest {
    @Test fun defaultPoseIsActuallyStaticNotAnIdleFrame() {
        val c = AvatarController(); c.bindConversation("a"); c.beginTurn(1)
        assertNull(c.frame(.1f).animation)
        val b = BodyPlayback()
        assertTrue(b.frame(null, .1f, false).restoreBindPose)
        repeat(50) { val f = b.frame(null, .1f, false); assertNull(f.animation); assertEquals(0f, f.seconds, 0f) }
    }
    @Test fun stoppingClipRestoresBindPose() {
        val b = BodyPlayback(); b.frame("HumanHappyIdle", 0f, false)
        repeat(20) { b.frame("HumanHappyIdle", .1f, false) }
        val f = b.frame(null, .1f, false)
        assertTrue(f.restoreBindPose); assertEquals(0f, f.seconds, 0f)
    }
    @Test fun freezeHoldsCurrentTimeThenResumesWithoutJumping() {
        val b = BodyPlayback(); b.frame("HumanHappyIdle", 0f, false)
        val before = b.frame("HumanHappyIdle", .1f, false)
        repeat(100) { assertEquals(before.seconds, b.frame("HumanHappyIdle", .1f, true).seconds, 0f) }
        assertEquals(before.seconds+.1f, b.frame("HumanHappyIdle", .1f, false).seconds, .00001f)
    }
    @Test fun faceResetReturnsToStaticAndClearsFreeze() {
        val c = AvatarController(); c.bindConversation("a"); c.beginTurn(1)
        c.dispatch(AvatarController.Event("a",1,0,AvatarController.Command.Animation("HumanHappyIdle")))
        c.dispatch(AvatarController.Event("a",1,1,AvatarController.Command.BodyPaused(true)))
        assertTrue(c.snapshot().bodyPaused)
        c.dispatch(AvatarController.Event("a",1,2,AvatarController.Command.Reset))
        assertFalse(c.snapshot().bodyPaused); assertNull(c.snapshot().animation)
    }
}
