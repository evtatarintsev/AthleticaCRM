package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logDelete
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Удаляет документ [docId], прикреплённый к клиенту.
 * Проверяет принадлежность документа организации из [ctx].
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun deleteClientDoc(docId: Uuid): Either<CommonDomainError, Unit> =
    either {
        val doc =
            db
                .sql(
                    """
                    SELECT id, name FROM client_docs
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", docId)
                .bind("orgId", ctx.orgId.value)
                .firstOrNull { row ->
                    Pair(
                        row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        row.get("name", String::class.java)!!,
                    )
                }
                ?: raise(CommonDomainError("DOC_NOT_FOUND", Messages.UploadNotFound.localize()))

        db
            .sql("DELETE FROM client_docs WHERE id = :id AND org_id = :orgId")
            .bind("id", docId)
            .bind("orgId", ctx.orgId.value)
            .execute()

        audit.logDelete("client_doc", doc.first, doc.second)
    }
