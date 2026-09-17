package org.athletica.crm.read.uploads

import org.athletica.crm.core.entityids.UploadId

/**
 * Метаданные загруженного файла в том виде, в каком они лежат в БД.
 *
 * Read-проекции отдают именно эту строку, а не `UploadResponse`: ссылка для скачивания
 * подписывается объектным хранилищем и не может быть получена из SQL. Подпись —
 * задача слоя routes, который дособирает ответ.
 */
data class UploadRow(
    /** Идентификатор загрузки. */
    val id: UploadId,
    /** Ключ объекта в хранилище; используется для подписи ссылки. */
    val objectKey: String,
    /** Исходное имя файла, под которым он был загружен. */
    val originalName: String,
    /** MIME-тип содержимого. */
    val contentType: String,
    /** Размер файла в байтах. */
    val sizeBytes: Long,
)
