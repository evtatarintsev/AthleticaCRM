package org.athletica.crm.read.uploads

import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString

/** Метаданные файла, доступного в организации текущего сотрудника. */
context(ctx: EmployeeRequestContext, tr: Transaction)
internal suspend fun uploadById(id: UploadId): UploadRow? =
    tr.sql(
        """
        SELECT object_key, original_name, content_type, size_bytes
        FROM uploads
        WHERE id = :id AND org_id = :orgId
        """.trimIndent(),
    ).bind("id", id)
        .bind("orgId", ctx.orgId)
        .firstOrNull { row ->
            UploadRow(
                id = id,
                objectKey = row.asString("object_key"),
                originalName = row.asString("original_name"),
                contentType = row.asString("content_type"),
                sizeBytes = row.asLong("size_bytes"),
            )
        }
