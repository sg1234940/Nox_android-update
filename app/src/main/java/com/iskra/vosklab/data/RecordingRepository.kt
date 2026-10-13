package com.iskra.vosklab.data

import android.content.Context
import com.iskra.vosklab.metrics.DiagnosticTimestamps
import com.iskra.vosklab.metrics.RecognitionMetrics
import com.iskra.vosklab.metrics.TextMetrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

data class RecordingItem(
    val id: String,
    val timestamp: Long,
    val audioPath: String,
    val rawTranscript: String,
    val correctedText: String,
    val metrics: RecognitionMetrics,
    val modelName: String = "small-pl"
)

class RecordingRepository(private val context: Context) {

    private val recordingsDir = File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }
    private val metadataFile = File(recordingsDir, "recordings.json")

    fun getAllRecordings(): List<RecordingItem> {
        if (!metadataFile.exists()) return emptyList()
        val list = mutableListOf<RecordingItem>()
        try {
            val jsonArray = JSONArray(metadataFile.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val raw = obj.optString("rawTranscript", "")
                val corr = obj.optString("correctedText", raw)

                val tsObj = obj.optJSONObject("timestamps")
                val timestamps = if (tsObj != null) {
                    DiagnosticTimestamps(
                        micStartMs = tsObj.optLong("micStartMs", 0L),
                        firstPcmMs = tsObj.optLong("firstPcmMs", 0L),
                        speechOnsetMs = tsObj.optLong("speechOnsetMs", 0L),
                        firstPartialMs = tsObj.optLong("firstPartialMs", 0L),
                        firstWordMs = tsObj.optLong("firstWordMs", 0L),
                        finalMs = tsObj.optLong("finalMs", 0L),
                        stopMs = tsObj.optLong("stopMs", 0L)
                    )
                } else DiagnosticTimestamps()

                list.add(
                    RecordingItem(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        audioPath = obj.getString("audioPath"),
                        rawTranscript = raw,
                        correctedText = corr,
                        metrics = RecognitionMetrics(
                            timestamps = timestamps,
                            micStartupMs = obj.optLong("micStartupMs", 0L),
                            speechOnsetDelayMs = obj.optLong("speechOnsetDelayMs", 0L),
                            firstPartialLatencyMs = obj.optLong("firstPartialLatencyMs", 0L),
                            firstWordLatencyMs = obj.optLong("firstWordLatencyMs", 0L),
                            totalRecognitionTimeMs = obj.optLong("totalRecognitionTimeMs", 0L),
                            audioDurationMs = obj.optLong("audioDurationMs", 0L),
                            rtf = obj.optDouble("rtf", 0.0).toFloat(),
                            snrDb = obj.optDouble("snrDb", 0.0).toFloat(),
                            wer = obj.optDouble("wer", 0.0).toFloat(),
                            cer = obj.optDouble("cer", 0.0).toFloat()
                        ),
                        modelName = obj.optString("modelName", "small-pl")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveRecording(item: RecordingItem) {
        val current = getAllRecordings().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveList(current)
    }

    fun updateCorrection(id: String, newCorrectedText: String) {
        val current = getAllRecordings().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = current[index]
            val wordMetrics = TextMetrics.calculateWordMetrics(newCorrectedText, old.rawTranscript)
            val charMetrics = TextMetrics.calculateCharacterMetrics(newCorrectedText, old.rawTranscript)
            val updatedMetrics = old.metrics.copy(
                wer = wordMetrics.errorRate,
                cer = charMetrics.errorRate
            )
            val updatedItem = old.copy(
                correctedText = newCorrectedText,
                metrics = updatedMetrics
            )
            current[index] = updatedItem
            saveList(current)
        }
    }

    fun deleteRecording(id: String) {
        val current = getAllRecordings().toMutableList()
        val item = current.find { it.id == id }
        if (item != null) {
            File(item.audioPath).delete()
            current.remove(item)
            saveList(current)
        }
    }

    private fun saveList(list: List<RecordingItem>) {
        val jsonArray = JSONArray()
        list.forEach { rec ->
            val obj = JSONObject().apply {
                put("id", rec.id)
                put("timestamp", rec.timestamp)
                put("audioPath", rec.audioPath)
                put("rawTranscript", rec.rawTranscript)
                put("correctedText", rec.correctedText)

                val ts = rec.metrics.timestamps
                val tsObj = JSONObject().apply {
                    put("micStartMs", ts.micStartMs)
                    put("firstPcmMs", ts.firstPcmMs)
                    put("speechOnsetMs", ts.speechOnsetMs)
                    put("firstPartialMs", ts.firstPartialMs)
                    put("firstWordMs", ts.firstWordMs)
                    put("finalMs", ts.finalMs)
                    put("stopMs", ts.stopMs)
                }
                put("timestamps", tsObj)

                put("micStartupMs", rec.metrics.micStartupMs)
                put("speechOnsetDelayMs", rec.metrics.speechOnsetDelayMs)
                put("firstPartialLatencyMs", rec.metrics.firstPartialLatencyMs)
                put("firstWordLatencyMs", rec.metrics.firstWordLatencyMs)
                put("totalRecognitionTimeMs", rec.metrics.totalRecognitionTimeMs)
                put("audioDurationMs", rec.metrics.audioDurationMs)
                put("rtf", rec.metrics.rtf)
                put("snrDb", rec.metrics.snrDb)
                put("wer", rec.metrics.wer)
                put("cer", rec.metrics.cer)
                put("modelName", rec.modelName)
            }
            jsonArray.put(obj)
        }

        FileWriter(metadataFile).use { writer ->
            writer.write(jsonArray.toString(2))
        }
    }

    fun getNewAudioFile(): File {
        return File(recordingsDir, "rec_${System.currentTimeMillis()}.wav")
    }
}
