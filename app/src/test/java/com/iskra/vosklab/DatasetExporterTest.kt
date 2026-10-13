package com.iskra.vosklab

import com.iskra.vosklab.data.RecordingItem
import com.iskra.vosklab.metrics.DiagnosticTimestamps
import com.iskra.vosklab.metrics.RecognitionMetrics
import com.iskra.vosklab.personalization.DatasetExporter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DatasetExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExportDatasetZip() {
        val dummyAudioFile = tempFolder.newFile("test_sample.wav")
        dummyAudioFile.writeBytes(ByteArray(100))

        val now = System.currentTimeMillis()
        val recording = RecordingItem(
            id = "1001",
            timestamp = now,
            audioPath = dummyAudioFile.absolutePath,
            rawTranscript = "testowe nagranie",
            correctedText = "Testowe nagranie.",
            metrics = RecognitionMetrics(
                timestamps = DiagnosticTimestamps(micStartMs = now, finalMs = now + 450),
                firstWordLatencyMs = 120,
                totalRecognitionTimeMs = 450,
                audioDurationMs = 2000,
                rtf = 0.225f,
                snrDb = 18.5f,
                wer = 0.0f,
                cer = 0.0f
            ),
            modelName = "small-pl"
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
        assertTrue(outputZip.length() > 0)
    }
}
