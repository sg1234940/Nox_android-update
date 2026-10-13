package com.iskra.vosklab.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioAnalysisResult(
    val durationMs: Long,
    val peakAmplitude: Int,
    val avgRmsDb: Float,
    val estimatedNoiseFloorDb: Float,
    val snrDb: Float,
    val droppedFirstWordsDetected: Boolean
)

class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    interface AudioFrameListener {
        fun onAudioFrame(buffer: ShortArray, readSize: Int, currentRmsDb: Float)
    }

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun startRecording(
        outputFile: File,
        vadSensitivityRatio: Float = 0.5f,
        listener: AudioFrameListener? = null
    ) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Nie można zainicjalizować mikrofonu AudioRecord.")
        }

        isRecording = true
        audioRecord.startRecording()

        recordingThread = Thread {
            val fileOutputStream = FileOutputStream(outputFile)
            fileOutputStream.write(ByteArray(44))

            val buffer = ShortArray(1024)
            val byteBuffer = ByteArray(2048)
            var totalBytesWritten = 0

            var peakAmp = 0
            var sumRmsSq = 0.0
            var totalFrames = 0

            var initialFramesCount = 0

            while (isRecording) {
                val readCount = audioRecord.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    var frameSumSq = 0.0
                    for (i in 0 until readCount) {
                        val sample = buffer[i].toInt()
                        val absSample = kotlin.math.abs(sample)
                        if (absSample > peakAmp) {
                            peakAmp = absSample
                        }
                        frameSumSq += sample * sample

                        byteBuffer[i * 2] = (sample and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                    }

                    val frameRms = sqrt(frameSumSq / readCount)
                    val frameRmsDb = if (frameRms > 0) 20 * log10(frameRms) else 0.0

                    sumRmsSq += frameSumSq
                    totalFrames += readCount

                    if (initialFramesCount < 10) {
                        initialFramesCount++
                    }

                    fileOutputStream.write(byteBuffer, 0, readCount * 2)
                    totalBytesWritten += readCount * 2

                    listener?.onAudioFrame(buffer, readCount, frameRmsDb.toFloat())
                }
            }

            audioRecord.stop()
            audioRecord.release()
            fileOutputStream.close()

            updateWavHeader(outputFile, totalBytesWritten)
        }

        recordingThread?.start()
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null
    }

    fun analyzeAudioFile(wavFile: File): AudioAnalysisResult {
        if (!wavFile.exists() || wavFile.length() <= 44) {
            return AudioAnalysisResult(0L, 0, 0f, 0f, 0f, false)
        }

        val fileLength = wavFile.length() - 44
        val totalSamples = fileLength / 2
        val durationMs = (totalSamples * 1000) / SAMPLE_RATE

        val bytes = wavFile.readBytes()
        var peakAmp = 0
        var totalEnergy = 0.0
        val sampleCount = (bytes.size - 44) / 2

        var quietEnergy = 0.0
        var quietSamples = 0
        var speechEnergy = 0.0
        var speechSamples = 0

        var firstWindowSpeechClipped = false

        for (i in 44 until bytes.size - 1 step 2) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
            val absSample = kotlin.math.abs(sample)
            if (absSample > peakAmp) peakAmp = absSample

            val sq = (sample * sample).toDouble()
            totalEnergy += sq

            if (absSample < 800) {
                quietEnergy += sq
                quietSamples++
            } else {
                speechEnergy += sq
                speechSamples++
            }

            val sampleIdx = (i - 44) / 2
            if (sampleIdx < 4800 && absSample > 15000) {
                firstWindowSpeechClipped = true
            }
        }

        val avgRms = if (sampleCount > 0) sqrt(totalEnergy / sampleCount) else 0.0
        val noiseRms = if (quietSamples > 0) sqrt(quietEnergy / quietSamples) else 100.0
        val speechRms = if (speechSamples > 0) sqrt(speechEnergy / speechSamples) else avgRms

        val avgRmsDb = if (avgRms > 0) (20 * log10(avgRms)).toFloat() else 0f
        val noiseFloorDb = if (noiseRms > 0) (20 * log10(noiseRms)).toFloat() else 0f
        val speechDb = if (speechRms > 0) (20 * log10(speechRms)).toFloat() else 0f
        val snrDb = maxOf(0f, speechDb - noiseFloorDb)

        return AudioAnalysisResult(
            durationMs = durationMs,
            peakAmplitude = peakAmp,
            avgRmsDb = avgRmsDb,
            estimatedNoiseFloorDb = noiseFloorDb,
            snrDb = snrDb,
            droppedFirstWordsDetected = firstWindowSpeechClipped
        )
    }

    private fun updateWavHeader(file: File, pcmAudioLength: Int) {
        val randomAccessFile = RandomAccessFile(file, "rw")
        val totalDataLen = pcmAudioLength + 36
        val byteRate = SAMPLE_RATE * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1
        header[21] = 0

        header[22] = 1
        header[23] = 0

        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = 2
        header[33] = 0

        header[34] = 16
        header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (pcmAudioLength and 0xff).toByte()
        header[41] = ((pcmAudioLength shr 8) and 0xff).toByte()
        header[42] = ((pcmAudioLength shr 16) and 0xff).toByte()
        header[43] = ((pcmAudioLength shr 24) and 0xff).toByte()

        randomAccessFile.seek(0)
        randomAccessFile.write(header, 0, 44)
        randomAccessFile.close()
    }
}
