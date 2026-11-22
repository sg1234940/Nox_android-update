package pl.iskra.avatar

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class JointPoseTest {
    @Test fun firstCommandBlendsFromVisibleRestingArmAndBlinkReturnsToOpen() {
        val p=JointPose()
        p.set(mapOf("RightArm" to JointAngles(35f,0f,0f)), 400, 0,
            mapOf("RightArm" to JointAngles(25f,0f,0f)))
        assertEquals(30f,p.at(200_000_000).getValue("RightArm").x,.001f)
        assertEquals(0f,naturalBlink(0),0f)
        assertEquals(1f,naturalBlink(3_835_000_000L),0f)
        assertEquals(0f,naturalBlink(4_000_000_000L),0f)
    }

    @Test fun interruptedTransitionStartsFromItsCurrentPose() {
        val p=JointPose()
        p.set(mapOf("Head" to JointAngles(0f,40f,0f)),1000,0)
        assertEquals(20f,p.at(500_000_000).getValue("Head").y,.001f)
        p.set(mapOf("Head" to JointAngles.ZERO,"RightHand" to JointAngles(20f,0f,0f)),500,500_000_000)
        assertEquals(10f,p.at(750_000_000).getValue("Head").y,.001f)
        assertEquals(20f,p.at(1_000_000_000).getValue("RightHand").x,.001f)
        p.clear();assertTrue(p.at(2_000_000_000).isEmpty())
    }

    @Test fun additiveRotationPreservesJointPivotAndLocalScale() {
        val base=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,1f,2f,3f,1f)
        val out=FloatArray(16)
        rotateJoint(base,JointAngles(0f,90f,0f),out)
        assertArrayEquals(floatArrayOf(1f,2f,3f,1f),out.copyOfRange(12,16),0f)
        assertEquals(-1f,out[2],.0001f)
        rotateJoint(base,JointAngles.ZERO,out)
        assertArrayEquals(base,out,0f)
    }

    @Test fun protocolValidatesFaceAndWholeBodyAtomically() {
        val joint=JSONObject("""{"name":"RightHand","x":15,"y":20,"z":0}""")
        val a=JSONObject().put("action","POSE").put("joints",org.json.JSONArray().put(joint))
            .put("face",JSONObject().put("cheekRaiseLeft",.3)).put("duration_ms",800)
        assertTrue(AvatarCommandProtocol.valid(a))
        assertEquals(JointAngles(15f,20f,0f),AvatarCommandProtocol.joints(a)["RightHand"])
        a.getJSONArray("joints").put(joint)
        assertFalse(AvatarCommandProtocol.valid(a))
        assertFalse(AvatarCommandProtocol.valid(JSONObject("""{"action":"RIG","duration_ms":300}""")))
    }
}
