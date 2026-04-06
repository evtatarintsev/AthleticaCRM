package org.athletica.crm.usecases.upload

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.MinioService
import kotlin.time.Duration
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает информацию о загрузке по [id].
 * Доступ ограничен организацией из [ctx] — чужие загрузки не видны.
 */
context(db: Database, minioService: MinioService, ctx: RequestContext)
suspend fun uploadInfo(id: Uuid, ttl: Duration): Either<CommonDomainError, UploadResponse> =
    either {
        val upload =
            db
                .sql(
                    """
                    SELECT id, object_key, original_name, content_type, size_bytes
                    FROM uploads
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId.value)
                .firstOrNull { row ->
                    UploadDbRecord(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        objectKey = row.get("object_key", String::class.java)!!,
                        originalName = row.get("original_name", String::class.java)!!,
                        contentType = row.get("content_type", String::class.java)!!,
                        sizeBytes = row.get("size_bytes", Long::class.java)!!,
                    )
                } ?: raise(CommonDomainError("UPLOAD_NOT_FOUND", Messages.UploadNotFound.localize()))

        UploadResponse(
            id = upload.id,
            url = minioService.presignedGetUrl(upload.objectKey, ttlSeconds = ttl.inWholeSeconds.toInt()),
            originalName = upload.originalName,
            contentType = upload.contentType,
            sizeBytes = upload.sizeBytes,
        )
    }

data class UploadDbRecord(
    val id: Uuid,
    val objectKey: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
)
