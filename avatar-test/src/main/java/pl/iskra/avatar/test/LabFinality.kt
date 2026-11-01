package pl.iskra.avatar.test

import org.json.JSONObject

/** Same finality protocol as Iskra: neither streamed text nor an avatar handoff authorizes speech. */
internal class LabFinality {
    private var responseId:String?=null
    private var round=0
    private var invalid=false
    private fun number(value:Any):Int {require(value is Number);return java.math.BigDecimal(value.toString()).intValueExact().also{require(it>0)}}
    fun accept(event:JSONObject) {
        try {when(event.optString("type")) {
            "round_start"->{val id=event.getString("response_id");val n=number(event.get("round"))
                require(id.matches(Regex("[A-Za-z0-9_-]{1,120}")) && (responseId==null || responseId==id) && n==round+1)
                responseId=id;round=n}
            "error"->invalid=true
        }}catch(_:Exception){invalid=true}
    }
    fun final(event:JSONObject):JSONObject {
        require(!invalid && event.optString("type")=="done" && event.optString("finish_reason")=="stop")
        val schema=event.getString("finality_protocol")
        require(schema in setOf("promyk.final/v1","promyk.final/v2"))
        val f=event.getJSONObject("final_response")
        require(round>0 && f.getString("schema")==schema && f.getString("response_id")==responseId && number(f.get("round"))==round)
        require(f.getString("finish_reason")=="stop" && f.get("server_continuations_complete")==true)
        require(f.get("text") is String)
        if(schema=="promyk.final/v2") {
            require(f.getString("completion_source")=="native_voice_reply" && f.getString("provider_finish_reason")=="tool_calls")
            val p=f.getJSONObject("voice_plan_candidate")
            require(p.getString("schema")=="promyk.voice/v2" && p.getString("text")==f.getString("text"))
        }
        return JSONObject(f.toString())
    }
}
