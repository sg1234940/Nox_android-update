package pl.iskra.avatar

import kotlin.math.cos
import kotlin.math.sin

/** Additive local joint angles in degrees, relative to the sampled clip or bind pose. */
data class JointAngles(val x: Float, val y: Float, val z: Float) {
    fun valid() = listOf(x, y, z).all { it.isFinite() && it in -180f..180f }
    fun mix(to: JointAngles, t: Float) = JointAngles(x+(to.x-x)*t, y+(to.y-y)*t, z+(to.z-z)*t)
    companion object { val ZERO = JointAngles(0f, 0f, 0f) }
}

internal class JointPose {
    private data class Transition(val from: JointAngles, val to: JointAngles, val start: Long, val duration: Long)
    private val transitions = linkedMapOf<String, Transition>()
    fun clear() = transitions.clear()
    private fun sample(v: Transition, now: Long): JointAngles {
        val t = if (v.duration == 0L) 1f else ((now-v.start).toFloat()/v.duration).coerceIn(0f, 1f)
        return v.from.mix(v.to, t*t*(3f-2f*t))
    }
    fun set(values: Map<String, JointAngles>, durationMs: Int, now: Long, initial: Map<String,JointAngles> = emptyMap()) {
        for ((name, target) in values) {
            val from = transitions[name]?.let { sample(it, now) } ?: initial[name] ?: JointAngles.ZERO
            transitions[name] = Transition(from, target, now, durationMs*1_000_000L)
        }
    }
    fun targets() = transitions.mapValues { it.value.to }
    fun at(now: Long) = transitions.mapValues { sample(it.value, now) }
    fun moving(now: Long) = transitions.values.any { now-it.start < it.duration }
}

/** Post-multiply local rotations; joint origin, skin scale and bind orientation are preserved. */
internal fun rotateJoint(base: FloatArray, a: JointAngles, out: FloatArray) {
    base.copyInto(out)
    fun axis(i: Int, j: Int, degrees: Float) {
        if (degrees == 0f) return
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        for (row in 0..3) {
            val u = out[i+row]; val v = out[j+row]
            out[i+row] = c*u+s*v
            out[j+row] = c*v-s*u
        }
    }
    axis(4, 8, a.x); axis(8, 0, a.y); axis(0, 4, a.z)
}
