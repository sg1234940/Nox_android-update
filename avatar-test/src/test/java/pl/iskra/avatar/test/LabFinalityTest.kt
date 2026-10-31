package pl.iskra.avatar.test

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LabFinalityTest {
 private fun start(id:String="r",round:Int=1)=JSONObject().put("type","round_start").put("response_id",id).put("round",round)
 private fun done()=JSONObject().put("type","done").put("finish_reason","stop").put("finality_protocol","promyk.final/v1")
  .put("final_response",JSONObject().put("schema","promyk.final/v1").put("response_id","r").put("round",1).put("finish_reason","stop").put("server_continuations_complete",true).put("text","Gotowe"))
 @Test fun aBoundFinalResponseCanComplete(){val f=LabFinality();f.accept(start());assertEquals("Gotowe",f.final(done()).getString("text"))}
 @Test fun textOrDoneAloneCannotAuthorizeCompletion(){assertThrows(IllegalArgumentException::class.java){LabFinality().final(done())}}
 @Test fun foreignResponseAndIncompleteServerWorkAreRejected(){for(patch in listOf("response_id" to "other","server_continuations_complete" to false)){val f=LabFinality();f.accept(start());val d=done();d.getJSONObject("final_response").put(patch.first,patch.second);assertThrows(IllegalArgumentException::class.java){f.final(d)}}}
 @Test fun errorAndSkippedRoundInvalidateFinality(){for(event in listOf(JSONObject().put("type","error"),start(round=3))){val f=LabFinality();f.accept(start());f.accept(event);assertThrows(IllegalArgumentException::class.java){f.final(done())}}}
 @Test fun nativeVoiceNeedsProviderToolProofAndMatchingText(){val f=LabFinality();f.accept(start());val d=done().put("finality_protocol","promyk.final/v2");val out=d.getJSONObject("final_response").put("schema","promyk.final/v2").put("completion_source","native_voice_reply").put("provider_finish_reason","tool_calls");out.put("voice_plan_candidate",JSONObject().put("schema","promyk.voice/v2").put("text","different"));assertThrows(IllegalArgumentException::class.java){f.final(d)}}
}
