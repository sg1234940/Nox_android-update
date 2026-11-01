package pl.iskra.avatar

/** Local UI choices are separate from the model's pose and never carry chat content. */
data class LabPresentation(
    val model: String = "builtin", val outfit: String = "", val hologram: Boolean = false,
    val brightness: Float = 1f, val angle: Float = 0f, val zoom: Float = 1f, val face: Boolean = false,
    val background: String = "black", val inputLanguage: String = "pl", val outputLanguage: String = "pl",
    val voiceId: String = "", val lengthScale: Float = 1f, val silenceMs: Int = 1200,
    val chatTransparency: Int = 40, val chatHeight: Int = 35,
) {
    fun checked(): LabPresentation {
        require(chatTransparency in 0..100 && chatHeight in 15..65)
        require(brightness.isFinite() && brightness in .2f..2f)
        require(angle.isFinite() && angle in -1.2f..1.2f && zoom.isFinite() && zoom in .65f..1.8f)
        require(lengthScale.isFinite() && lengthScale in .8f..1.2f && silenceMs in 600..3000)
        require(listOf(inputLanguage, outputLanguage).all { it.matches(Regex("[a-z]{2,3}(?:-[A-Z]{2})?")) })
        require(model == "builtin" || model.matches(Regex("[a-f0-9]{64}")))
        require(outfit.isEmpty() || outfit.matches(Regex("[a-f0-9]{64}")))
        require(background in setOf("black", "scene", "photo"))
        require(voiceId.isEmpty() || voiceId.matches(Regex("local-[a-f0-9]{32}")))
        return this
    }
    fun action(hasText: Boolean, listening: Boolean) = if (hasText) "SEND" else if (listening) "STOP" else "MIC"
    fun frameDelay(active: Boolean, settling: Boolean, capture: Boolean) = if (active || settling || capture) 16L else 200L
}
