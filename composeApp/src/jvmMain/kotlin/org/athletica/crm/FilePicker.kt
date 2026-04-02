package org.athletica.crm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual suspend fun pickImageFile(): Triple<ByteArray, String, String>? =
    withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, "Выбрать фото", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            name.lowercase().let {
                it.endsWith(".jpg") ||
                    it.endsWith(".jpeg") ||
                    it.endsWith(".png") ||
                    it.endsWith(".webp")
            }
        }
        dialog.isVisible = true
        val filename = dialog.file ?: return@withContext null
        val dir = dialog.directory ?: return@withContext null
        withContext(Dispatchers.IO) {
            val bytes = File(dir, filename).readBytes()
            val contentType =
                when (filename.substringAfterLast('.').lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
            Triple(bytes, filename, contentType)
        }
    }
