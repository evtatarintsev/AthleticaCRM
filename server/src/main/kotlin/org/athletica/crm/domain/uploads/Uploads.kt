package org.athletica.crm.domain.uploads

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.core.entityids.toUploadId

interface Uploads {
    context(ctx: RequestContext, db: Database, raise: Raise<CommonDomainError>)
    suspend fun info(id: UploadId): UploadInfo
}

data class UploadInfo(
    val id: UploadId,
    val objectKey: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
)

class DbUploads : Uploads {
    context(ctx: RequestContext, db: Database, raise: Raise<CommonDomainError>)
    override suspend fun info(id: UploadId): UploadInfo {
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
                    UploadInfo(
                        id = row.asUuid("id").toUploadId(),
                        objectKey = row.asString("object_key"),
                        originalName = row.asString("original_name"),
                        contentType = row.asString("content_type"),
                        sizeBytes = row.asLong("size_bytes"),
                    )
                } ?: raise(CommonDomainError("UPLOAD_NOT_FOUND", Messages.UploadNotFound.localize()))

        return upload
    }
}


