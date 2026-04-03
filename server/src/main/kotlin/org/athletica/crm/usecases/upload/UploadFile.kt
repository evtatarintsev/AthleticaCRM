package org.athletica.crm.usecases.upload

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.storage.MinioService
import kotlin.uuid.Uuid

/**
 * Загружает файл в MinIO и сохраняет метаданные в таблице uploads.
 * Возвращает [UploadResponse] с presigned URL для доступа к файлу.
 */
context(db: Database, minioService: MinioService, ctx: RequestContext, audit: AuditLog)
suspend fun uploadFile(
    bytes: ByteArray,
    originalName: String,
    contentType: String,
): Either<CommonDomainError, UploadResponse> =
    either {
        if (bytes.isEmpty()) {
            raise(CommonDomainError("EMPTY_FILE", "Файл не может быть пустым"))
        }

        val uploadId = Uuid.random()
        val sanitizedName = originalName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val objectKey = "${ctx.orgId.value}/$uploadId/$sanitizedName"

        minioService.uploadObject(
            key = objectKey,
            stream = bytes.inputStream(),
            size = bytes.size.toLong(),
            contentType = contentType,
        )

        db
            .sql(
                """
                INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
                VALUES (:id, :orgId, :uploadedBy, :objectKey, :originalName, :contentType, :sizeBytes)
                """.trimIndent(),
            ).bind("id", uploadId)
            .bind("orgId", ctx.orgId.value)
            .bind("uploadedBy", ctx.userId.value)
            .bind("objectKey", objectKey)
            .bind("originalName", originalName)
            .bind("contentType", contentType)
            .bind("sizeBytes", bytes.size.toLong())
            .execute()

        UploadResponse(
            id = uploadId,
            url = minioService.presignedGetUrl(objectKey),
            originalName = originalName,
            contentType = contentType,
            sizeBytes = bytes.size.toLong(),
        ).also { audit.logCreate(it) }
    }

/** Логирует загрузку файла: тип сущности `"upload"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: UploadResponse) = logCreate("upload", result.id, Json.encodeToString(result))
