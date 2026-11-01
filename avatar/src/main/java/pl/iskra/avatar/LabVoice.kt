package pl.iskra.avatar

import android.net.Uri

/** Optional adapter implemented by Iskra's existing private Promyk module, never a cloud fallback. */
interface LabVoice : AutoCloseable {
    data class Voice(val id: String, val name: String, val language: String)
    data class Settings(val inputLanguage: String = "pl", val outputLanguage: String = "pl", val voiceId: String = "",
        val lengthScale: Float = 1f, val silenceMs: Int = 1200)
    interface Events {
        fun state(value: State, message: String = "")
        fun transcript(text: String)
        fun speechDetected()
        fun playback(id: String, positionMs: Long, energy: Float)
        fun playbackEnded()
    }
    enum class State { IDLE, LISTENING, RECOGNIZING, SYNTHESIZING, SPEAKING, FAILED }
    var events: Events?
    fun catalogue(callback: (List<Voice>, List<String>, String?) -> Unit)
    fun importModels(folder: Uri, callback: (String?) -> Unit)
    fun listen(settings: Settings, echoGuarded: Boolean = false)
    fun finishListening()
    fun speak(text: String, voiceId: String, lengthScale: Float)
    fun interruptSpeech()
    fun stop()
}
