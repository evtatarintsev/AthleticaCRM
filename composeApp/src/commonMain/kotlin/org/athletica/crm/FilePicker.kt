package org.athletica.crm

/**
 * Открывает системный диалог выбора изображения.
 * Возвращает тройку (bytes, filename, contentType) или null если пользователь отменил.
 */
expect suspend fun pickImageFile(): Triple<ByteArray, String, String>?
