package org.athletica.crm.utils

/**
 * Скачивает файл на клиентском устройстве.
 * [filename] — имя файла для сохранения (например, "clients.csv").
 * [data] — содержимое файла в виде ByteArray.
 *
 * Реализация зависит от платформы:
 * - Браузер: создаёт data URI с Base64 и программно кликает по ссылке
 * - Десктоп: открывает диалог Save As через JFileChooser
 */
expect fun downloadFile(filename: String, data: ByteArray)
