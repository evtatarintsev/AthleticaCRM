package org.athletica.crm

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

actual suspend fun pickImageFile(): Triple<ByteArray, String, String>? {
    val deferred = CompletableDeferred<Uri?>()
    AndroidFilePicker.imageLauncher?.invoke(deferred) ?: return null
    val uri = deferred.await() ?: return null
    return readUri(uri)
}

actual suspend fun pickAnyFile(): Triple<ByteArray, String, String>? {
    val deferred = CompletableDeferred<Uri?>()
    AndroidFilePicker.anyFileLauncher?.invoke(deferred) ?: return null
    val uri = deferred.await() ?: return null
    return readUri(uri)
}

/** Читает содержимое файла по [uri] через ContentResolver и возвращает байты, имя и MIME-тип. */
private fun readUri(uri: Uri): Triple<ByteArray, String, String>? {
    val ctx = AndroidContextHolder.applicationContext ?: return null
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val mimeType = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    return Triple(bytes, filename, mimeType)
}
