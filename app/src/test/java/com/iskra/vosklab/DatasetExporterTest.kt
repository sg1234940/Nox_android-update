package com.iskra.vosklab

import com.iskra.vosklab.data.RecordingItem
import com.iskra.vosklab.metrics.DiagnosticTimestamps
import com.iskra.vosklab.metrics.RecognitionMetrics
import com.iskra.vosklab.personalization.DatasetExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class DatasetExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExportDatasetZipFullProvenance() {
        val dummyAudioFile = tempFolder.newFile("test_sample.wav")
        dummyAudioFile.writeBytes(ByteArray(100))

        val now = System.currentTimeMillis()
        val recording = RecordingItem(
            id = "1001",
            timestamp = now,
            audioPath = dummyAudioFile.absolutePath,
            rawTranscript = "wloncz iskra",
            correctedText = "Włącz Iskra.",
            metrics = RecognitionMetrics(
                timestamps = DiagnosticTimestamps(
                    micStartMs = now,
                    firstPcmMs = now + 50,
                    speechOnsetMs = now + 200,
                    firstPartialMs = now + 350,
                    firstWordMs = now + 400,
                    stopMs = now + 1200,
                    finalMs = now + 1250
                ),
                firstWordLatencyMs = 400,
                totalRecognitionTimeMs = 1250,
                audioDurationMs = 2000,
                rtf = 0.625f,
                snrDb = 18.5f,
                wer = 0.5f,
                cer = 0.3f
            ),
            modelName = "vosk-model-small-pl-0.22"
        )

        val exportDir = tempFolder.newFolder("export_output")
        val outputZip = File(exportDir, "dataset.zip")

        val exporter = DatasetExporter(exportDir)
        val result = exporter.exportDatasetZip(
            recordings = listOf(recording),
            outputZipFile = outputZip,
            exportKaldiFormat = true
        )

        assertTrue(result)
        assertTrue(outputZip.exists())

        // Read manifest from generated zip
        val zipFile = ZipFile(outputZip)
        val manifestEntry = zipFile.getEntry("dataset_manifest.json")
        val kaldiTextEntry = zipFile.getEntry("text")

        assertTrue(manifestEntry != null)
        assertTrue(kaldiTextEntry != null)

        val manifestContent = zipFile.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
        val kaldiContent = zipFile.getInputStream(kaldiTextEntry).bufferedReader().use { it.readText() }

        assertTrue(manifestContent.contains("vosk-model-small-pl-0.22"))
        assertTrue(manifestContent.contains("raw_transcript\": \"wloncz iskra\""))
        assertTrue(manifestContent.contains("corrected_text\": \"Włącz Iskra.\""))
        assertTrue(manifestContent.contains("estimated_snr_db\": 18.5"))
        assertTrue(manifestContent.contains("micStartMs"))
        assertTrue(manifestContent.contains("stopMs"))

        assertTrue(kaldiContent.contains("iskra_1001 Włącz Iskra."))
    }
}
