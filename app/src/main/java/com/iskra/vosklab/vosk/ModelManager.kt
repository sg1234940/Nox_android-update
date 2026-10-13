package com.iskra.vosklab.vosk

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class VoskModelInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val sizeMb: Float = sizeBytes / (1024f * 1024f),
    val isDefault: Boolean = false
)

class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "vosk_models").apply { if (!exists()) mkdirs() }

    fun listInstalledModels(): List<VoskModelInfo> = listAvailableModels()

    fun listAvailableModels(): List<VoskModelInfo> {
        val dir = modelsDir
        val files = dir.listFiles() ?: return emptyList()

        return files.filter { it.isDirectory }.map { folder ->
            val size = getFolderSize(folder)
            VoskModelInfo(
                name = folder.name,
                path = folder.absolutePath,
                sizeBytes = size,
                sizeMb = size / (1024f * 1024f),
                isDefault = folder.name.contains("small-pl") || folder.name == "default-pl"
            )
        }
    }

    fun getModelDirectory(name: String): File? {
        val file = File(modelsDir, name)
        return if (file.exists() && file.isDirectory) file else null
    }

    fun copyDefaultModelFromAssetsIfNeeded() {
        val defaultDir = File(modelsDir, "vosk-model-small-pl-0.22")
        if (!defaultDir.exists()) {
            try {
                val assetManager = context.assets
                val modelZipStream = assetManager.open("vosk-model-small-pl-0.22.zip")
                importModelFromZipStream(modelZipStream, "vosk-model-small-pl-0.22")
            } catch (_: Exception) {
            }
        }
    }

    fun importModelFromZip(uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val folderName = "model_${System.currentTimeMillis()}"
            importModelFromZipStream(inputStream, folderName)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importModelFromZipStream(zipInputStream: InputStream, targetFolderName: String): File {
        val targetDir = File(modelsDir, targetFolderName)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val zis = ZipInputStream(zipInputStream)
        var entry = zis.nextEntry
        val buffer = ByteArray(8192)

        while (entry != null) {
            val file = File(targetDir, entry.name)
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                val fos = FileOutputStream(file)
                var len = zis.read(buffer)
                while (len > 0) {
                    fos.write(buffer, 0, len)
                    len = zis.read(buffer)
                }
                fos.close()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        val children = targetDir.listFiles()
        if (children != null && children.size == 1 && children[0].isDirectory) {
            val innerFolder = children[0]
            val tempDir = File(modelsDir, "${targetFolderName}_tmp")
            innerFolder.renameTo(tempDir)
            targetDir.deleteRecursively()
            tempDir.renameTo(targetDir)
        }

        return targetDir
    }

    fun deleteModel(name: String): Boolean {
        val file = File(modelsDir, name)
        return if (file.exists()) file.deleteRecursively() else false
    }

    fun deleteModel(modelInfo: VoskModelInfo): Boolean {
        return deleteModel(modelInfo.name)
    }

    private fun getFolderSize(folder: File): Long {
        var length = 0L
        val files = folder.listFiles() ?: return 0L
        for (file in files) {
            length += if (file.isFile) file.length() else getFolderSize(file)
        }
        return length
    }
}
