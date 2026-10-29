package pl.iskra.app.avatar

import kotlin.math.exp

/** Pure Kotlin state machine. Does not call Filament or Android. */
class IskraFaceMotion(private val contract: GlbFaceContract) {
    data class State(
        val blinkLeft: Float = 0f, val blinkRight: Float = 0f,
        val gazeX: Float = 0f, val gazeY: Float = 0f,
        val browInnerUp: Float = 0f, val browDownLeft: Float = 0f, val browDownRight: Float = 0f,
        val jawOpen: Float = 0f, val smile: Float = 0f, val frown: Float = 0f,
        val funnel: Float = 0f, val pucker: Float = 0f,
        val visemes: Map<String, Float> = emptyMap()
    ) {
        fun validate(): State {
            val unit = listOf(blinkLeft, blinkRight, browInnerUp, browDownLeft, browDownRight,
                jawOpen, smile, frown, funnel, pucker) + visemes.values
            require(unit.all { it.isFinite() && it in 0f..1f }) { "Face weight outside 0..1" }
            require(gazeX.isFinite() && gazeX in -1f..1f && gazeY.isFinite() && gazeY in -1f..1f) { "Invalid gaze" }
            require(visemes.keys.all { it in VISEMES }) { "Unknown viseme" }
            return this
        }
    }
    companion object { val VISEMES = setOf("viseme_MBP", "viseme_FV", "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U") }
    var current = State(); private set
    var target = State(); private set
    private var weights = FloatArray(contract.names.size)
    fun weights(): FloatArray = weights.copyOf()
    fun setTarget(next: State) { target = next.validate() }
    fun setImmediate(next: State): FloatArray {
        val candidate = encode(next.validate()); current = next; target = next; weights = candidate
        return candidate.copyOf()
    }
    /** Stage candidate; commit only after native upload succeeded. */
    fun candidate(dt: Float): Pair<State, FloatArray> {
        require(dt.isFinite() && dt >= 0f)
        val a = (1f - exp(-12f * dt.coerceAtMost(0.25f))).coerceIn(0f, 1f)
        val b = (1f - exp(-20f * dt.coerceAtMost(0.25f))).coerceIn(0f, 1f)
        fun mix(x: Float, y: Float, f: Float = a) = x + (y - x) * f
        val v = (VISEMES).associateWith { mix(current.visemes[it] ?: 0f, target.visemes[it] ?: 0f, b) }
        val next = State(
            mix(current.blinkLeft, target.blinkLeft, b), mix(current.blinkRight, target.blinkRight, b),
            mix(current.gazeX, target.gazeX), mix(current.gazeY, target.gazeY),
            mix(current.browInnerUp, target.browInnerUp), mix(current.browDownLeft, target.browDownLeft),
            mix(current.browDownRight, target.browDownRight), mix(current.jawOpen, target.jawOpen, b),
            mix(current.smile, target.smile), mix(current.frown, target.frown),
            mix(current.funnel, target.funnel), mix(current.pucker, target.pucker), v
        )
        return next to encode(next)
    }
    fun commit(next: State, uploaded: FloatArray) { current = next; weights = uploaded.copyOf() }
    fun encode(s: State): FloatArray {
        s.validate()
        val out = FloatArray(contract.names.size)
        fun put(name: String, value: Float) { out[contract.indices.getValue(name)] = value }
        put("eyeBlinkLeft", s.blinkLeft); put("eyeBlinkRight", s.blinkRight)
        put("eyesLeft", (-s.gazeX).coerceAtLeast(0f)); put("eyesRight", s.gazeX.coerceAtLeast(0f))
        put("eyesDown", (-s.gazeY).coerceAtLeast(0f)); put("eyesUp", s.gazeY.coerceAtLeast(0f))
        put("browInnerUp", s.browInnerUp); put("browDownLeft", s.browDownLeft); put("browDownRight", s.browDownRight)
        put("jawOpen", s.jawOpen); put("mouthSmile", s.smile); put("mouthFrown", s.frown)
        put("mouthFunnel", s.funnel); put("mouthPucker", s.pucker)
        for (name in VISEMES) put(name, s.visemes[name] ?: 0f)
        return out
    }
}
