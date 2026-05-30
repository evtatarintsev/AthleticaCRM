package org.athletica.crm.utils

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.athletica.crm.AndroidContextHolder
import java.io.File

/**
 * Сохраняет файл в папку Downloads.
 * На API 29+ использует [MediaStore.Downloads] (scoped storage).
 * На API 26–28 — публичную папку [Environment.DIRECTORY_DOWNLOADS].
 */
actual fun downloadFile(
    filename: String,
    data: ByteArray,
) {
    val ctx = AndroidContextHolder.applicationContext ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(data) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } else {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, filename).writeBytes(data)
    }
}
