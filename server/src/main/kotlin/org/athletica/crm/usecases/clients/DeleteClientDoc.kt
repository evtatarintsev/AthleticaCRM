package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asString
import org.athletica.crm.db.asUuid
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.i18n.Messages
import kotlin.uuid.Uuid

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
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    Pair(
                        row.asUuid("id"),
                        row.asString("name"),
                    )
                }
                ?: raise(CommonDomainError("DOC_NOT_FOUND", Messages.UploadNotFound.localize()))

        db
            .sql("DELETE FROM client_docs WHERE id = :id AND org_id = :orgId")
            .bind("id", docId)
            .bind("orgId", ctx.orgId)
            .execute()

        audit.logDelete("client_doc", doc.first, doc.second)
    }
