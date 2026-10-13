package com.iskra.vosklab.vosk

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream

data class RecognitionResult(
    val text: String,
    val partialText: String = "",
    val recognitionTimeMs: Long = 0L,
    val rawJson: String = ""
)

class VoskEngineManager(val context: Context) {

    private var currentModel: Model? = null
    private var currentRecognizer: Recognizer? = null

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    private val _activeModelName = MutableStateFlow("Brak")
    val activeModelName: StateFlow<String> = _activeModelName

    fun isModelLoaded(): Boolean = currentModel != null

    fun loadModel(modelDir: File, sampleRate: Float = 16000f, grammarJson: String? = null) {
        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IllegalArgumentException("Katalog modelu nie istnieje: ${modelDir.absolutePath}")
        }

        close()

        val model = Model(modelDir.absolutePath)
        val recognizer = if (!grammarJson.isNullOrBlank()) {
            Recognizer(model, sampleRate, grammarJson)
        } else {
            Recognizer(model, sampleRate)
        }

        currentModel = model
        currentRecognizer = recognizer
        _isModelLoaded.value = true
        _activeModelName.value = modelDir.name
    }

    fun loadModel(modelPath: String) {
        loadModel(File(modelPath))
    }

    fun acceptWaveform(buffer: ShortArray, readSize: Int): String {
        val recognizer = currentRecognizer ?: return ""
        val isFinal = recognizer.acceptWaveForm(buffer, readSize)
        return if (isFinal) {
            parseTextFromJson(recognizer.result)
        } else {
            parsePartialFromJson(recognizer.partialResult)
        }
    }

    fun getFinalResult(): String {
        val recognizer = currentRecognizer ?: return ""
        return parseTextFromJson(recognizer.finalResult)
    }

    fun recognizeAudioFile(wavFile: File): String {
        val res = transcribeWavFile(wavFile)
        return res.text
    }

    fun transcribeWavFile(wavFile: File, sampleRate: Float = 16000f): RecognitionResult {
        val model = currentModel ?: throw IllegalStateException("Model Vosk nie został załadowany")
        val recognizer = Recognizer(model, sampleRate)

        val startTime = System.currentTimeMillis()
        val inputStream = FileInputStream(wavFile)

        if (wavFile.length() > 44) {
            inputStream.skip(44)
        }

        val buffer = ByteArray(4096)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead != -1) {
            val shortBuffer = ShortArray(bytesRead / 2)
            for (i in shortBuffer.indices) {
                shortBuffer[i] = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
            }
            recognizer.acceptWaveForm(shortBuffer, shortBuffer.size)
            bytesRead = inputStream.read(buffer)
        }

        val finalJson = recognizer.finalResult
        val endTime = System.currentTimeMillis()
        val text = parseTextFromJson(finalJson)
        recognizer.close()

        return RecognitionResult(
            text = text,
            recognitionTimeMs = endTime - startTime,
            rawJson = finalJson
        )
    }

    fun close() {
        try {
            currentRecognizer?.close()
        } catch (_: Exception) {}
        try {
            currentModel?.close()
        } catch (_: Exception) {}

        currentRecognizer = null
        currentModel = null
        _isModelLoaded.value = false
        _activeModelName.value = "Brak"
    }

    private fun parseTextFromJson(json: String): String {
        return try {
            val element = JsonParser.parseString(json)
            if (element.isJsonObject) {
                element.asJsonObject.get("text")?.asString ?: ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parsePartialFromJson(json: String): String {
        return try {
            val element = JsonParser.parseString(json)
            if (element.isJsonObject) {
                element.asJsonObject.get("partial")?.asString ?: ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }
}
