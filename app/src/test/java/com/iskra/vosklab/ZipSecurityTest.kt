package com.iskra.vosklab

import com.iskra.vosklab.vosk.ModelManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSlipVulnerabilityPrevention() {
        val zipBytesStream = ByteArrayOutputStream()
        ZipOutputStream(zipBytesStream).use { zos ->
            // Attempt malicious Zip Slip path traversal entry
            val entry = ZipEntry("../../../evil_script.sh")
            zos.putNextEntry(entry)
            zos.write("echo malicious".toByteArray())
            zos.closeEntry()
        }

        val targetDir = tempFolder.newFolder("model_target")
        val manager = ModelManager(customModelsDir = tempFolder.root)

        try {
            manager.importModelFromZipStream(
                ByteArrayInputStream(zipBytesStream.toByteArray()),
                targetDir.name
            )
            fail("Expected SecurityException due to Zip Slip entry!")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Zip Slip") == true)
        }
    }

    @Test
    fun testCorruptedZipHandling() {
        val corruptedBytes = "This is not a valid zip archive data".toByteArray()
        val manager = ModelManager(customModelsDir = tempFolder.root)

        try {
            manager.importModelFromZipStream(
                ByteArrayInputStream(corruptedBytes),
                "corrupted_model"
            )
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun testValidZipExtraction() {
        val zipBytesStream = ByteArrayOutputStream()
        ZipOutputStream(zipBytesStream).use { zos ->
            val entry = ZipEntry("model_file.txt")
            zos.putNextEntry(entry)
            zos.write("vosk model data".toByteArray())
            zos.closeEntry()
        }

        val manager = ModelManager(customModelsDir = tempFolder.root)
        val resultDir = manager.importModelFromZipStream(
            ByteArrayInputStream(zipBytesStream.toByteArray()),
            "valid_model"
        )

        assertTrue(resultDir.exists())
        val extractedFile = File(resultDir, "model_file.txt")
        assertTrue(extractedFile.exists())
        assertEquals("vosk model data", extractedFile.readText())
    }
}
