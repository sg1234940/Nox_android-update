package pl.iskra.avatar

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AvatarCommandProtocolTest {
    @Test fun validCommandsCoverEveryExistingControl() {
        for (name in AvatarController.MORPHS) assertTrue(AvatarCommandProtocol.valid(JSONObject("""{"action":"SET","weights":{"$name":0.5}}""")))
        for (name in AvatarController.ANIMATIONS) assertTrue(AvatarCommandProtocol.valid(JSONObject("""{"action":"SET","animation":"$name"}""")))
        assertTrue(AvatarCommandProtocol.valid(JSONObject("""{"action":"SET","animation":null,"body_paused":true,"camera":{"face":false,"angle":1.2}}""")))
        assertTrue(AvatarCommandProtocol.valid(JSONObject("""{"action":"STATE","preview":false}""")))
        assertTrue(AvatarCommandProtocol.valid(JSONObject("""{"action":"RESET"}""")))
    }
    @Test fun invalidFieldRejectsWholeCommandBeforeControllerMutation() {
        val controller = AvatarController(); controller.bindConversation("lab"); controller.beginTurn(1)
        val before = controller.snapshot()
        for (raw in listOf("""{"action":"SET"}""", """{"action":"SET","weights":{"jawOpen":0.4,"bogus":1}}""",
            """{"action":"SET","weights":{"jawOpen":"0.4"}}""", """{"action":"SET","weights":{"jawOpen":1.01}}""",
            """{"action":"SET","camera":{"face":true,"angle":2}}""", """{"action":"STATE","animation":null}""",
            """{"action":"RESET","preview":1}""", """{"action":"SET","animation":"fake"}""")) {
            assertFalse(raw, AvatarCommandProtocol.valid(JSONObject(raw)))
        }
        assertArrayEquals(before.weights, controller.snapshot().weights, 0f)
    }
    @Test fun renderedAndRequestedPosesAreDifferentAndOpeningBudgetIsPreserved() {
        val c = AvatarController(); c.bindConversation("lab"); c.beginTurn(1)
        c.dispatch(AvatarController.Event("lab",1,0,AvatarController.Command.Weight("jawOpen",1f)))
        c.dispatch(AvatarController.Event("lab",1,1,AvatarController.Command.Weight("viseme_A",1f)))
        val target = AvatarCommandProtocol.pose(c.snapshot(), 1.2f, true)
        val rendered = AvatarCommandProtocol.pose(c.frame(.1f), 1.2f, true)
        assertEquals(1.2, rendered.getJSONObject("camera").getDouble("angle"),0.0)
        assertEquals(1.0,target.getJSONObject("weights").getDouble("jawOpen"),0.0)
        assertTrue(rendered.getJSONObject("weights").getDouble("jawOpen") < 1)
        assertEquals(21,rendered.getJSONObject("weights").length())
    }
    @Test fun replayIsImmutableAndConflictingIdNeverRunsAgain() {
        val cache = AvatarCommandProtocol.Calls(1); val args = JSONObject("""{"action":"STATE"}""")
        val result = JSONObject("""{"operation":"AVATAR","ok":true}""")
        assertNull(cache.cached("a",args)); assertTrue(cache.canExecute()); cache.remember("a",args,result)
        result.put("ok",false)
        assertTrue(cache.cached("a",args)!!.getBoolean("ok"))
        assertFalse(cache.canExecute())
        assertEquals("REPLAY_CONFLICT",cache.cached("a",JSONObject("""{"action":"RESET"}"""))!!.getString("error"))
        assertTrue(cache.cached("a",args)!!.getBoolean("ok"))
    }
    @Test fun replayComparisonDoesNotDependOnJsonObjectKeyOrder() {
        val cache = AvatarCommandProtocol.Calls()
        val a = JSONObject("""{"action":"SET","weights":{"mouthSmile":0.3,"browInnerUp":0.2}}""")
        val b = JSONObject("""{"weights":{"browInnerUp":0.2,"mouthSmile":0.3},"action":"SET"}""")
        cache.remember("a",a,JSONObject().put("ok",true))
        assertTrue(cache.cached("a",b)!!.getBoolean("ok"))
    }
}
