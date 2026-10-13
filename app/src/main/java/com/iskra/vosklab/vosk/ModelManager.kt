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

class ModelManager(
    private val context: Context? = null,
    private val customModelsDir: File? = null
) {

    companion object {
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_ZIP_TOTAL_SIZE = 1_073_741_824L // 1 GB
    }

    private val modelsDir: File
        get() = customModelsDir ?: File(context!!.filesDir, "vosk_models").apply { if (!exists()) mkdirs() }

    fun listInstalledModels(): List<VoskModelInfo> = listAvailableModels()

    fun listAvailableModels(): List<VoskModelInfo> {
        val dir = modelsDir
        val files = dir.listFiles() ?: return emptyList()

        return files.filter { it.isDirectory && isValidVoskModelDirectory(it) }.map { folder ->
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
        return if (file.exists() && file.isDirectory && isValidVoskModelDirectory(file)) file else null
    }

    fun isValidVoskModelDirectory(modelDir: File): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false

        // Structural validation only; import must not run inference.
        val finalMdl = File(modelDir, "am/final.mdl")
        if (!finalMdl.isFile || finalMdl.length() <= 0L) return false

        val modelConf = File(modelDir, "conf/model.conf")
        val mfccConf = File(modelDir, "conf/mfcc.conf")
        val hasConfig = (modelConf.isFile && modelConf.length() > 0L) ||
                (mfccConf.isFile && mfccConf.length() > 0L)

        val graphDir = File(modelDir, "graph")
        val hasGraph = graphDir.isDirectory &&
                graphDir.walkTopDown().any {
                    it.isFile && it.length() > 0L && it.extension.equals("fst", ignoreCase = true)
                }

        return hasConfig || hasGraph
    }

    fun copyDefaultModelFromAssetsIfNeeded() {
        val defaultDir = File(modelsDir, "vosk-model-small-pl-0.22")
        if (!defaultDir.exists() && context != null) {
            try {
                val assetManager = context.assets
                val modelZipStream = assetManager.open("vosk-model-small-pl-0.22.zip")
                importModelFromZipStream(modelZipStream, "vosk-model-small-pl-0.22")
            } catch (_: Exception) {
            }
        }
    }

    fun importModelFromZip(uri: Uri): Boolean {
        if (context == null) return false
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

        val destDirCanonical = targetDir.canonicalPath
        val zis = ZipInputStream(zipInputStream)
        var entry = zis.nextEntry
        val buffer = ByteArray(8192)

        var totalEntries = 0
        var totalBytesRead = 0L

        while (entry != null) {
            totalEntries++
            if (totalEntries > MAX_ZIP_ENTRIES) {
                targetDir.deleteRecursively()
                throw SecurityException("Za dużo wpisów w archiwum ZIP (przekroczono limit $MAX_ZIP_ENTRIES).")
            }

            val file = File(targetDir, entry.name)
            val fileCanonical = file.canonicalPath
            if (!fileCanonical.startsWith(destDirCanonical + File.separator) && fileCanonical != destDirCanonical) {
                targetDir.deleteRecursively()
                throw SecurityException("Wykryto zagrożenie Zip Slip: ścieżka '${entry.name}' poza katalogiem docelowym.")
            }

            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                val fos = FileOutputStream(file)
                var len = zis.read(buffer)
                while (len > 0) {
                    totalBytesRead += len
                    if (totalBytesRead > MAX_ZIP_TOTAL_SIZE) {
                        fos.close()
                        targetDir.deleteRecursively()
                        throw SecurityException("Przekroczono maksymalny rozmiar po rozpakowaniu ($MAX_ZIP_TOTAL_SIZE bajtów).")
                    }
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

        if (!isValidVoskModelDirectory(targetDir)) {
            targetDir.deleteRecursively()
            throw IllegalArgumentException("Brak wymaganej struktury modelu Vosk (am, graph, conf, model.conf).")
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
