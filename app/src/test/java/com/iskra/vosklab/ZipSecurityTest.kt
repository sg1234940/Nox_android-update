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
            fail("Expected Exception for corrupted zip")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun testNonModelZipRejectionAndCleanup() {
        val zipBytesStream = ByteArrayOutputStream()
        ZipOutputStream(zipBytesStream).use { zos ->
            val entry = ZipEntry("random_image.jpg")
            zos.putNextEntry(entry)
            zos.write("not a vosk model".toByteArray())
            zos.closeEntry()
        }

        val manager = ModelManager(customModelsDir = tempFolder.root)
        try {
            manager.importModelFromZipStream(
                ByteArrayInputStream(zipBytesStream.toByteArray()),
                "random_zip"
            )
            fail("Expected IllegalArgumentException for ZIP lacking Vosk model structure!")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Brak wymaganej struktury modelu Vosk") == true)
        }

        val rejectedDir = File(tempFolder.root, "random_zip")
        assertFalse("Partially unzipped directory must be cleaned up on failure", rejectedDir.exists())
    }

    @Test
    fun testRejectOnlyConfDirectory() {
        val dir = tempFolder.newFolder("only_conf")
        File(dir, "conf").mkdirs()
        File(dir, "conf/model.conf").writeText("some config")

        val manager = ModelManager(customModelsDir = tempFolder.root)
        assertFalse("Directory with only conf/ must be rejected", manager.isValidVoskModelDirectory(dir))
    }

    @Test
    fun testRejectOnlyEmptyAmDirectory() {
        val dir = tempFolder.newFolder("empty_am")
        File(dir, "am").mkdirs()
        File(dir, "conf").mkdirs()
        File(dir, "conf/model.conf").writeText("some config")

        val manager = ModelManager(customModelsDir = tempFolder.root)
        assertFalse("Directory with empty am/ must be rejected", manager.isValidVoskModelDirectory(dir))
    }

    @Test
    fun testRejectArbitraryFileInAmEvenWithConfig() {
        val dir = tempFolder.newFolder("fake_am_contents")
        File(dir, "am").mkdirs()
        File(dir, "am/not_a_model.txt").writeText("not an acoustic model")
        File(dir, "conf").mkdirs()
        File(dir, "conf/model.conf").writeText("some config")

        val manager = ModelManager(customModelsDir = tempFolder.root)
        assertFalse(
            "am/ with an arbitrary file must not be accepted as a Vosk acoustic model",
            manager.isValidVoskModelDirectory(dir)
        )
    }

    @Test
    fun testRejectRandomFinalMdlInWrongPlace() {
        val dir = tempFolder.newFolder("wrong_place")
        File(dir, "some_folder").mkdirs()
        File(dir, "some_folder/final.mdl").writeText("fake mdl")

        val manager = ModelManager(customModelsDir = tempFolder.root)
        assertFalse("Mislocated final.mdl must be rejected", manager.isValidVoskModelDirectory(dir))
    }

    @Test
    fun testAcceptMinimalValidVoskModelStructure() {
        val dir = tempFolder.newFolder("valid_structure")
        File(dir, "am").mkdirs()
        File(dir, "am/final.mdl").writeText("acoustic model binary")
        File(dir, "conf").mkdirs()
        File(dir, "conf/model.conf").writeText("model config")

        val manager = ModelManager(customModelsDir = tempFolder.root)
        assertTrue("Minimal valid structure must be accepted", manager.isValidVoskModelDirectory(dir))
    }

    @Test
    fun testValidVoskModelZipExtraction() {
        val zipBytesStream = ByteArrayOutputStream()
        ZipOutputStream(zipBytesStream).use { zos ->
            val entry1 = ZipEntry("am/final.mdl")
            zos.putNextEntry(entry1)
            zos.write("vosk acoustic model data".toByteArray())
            zos.closeEntry()

            val entry2 = ZipEntry("conf/model.conf")
            zos.putNextEntry(entry2)
            zos.write("vosk model config".toByteArray())
            zos.closeEntry()
        }

        val manager = ModelManager(customModelsDir = tempFolder.root)
        val resultDir = manager.importModelFromZipStream(
            ByteArrayInputStream(zipBytesStream.toByteArray()),
            "valid_vosk_model"
        )

        assertTrue(resultDir.exists())
        val extractedFile = File(resultDir, "conf/model.conf")
        assertTrue(extractedFile.exists())
        assertEquals("vosk model config", extractedFile.readText())
    }
}
