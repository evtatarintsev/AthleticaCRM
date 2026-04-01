package org.athletica.crm.usecases.upload

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.storage.MinioService
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает информацию о загрузке по [id].
 * Доступ ограничен организацией из [ctx] — чужие загрузки не видны.
 */
context(db: Database, minioService: MinioService, ctx: RequestContext)
suspend fun getUploadInfo(id: Uuid): Either<CommonDomainError, UploadResponse> =
    either {
        val row =
            db
                .sql(
                    """
                    SELECT id, object_key, original_name, content_type, size_bytes
                    FROM uploads
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                ).bind("id", id)
                .bind("orgId", ctx.orgId.value)
                .firstOrNull { row, _ ->
                    UploadResponse(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        url = "", // заполняется ниже
                        originalName = row.get("original_name", String::class.java)!!,
                        contentType = row.get("content_type", String::class.java)!!,
                        sizeBytes = row.get("size_bytes", Long::class.java)!!,
                    ) to row.get("object_key", String::class.java)!!
                } ?: raise(CommonDomainError("UPLOAD_NOT_FOUND", "Загрузка не найдена"))

        row.first.copy(url = minioService.presignedGetUrl(row.second))
    }
