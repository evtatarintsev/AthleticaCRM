package org.athletica.crm.read.clients.import

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.clients.import.ClientImportParseRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportParseResponse
import org.athletica.crm.api.schemas.clients.import.ClientImportParseResponse.Companion.SAMPLE_ROWS_LIMIT
import org.athletica.crm.api.schemas.clients.import.ClientImportParseResponse.Companion.UNIQUE_VALUES_LIMIT
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.read.uploads.uploadById
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService

/** Максимальный размер CSV для импорта — 10 МиБ. */
private const val MAX_CSV_SIZE_BYTES = 10L * 1024 * 1024

/**
 * Загружает CSV из MinIO по [ClientImportParseRequest.uploadId], парсит и возвращает превью:
 * заголовки, образцы строк и уникальные значения по колонкам.
 *
 * Никакого suggested mapping не строится — пользователь задаёт всё вручную в UI.
 */
context(db: Database, minio: MinioService, ctx: EmployeeRequestContext, raise: Raise<AppError>)
suspend fun importClientsParse(request: ClientImportParseRequest): ClientImportParseResponse {
    val upload =
        db.transaction { uploadById(request.uploadId) }
            ?: raise(CommonDomainError("UPLOAD_NOT_FOUND", "Файл не найден"))

    if (upload.sizeBytes > MAX_CSV_SIZE_BYTES) {
        raise(CommonDomainError("FILE_TOO_BIG", "Файл превышает 10 МБ"))
    }
    if (!isCsv(upload.contentType, upload.originalName)) {
        raise(CommonDomainError("UNSUPPORTED_FORMAT", "Поддерживается только CSV"))
    }

    val bytes = minio.downloadObject(upload.objectKey)
    val parsed = CsvReader.parse(bytes)
    if (parsed.headers.isEmpty()) {
        raise(CommonDomainError("CSV_EMPTY_HEADER", "В CSV нет заголовков"))
    }

    val sampleRows = parsed.rows.take(SAMPLE_ROWS_LIMIT)
    val uniqueValuesPerColumn =
        parsed.headers
            .mapIndexed { index, header ->
                val unique =
                    parsed.rows
                        .asSequence()
                        .mapNotNull { row -> row.getOrNull(index)?.takeIf { it.isNotBlank() } }
                        .distinct()
                        .take(UNIQUE_VALUES_LIMIT)
                        .toList()
                header to unique
            }.toMap()

    return ClientImportParseResponse(
        originalName = upload.originalName,
        totalRows = parsed.rows.size,
        columns = parsed.headers,
        sampleRows = sampleRows,
        uniqueValuesPerColumn = uniqueValuesPerColumn,
    )
}

private fun isCsv(contentType: String, originalName: String): Boolean {
    val ct = contentType.lowercase()
    if (ct.startsWith("text/csv") || ct == "application/csv" || ct.startsWith("text/plain")) {
        return true
    }
    return originalName.lowercase().endsWith(".csv")
}
