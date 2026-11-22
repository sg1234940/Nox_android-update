package pl.iskra.avatar.test

import org.json.JSONObject

/** Same finality protocol as Iskra: neither streamed text nor an avatar handoff authorizes speech. */
internal class LabFinality {
    private var responseId:String?=null
    private var round=0
    private val questions = mutableListOf<String>()
    private var invalid=false
    private fun number(value:Any):Int {require(value is Number);return java.math.BigDecimal(value.toString()).intValueExact().also{require(it>0)}}
    fun accept(event:JSONObject) {
        try {when(event.optString("type")) {
            "round_start"->{val id=event.getString("response_id");val n=number(event.get("round"))
                require(id.matches(Regex("[A-Za-z0-9_-]{1,120}")) && (responseId==null || responseId==id) && n==round+1)
                responseId=id;round=n;questions.clear()}
            "question" -> if (round > 0 && questions.size <= 3) questions.add(event.getString("text"))
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
        val result = JSONObject(f.toString())
        if (schema == "promyk.final/v1" && f.isNull("voice_plan_candidate") && questions.isNotEmpty()) {
            result.put("text", publicQuestions(f.getString("text")))
        }
        return result
    }

    // A question event alone is provisional. Only replace envelopes repeated in the
    // proven final of the same round, in order and with exactly the same public text.
    private fun publicQuestions(raw: String): String {
        if (questions.size > 3) return raw
        var from = 0
        val rendered = StringBuilder()
        for (expected in questions) {
            val start = raw.indexOf(QUESTION_START, from)
            if (start < 0) return raw
            val payload = start + QUESTION_START.length
            val end = raw.indexOf(QUESTION_END, payload)
            if (end < 0) return raw
            val body = runCatching { JSONObject(raw.substring(payload, end)) }.getOrNull() ?: return raw
            val question = (body.opt("text") as? String)?.trim()
            if (body.length() != 1 || question.isNullOrEmpty() || question != expected) return raw
            rendered.append(raw, from, start).append(question)
            from = end + QUESTION_END.length
        }
        return if (raw.indexOf(QUESTION_START, from) < 0) rendered.append(raw, from, raw.length).toString() else raw
    }

    private companion object {
        const val QUESTION_START = "<<<ISKRA_QUESTION>>>"
        const val QUESTION_END = "<<<END_ISKRA_QUESTION>>>"
    }
}
