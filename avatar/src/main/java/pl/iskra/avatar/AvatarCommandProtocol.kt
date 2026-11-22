package pl.iskra.avatar

import org.json.JSONObject

/** Closed native tool protocol. Validation never mutates a renderer or controller. */
object AvatarCommandProtocol {
    private fun keys(o: JSONObject) = o.keys().asSequence().toSet()
    private fun closed(o: JSONObject, allowed: Set<String>) = keys(o).all { it in allowed }
    private fun numeric(o: JSONObject, name: String): Double? = (o.opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    fun valid(a: JSONObject): Boolean {
        if (a.optString("action") in setOf("RIG","POSE")) return validRigCommand(a)
        if (!closed(a, setOf("action", "weights", "animation", "body_paused", "camera", "preview"))) return false
        val action = a.opt("action") as? String ?: return false
        if (action !in setOf("SET", "STATE", "RESET") || a.has("preview") && a.opt("preview") !is Boolean) return false
        if (action != "SET") return keys(a).all { it in setOf("action", "preview") }
        if (keys(a).none { it in setOf("weights", "animation", "body_paused", "camera") }) return false
        if (a.has("weights")) {
            val w = a.optJSONObject("weights") ?: return false
            if (w.length() !in 1..21 || keys(w).any { it !in GlbFaceContractReader.EXPECTED || numeric(w, it)?.let { n -> n in 0.0..1.0 } != true }) return false
        }
        if (a.has("animation") && a.opt("animation") != JSONObject.NULL && a.opt("animation") !in AvatarController.ANIMATIONS) return false
        if (a.has("body_paused") && a.opt("body_paused") !is Boolean) return false
        if (a.has("camera")) {
            val c = a.optJSONObject("camera") ?: return false
            if (keys(c) != setOf("face", "angle") || c.opt("face") !is Boolean || numeric(c, "angle")?.let { it in -1.2..1.2 } != true) return false
        }
        return true
    }
    fun pose(state: AvatarController.Snapshot, angle: Float, face: Boolean): JSONObject = JSONObject().apply {
        put("weights", JSONObject().apply { GlbFaceContractReader.EXPECTED.forEachIndexed { i, name -> put(name, state.weights[i].toDouble()) } })
        put("animation", state.animation ?: JSONObject.NULL); put("body_paused", state.bodyPaused)
        put("camera", JSONObject().put("face", face).put("angle", angle.toString().toDouble()))
    }
    private val jointName = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
    private fun angle(value: Any?) = value is Number && value.toDouble().isFinite() && value.toDouble() in -180.0..180.0
    private fun validRigCommand(a: JSONObject): Boolean {
        if (a.has("preview") && a.opt("preview") !is Boolean) return false
        if (a.optString("action") == "RIG") return closed(a, setOf("action","preview"))
        if (!closed(a, setOf("action","joints","face","duration_ms","preview"))) return false
        val duration = numeric(a,"duration_ms")
        if (a.has("duration_ms") && (duration == null || duration !in 0.0..5000.0 || duration % 1.0 != 0.0)) return false
        val joints = a.optJSONArray("joints")
        val face = a.optJSONObject("face")
        if (a.has("joints") && (joints == null || joints.length() !in 1..128)) return false
        if (a.has("face") && (face == null || face.length() !in 1..AvatarController.MORPHS.size ||
                keys(face).any { it !in AvatarController.MORPHS || numeric(face,it)?.let { v -> v in 0.0..1.0 } != true })) return false
        if (joints == null && face == null) return false
        val seen = mutableSetOf<String>()
        for (i in 0 until (joints?.length() ?: 0)) {
            val r = joints!!.optJSONObject(i) ?: return false
            val name = r.opt("name") as? String ?: return false
            if (keys(r) != setOf("name","x","y","z") || !jointName.matches(name) || !seen.add(name) ||
                listOf("x","y","z").any { !angle(r.opt(it)) }) return false
        }
        return true
    }
    fun joints(a: JSONObject): Map<String, JointAngles> {
        val list = a.optJSONArray("joints") ?: return emptyMap()
        return (0 until list.length()).associate {
            val r = list.getJSONObject(it)
            r.getString("name") to JointAngles(r.getDouble("x").toFloat(),r.getDouble("y").toFloat(),r.getDouble("z").toFloat())
        }
    }
    fun failure(error: String, state: String = "REJECTED") = JSONObject().put("operation", "AVATAR").put("ok", false)
        .put("state", state).put("executed", false).put("error", error)

    /** One exchange owns this cache. Never evict an executed id and accidentally replay it. */
    class Calls(private val limit: Int = 16) {
        private fun canonical(o: JSONObject): String = o.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            val v = o.get(key); JSONObject.quote(key) + ":" + when (v) { is JSONObject -> canonical(v); is String -> JSONObject.quote(v); else -> v.toString() }
        }
        private data class Row(val args: String, val result: String)
        private val rows = linkedMapOf<String, Row>()
        fun cached(id: String, args: JSONObject): JSONObject? = rows[id]?.let {
            if (it.args == canonical(args)) JSONObject(it.result) else failure("REPLAY_CONFLICT")
        }
        fun canExecute() = rows.size < limit
        fun remember(id: String, args: JSONObject, result: JSONObject) { check(!rows.containsKey(id)); rows[id] = Row(canonical(args), result.toString()) }
    }
}
