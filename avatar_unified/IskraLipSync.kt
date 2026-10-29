package pl.iskra.app.avatar

/** Timeline driven by actual audio playback position (not wall-clock time). */
class IskraLipSync {
    data class Cue(val startMs: Long, val durationMs: Long, val name: String, val strength: Float = 1f, val jaw: Float = 0.3f) {
        init {
            require(startMs >= 0 && durationMs > 0 && startMs <= Long.MAX_VALUE - durationMs)
            require(name in IskraFaceMotion.VISEMES)
            require(strength.isFinite() && strength in 0f..1f && jaw.isFinite() && jaw in 0f..1f)
        }
        val endMs get() = startMs + durationMs
    }
    private var cues: List<Cue> = emptyList()
    private var active = false
    fun start(sequence: List<Cue>) {
        require(sequence.zipWithNext().all { it.first.endMs <= it.second.startMs }) { "Cues overlap or are unordered" }
        cues = sequence.toList(); active = true
    }
    fun stop() { cues = emptyList(); active = false }
    fun sample(playbackPositionMs: Long): Pair<Map<String, Float>, Float> {
        require(playbackPositionMs >= 0)
        if (!active) return emptyMap<String, Float>() to 0f
        val cue = cues.firstOrNull { playbackPositionMs >= it.startMs && playbackPositionMs < it.endMs }
        if (cue == null) {
            if (cues.isEmpty() || playbackPositionMs >= cues.last().endMs) stop()
            return emptyMap<String, Float>() to 0f
        }
        // The smoothing layer cross-fades each viseme independently.
        return mapOf(cue.name to cue.strength) to cue.jaw
    }
}
