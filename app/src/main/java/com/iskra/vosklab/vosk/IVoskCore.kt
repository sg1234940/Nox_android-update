package com.iskra.vosklab.vosk

import java.io.File

interface IVoskCore {
    val isLoaded: Boolean
    val activeModelName: String

    fun loadModel(modelDir: File, sampleRate: Float = 16000f, grammarJson: String? = null)
    fun acceptWaveform(buffer: ShortArray, readSize: Int): String
    fun getFinalResult(): String
    fun transcribeWavFile(wavFile: File, sampleRate: Float = 16000f): RecognitionResult
    fun close()
}
