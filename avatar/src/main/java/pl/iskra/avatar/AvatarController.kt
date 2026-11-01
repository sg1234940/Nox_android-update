package pl.iskra.avatar

import kotlin.math.exp

/** No chat, network, microphone, memory or player dependency. Host owns conversation/turn identity. */
class AvatarController {
    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }
    enum class Result { APPLIED, STALE, INVALID }
    sealed interface Command {
        data class PhaseChanged(val phase: Phase) : Command
        data class Weight(val name: String, val value: Float) : Command
        data class Animation(val name: String?) : Command
        data class BodyPaused(val paused: Boolean) : Command
        data class SpeechStarted(val playbackId: String) : Command
        data class SpeechFrame(val playbackId: String, val positionMs: Long, val energy: Float,
                               val viseme: String? = null) : Command
        data class SpeechStopped(val playbackId: String) : Command
        data object Interrupted : Command
        data object Reset : Command
    }
    data class Event(val conversationId: String, val turnId: Long, val sequence: Long, val command: Command)
    data class Snapshot(val phase: Phase, val animation: String?, val weights: FloatArray,
                        val playbackId: String?, val positionMs: Long?, val bodyPaused: Boolean = false)
    companion object {
        val MORPHS = listOf("eyeBlinkLeft", "eyeBlinkRight", "eyesLeft", "eyesRight", "eyesUp", "eyesDown",
            "browInnerUp", "browDownLeft", "browDownRight", "jawOpen", "mouthSmile", "mouthFrown",
            "mouthFunnel", "mouthPucker", "viseme_MBP", "viseme_FV", "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U")
        val ANIMATIONS = listOf("HumanDancingTwerk", "HumanForwardJump", "HumanHappyIdle", "HumanHipHopDancing2",
            "HumanJoyfulJump", "HumanOffensiveIdle", "HumanSitting3")
        val OPENING_GAINS = mapOf("jawOpen" to 1f, "mouthFunnel" to .35f, "viseme_A" to .85f,
            "viseme_E" to .45f, "viseme_I" to .24f, "viseme_O" to .60f, "viseme_U" to .35f)
        val VISEMES = MORPHS.filter { it.startsWith("viseme_") }
    }
    private var conversation: String? = null
    private var turn = -1L
    private var sequence = -1L
    private var suspended = false
    private var phase = Phase.IDLE
    private var animation: String? = null
    private var bodyPaused = false
    private var playback: String? = null
    private var position: Long? = null
    private var target = FloatArray(MORPHS.size)
    private var current = FloatArray(MORPHS.size)

    @Synchronized fun bindConversation(id: String) {
        require(id.isNotBlank())
        conversation = id; turn = -1; sequence = -1; suspended = false; reset()
    }
    @Synchronized fun beginTurn(id: Long) {
        require(conversation != null && id >= 0 && id > turn)
        turn = id; sequence = -1; suspended = false; clearSpeech(); phase = Phase.IDLE
    }
    @Synchronized fun dispatch(event: Event): Result {
        if (suspended || conversation == null || turn < 0 || event.conversationId != conversation || event.turnId != turn ||
            event.sequence < 0 || event.sequence <= sequence) return Result.STALE
        val c = event.command
        // Validate before mutating either face or event sequence.
        when (c) {
            is Command.Weight -> if (c.name !in MORPHS || !c.value.isFinite() || c.value !in 0f..1f) return Result.INVALID
            is Command.Animation -> if (c.name != null && c.name !in ANIMATIONS) return Result.INVALID
            is Command.SpeechStarted -> if (c.playbackId.isBlank()) return Result.INVALID
            is Command.SpeechFrame -> {
                if (c.playbackId != playback || phase != Phase.SPEAKING) return Result.STALE
                if (c.positionMs < 0 || c.positionMs < (position ?: 0) || !c.energy.isFinite() || c.energy !in 0f..1f ||
                    (c.viseme != null && c.viseme !in VISEMES)) return Result.INVALID
            }
            is Command.SpeechStopped -> if (c.playbackId != playback) return Result.STALE
            else -> Unit
        }
        when (c) {
            is Command.PhaseChanged -> { if (c.phase != Phase.SPEAKING) clearSpeech(); phase = c.phase }
            is Command.Weight -> target[MORPHS.indexOf(c.name)] = c.value
            is Command.Animation -> { animation = c.name; bodyPaused = false }
            is Command.BodyPaused -> bodyPaused = c.paused
            is Command.SpeechStarted -> { clearSpeech(); playback = c.playbackId; phase = Phase.SPEAKING }
            is Command.SpeechFrame -> {
                position = c.positionMs
                VISEMES.forEach { target[MORPHS.indexOf(it)] = 0f }
                c.viseme?.let { target[MORPHS.indexOf(it)] = c.energy }
                target[MORPHS.indexOf("jawOpen")] = c.energy * .45f
            }
            is Command.SpeechStopped, Command.Interrupted -> { clearSpeech(); phase = Phase.IDLE }
            Command.Reset -> reset()
        }
        sequence = event.sequence
        return Result.APPLIED
    }
    /** Called on pause/background as well as interruption: no suspended open mouth. */
    @Synchronized fun suspendPlayback() { suspended = true; clearSpeech(); phase = Phase.IDLE }
    @Synchronized fun frame(dtSeconds: Float): Snapshot {
        require(dtSeconds.isFinite() && dtSeconds >= 0f)
        val a = 1f - exp(-14f * dtSeconds.coerceAtMost(.1f))
        for (i in current.indices) current[i] += (target[i] - current[i]) * a
        val rendered = current.copyOf()
        val opening = OPENING_GAINS.entries.sumOf { (name, gain) -> (rendered[MORPHS.indexOf(name)] * gain).toDouble() }.toFloat()
        if (opening > 1f) for (name in OPENING_GAINS.keys) rendered[MORPHS.indexOf(name)] /= opening
        return Snapshot(phase, animation, rendered, playback, position, bodyPaused)
    }
    @Synchronized fun snapshot() = Snapshot(phase, animation, target.copyOf(), playback, position, bodyPaused)
    private fun clearSpeech() {
        playback = null; position = null
        for (name in VISEMES + "jawOpen") {
            val i = MORPHS.indexOf(name); target[i] = 0f; current[i] = 0f
        }
    }
    private fun reset() {
        target.fill(0f); current.fill(0f); clearSpeech(); phase = Phase.IDLE; animation = null; bodyPaused = false
    }
}
