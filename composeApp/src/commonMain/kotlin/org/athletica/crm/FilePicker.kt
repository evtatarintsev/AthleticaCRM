package org.athletica.crm

/**
 * Открывает системный диалог выбора изображения.
 * Возвращает тройку (bytes, filename, contentType) или null если пользователь отменил.
 */
expect suspend fun pickImageFile(): Triple<ByteArray, String, String>?

/**
 * Открывает системный диалог выбора любого файла.
 * Возвращает тройку (bytes, filename, contentType) или null если пользователь отменил.
 */
expect suspend fun pickAnyFile(): Triple<ByteArray, String, String>?
