package com.iskra.vosklab.personalization

import com.iskra.vosklab.data.RecordingItem
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DatasetExporter(private val exportDir: File) {

    fun exportDatasetZip(
        recordings: List<RecordingItem>,
        outputZipFile: File,
        exportKaldiFormat: Boolean = true
    ): Boolean {
        if (!exportDir.exists()) exportDir.mkdirs()

        val tempDir = File(exportDir, "temp_export_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val wavsDir = File(tempDir, "wavs")
            wavsDir.mkdirs()

            val manifestWriter = FileWriter(File(tempDir, "dataset_manifest.json"))
            val kaldiTextWriter = if (exportKaldiFormat) FileWriter(File(tempDir, "text")) else null
            val kaldiWavWriter = if (exportKaldiFormat) FileWriter(File(tempDir, "wav.scp")) else null
            val kaldiUttWriter = if (exportKaldiFormat) FileWriter(File(tempDir, "utt2spk")) else null

            manifestWriter.write("[\n")

            val exportableRecordings = recordings.filter { File(it.audioPath).exists() }

            exportableRecordings.forEachIndexed { index, item ->
                val sourceFile = File(item.audioPath)
                val destWavName = "sample_${item.id}.wav"
                val destFile = File(wavsDir, destWavName)
                sourceFile.copyTo(destFile, overwrite = true)

                val targetText = item.correctedText.ifBlank { item.rawTranscript }
                val ts = item.metrics.timestamps

                manifestWriter.write(
                    """  {
    "id": "${item.id}",
    "audio_file": "wavs/$destWavName",
    "raw_transcript": "${escapeJson(item.rawTranscript)}",
    "corrected_text": "${escapeJson(targetText)}",
    "model_name": "${escapeJson(item.modelName)}",
    "timestamp": ${item.timestamp},
    "micStartMs": ${ts.micStartMs},
    "firstPcmMs": ${ts.firstPcmMs},
    "speechOnsetMs": ${ts.speechOnsetMs},
    "firstPartialMs": ${ts.firstPartialMs},
    "firstWordMs": ${ts.firstWordMs},
    "stopMs": ${ts.stopMs},
    "finalMs": ${ts.finalMs},
    "audio_duration_ms": ${item.metrics.audioDurationMs},
    "rtf": ${item.metrics.rtf},
    "wer": ${item.metrics.wer},
    "cer": ${item.metrics.cer},
    "estimated_snr_db": ${item.metrics.snrDb}
  }${if (index < exportableRecordings.size - 1) "," else ""}
"""
                )

                if (exportKaldiFormat) {
                    val uttId = "iskra_${item.id}"
                    kaldiTextWriter?.write("$uttId $targetText\n")
                    kaldiWavWriter?.write("$uttId wavs/$destWavName\n")
                    kaldiUttWriter?.write("$uttId speaker_default\n")
                }
            }

            manifestWriter.write("]\n")
            manifestWriter.close()
            kaldiTextWriter?.close()
            kaldiWavWriter?.close()
            kaldiUttWriter?.close()

            zipDirectory(tempDir, outputZipFile)
            tempDir.deleteRecursively()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            tempDir.deleteRecursively()
            return false
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }

    private fun zipDirectory(dirToZip: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val baseUri = dirToZip.toURI()
            dirToZip.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = baseUri.relativize(file.toURI()).path
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}
