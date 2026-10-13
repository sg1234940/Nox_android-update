package com.iskra.vosklab

import com.google.gson.JsonParser
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

    @Test
    fun testExportWithMissingWavFilesNoTrailingCommaValidJson() {
        val existingWav1 = tempFolder.newFile("sample_1.wav")
        existingWav1.writeBytes(ByteArray(100))

        val existingWav3 = tempFolder.newFile("sample_3.wav")
        existingWav3.writeBytes(ByteArray(100))

        val missingWavPath = File(tempFolder.root, "non_existent_sample_2.wav").absolutePath

        val now = System.currentTimeMillis()
        val rec1 = RecordingItem(
            id = "1",
            timestamp = now,
            audioPath = existingWav1.absolutePath,
            rawTranscript = "test one",
            correctedText = "Test One",
            metrics = RecognitionMetrics()
        )
        val rec2Missing = RecordingItem(
            id = "2",
            timestamp = now + 1,
            audioPath = missingWavPath,
            rawTranscript = "test two missing",
            correctedText = "Test Two Missing",
            metrics = RecognitionMetrics()
        )
        val rec3 = RecordingItem(
            id = "3",
            timestamp = now + 2,
            audioPath = existingWav3.absolutePath,
            rawTranscript = "test three",
            correctedText = "Test Three",
            metrics = RecognitionMetrics()
        )

        val exportDir = tempFolder.newFolder("export_missing_test")
        val outputZip = File(exportDir, "dataset_missing.zip")

        val exporter = DatasetExporter(exportDir)
        val result = exporter.exportDatasetZip(
            recordings = listOf(rec1, rec2Missing, rec3),
            outputZipFile = outputZip,
            exportKaldiFormat = true
        )

        assertTrue(result)
        assertTrue(outputZip.exists())

        val zipFile = ZipFile(outputZip)
        val manifestEntry = zipFile.getEntry("dataset_manifest.json")
        assertTrue(manifestEntry != null)

        val manifestContent = zipFile.getInputStream(manifestEntry).bufferedReader().use { it.readText() }

        // Must parse cleanly as JSON array
        val jsonArray = JsonParser.parseString(manifestContent).asJsonArray
        assertEquals(2, jsonArray.size())

        val id1 = jsonArray.get(0).asJsonObject.get("id").asString
        val id2 = jsonArray.get(1).asJsonObject.get("id").asString

        assertEquals("1", id1)
        assertEquals("3", id2)
    }

    @Test
    fun testExportWhenLastItemIsMissingWavFileNoTrailingComma() {
        val existingWav1 = tempFolder.newFile("sample_last_1.wav")
        existingWav1.writeBytes(ByteArray(100))

        val missingWavPath = File(tempFolder.root, "missing_last.wav").absolutePath

        val now = System.currentTimeMillis()
        val rec1 = RecordingItem(
            id = "1",
            timestamp = now,
            audioPath = existingWav1.absolutePath,
            rawTranscript = "first",
            correctedText = "First",
            metrics = RecognitionMetrics()
        )
        val rec2LastMissing = RecordingItem(
            id = "2",
            timestamp = now + 1,
            audioPath = missingWavPath,
            rawTranscript = "last missing",
            correctedText = "Last Missing",
            metrics = RecognitionMetrics()
        )

        val exportDir = tempFolder.newFolder("export_last_missing_test")
        val outputZip = File(exportDir, "dataset_last_missing.zip")

        val exporter = DatasetExporter(exportDir)
        val result = exporter.exportDatasetZip(
            recordings = listOf(rec1, rec2LastMissing),
            outputZipFile = outputZip,
            exportKaldiFormat = true
        )

        assertTrue(result)
        val zipFile = ZipFile(outputZip)
        val manifestEntry = zipFile.getEntry("dataset_manifest.json")
        val manifestContent = zipFile.getInputStream(manifestEntry).bufferedReader().use { it.readText() }

        // Must parse cleanly as JSON array with 1 item
        val jsonArray = JsonParser.parseString(manifestContent).asJsonArray
        assertEquals(1, jsonArray.size())
        assertEquals("1", jsonArray.get(0).asJsonObject.get("id").asString)
    }
}
