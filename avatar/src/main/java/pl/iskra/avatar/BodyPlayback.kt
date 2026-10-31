package pl.iskra.avatar

/** Animation clock only. A null clip requests the model's captured bind transforms. */
class BodyPlayback {
    data class Frame(val animation: String?, val seconds: Float, val restoreBindPose: Boolean)
    private var initialized = false
    private var selected: String? = null
    private var seconds = 0f
    fun frame(animation: String?, deltaSeconds: Float, paused: Boolean): Frame {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0)
        require(animation == null || animation in AvatarController.ANIMATIONS)
        val changed = !initialized || selected != animation
        if (changed) seconds = 0f
        else if (animation != null && !paused) seconds += deltaSeconds.coerceAtMost(.1f)
        selected = animation; initialized = true
        return Frame(animation, seconds, changed)
    }
}
