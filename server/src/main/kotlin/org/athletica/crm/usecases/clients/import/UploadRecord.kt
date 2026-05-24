package org.athletica.crm.usecases.clients.import

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString

/**
 * Метаданные ранее загруженного файла, нужные обоим use-case импорта (parse и commit).
 * Принадлежность к организации проверяется при выборке.
 */
internal data class UploadRecord(
    val objectKey: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
)

/**
 * Загружает метаданные [UploadRecord] по [uploadId] из таблицы `uploads`,
 * фильтруя по `org_id` из [RequestContext]. Если запись не найдена — raise `UPLOAD_NOT_FOUND`.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
internal suspend fun uploadRecordById(uploadId: UploadId): UploadRecord =
    tr.sql(
        """
        SELECT object_key, original_name, content_type, size_bytes
        FROM uploads
        WHERE id = :id AND org_id = :orgId
        """.trimIndent(),
    ).bind("id", uploadId)
        .bind("orgId", ctx.orgId)
        .firstOrNull { row ->
            UploadRecord(
                objectKey = row.asString("object_key"),
                originalName = row.asString("original_name"),
                contentType = row.asString("content_type"),
                sizeBytes = row.asLong("size_bytes"),
            )
        } ?: raise(CommonDomainError("UPLOAD_NOT_FOUND", "Файл не найден"))
