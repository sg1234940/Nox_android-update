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
                        val playbackId: String?, val positionMs: Long?, val bodyPaused: Boolean = false,
                        val headPitchDegrees: Float = 0f)
    companion object {
        val MORPHS = listOf("eyeBlinkLeft", "eyeBlinkRight", "eyesLeft", "eyesRight", "eyesUp", "eyesDown",
            "browInnerUp", "browDownLeft", "browDownRight", "jawOpen", "mouthSmile", "mouthFrown",
            "mouthFunnel", "mouthPucker", "viseme_MBP", "viseme_FV", "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U",
            "cheekRaiseLeft", "cheekRaiseRight", "foreheadLiftLeft", "foreheadLiftRight")
        val ANIMATIONS = listOf("HumanDancingTwerk", "HumanForwardJump", "HumanHappyIdle", "HumanHipHopDancing2",
            "HumanJoyfulJump", "HumanOffensiveIdle", "HumanSitting3")
        val OPENING_GAINS = mapOf("jawOpen" to 1f, "mouthFunnel" to .35f, "viseme_A" to .85f,
            "viseme_E" to .45f, "viseme_I" to .24f, "viseme_O" to .60f, "viseme_U" to .35f)
        private val INNER_BROW = MORPHS.indexOf("browInnerUp")
        private val LEFT_BROW_DOWN = MORPHS.indexOf("browDownLeft")
        private val RIGHT_BROW_DOWN = MORPHS.indexOf("browDownRight")
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
    private var speechEnergy = 0f
    private var audioAge = 0f
    private var phrase = 0f
    private var sustained = 0f
    private var target = FloatArray(MORPHS.size)
    private val pose = FloatArray(MORPHS.size)
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
            is Command.Weight -> {
                val index = MORPHS.indexOf(c.name)
                pose[index] = c.value
                target[index] = c.value
            }
            is Command.Animation -> { animation = c.name; bodyPaused = false }
            is Command.BodyPaused -> bodyPaused = c.paused
            is Command.SpeechStarted -> { clearSpeech(); playback = c.playbackId; phase = Phase.SPEAKING }
            is Command.SpeechFrame -> {
                if (position != c.positionMs) audioAge = 0f
                speechEnergy = c.energy
                position = c.positionMs
                for (name in VISEMES) {
                    val index = MORPHS.indexOf(name)
                    target[index] = maxOf(pose[index], if (name == c.viseme) c.energy else 0f)
                }
                val jaw = MORPHS.indexOf("jawOpen")
                target[jaw] = maxOf(pose[jaw], c.energy * .45f)
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
        val dt = dtSeconds.coerceAtMost(.1f)
        val a = 1f - exp(-14f * dt)
        for (i in current.indices) current[i] += (target[i] - current[i]) * a
        val rendered = current.copyOf()
        val opening = OPENING_GAINS.entries.sumOf { (name, gain) -> (rendered[MORPHS.indexOf(name)] * gain).toDouble() }.toFloat()
        if (opening > 1f) for (name in OPENING_GAINS.keys) rendered[MORPHS.indexOf(name)] /= opening
        var pitch = 0f
        if (playback != null) {
            audioAge += dtSeconds
            val energy = if (audioAge <= .16f) speechEnergy else 0f
            // The difference between short and long envelopes follows accents.
            // A held vowel settles. A new phrase can lift the brows again.
            phrase += (energy - phrase) * (1f - exp(-7f * dt))
            sustained += (energy - sustained) * (1f - exp(-1.4f * dt))
            val accent = (phrase - sustained).coerceAtLeast(0f)
            val browRoom = (1f - rendered[INNER_BROW]) *
                (1f - maxOf(rendered[LEFT_BROW_DOWN], rendered[RIGHT_BROW_DOWN]))
            rendered[INNER_BROW] += browRoom * (.15f * phrase + .44f * accent)
            for (side in listOf("Left","Right")) {
                val cheek = MORPHS.indexOf("cheekRaise"+side)
                val forehead = MORPHS.indexOf("foreheadLift"+side)
                rendered[cheek] += (1f-rendered[cheek])*(.12f*phrase+.22f*rendered[MORPHS.indexOf("mouthSmile")])
                rendered[forehead] += (1f-rendered[forehead])*.36f*rendered[INNER_BROW]
            }
            if (!bodyPaused) pitch = 1.1f * phrase + 4.2f * accent
        }
        return Snapshot(phase, animation, rendered, playback, position, bodyPaused, pitch)
    }
    @Synchronized fun snapshot() = Snapshot(phase, animation, target.copyOf(), playback, position, bodyPaused)
    private fun clearSpeech() {
        if (playback == null) return
        playback = null; position = null
        speechEnergy = 0f; audioAge = 0f; phrase = 0f; sustained = 0f
        for (name in VISEMES + "jawOpen") {
            val i = MORPHS.indexOf(name); target[i] = pose[i]; current[i] = pose[i]
        }
    }
    private fun reset() {
        pose.fill(0f); target.fill(0f); current.fill(0f); clearSpeech(); phase = Phase.IDLE; animation = null; bodyPaused = false
    }
}
