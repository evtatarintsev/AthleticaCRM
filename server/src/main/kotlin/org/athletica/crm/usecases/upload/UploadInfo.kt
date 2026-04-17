package org.athletica.crm.usecases.upload

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.toUploadId
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.time.Duration
import kotlin.uuid.Uuid

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
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    UploadDbRecord(
                        id = row.asUuid("id").toUploadId(),
                        objectKey = row.asString("object_key"),
                        originalName = row.asString("original_name"),
                        contentType = row.asString("content_type"),
                        sizeBytes = row.asLong("size_bytes"),
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
    val id: UploadId,
    val objectKey: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
)
