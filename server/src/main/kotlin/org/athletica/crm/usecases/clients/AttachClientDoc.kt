package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.time.Instant
import kotlin.uuid.toKotlinUuid

/**
 * Прикрепляет ранее загруженный файл [request.uploadId] к клиенту [request.clientId].
 * Возвращает созданный [ClientDoc] или ошибку если клиент/загрузка не найдены.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun attachClientDoc(request: AttachClientDocRequest): Either<CommonDomainError, ClientDoc> =
    either {
        db
            .sql("SELECT 1 FROM clients WHERE id = :id AND org_id = :orgId")
            .bind("id", request.clientId)
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { true }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        db
            .sql("SELECT 1 FROM uploads WHERE id = :id AND org_id = :orgId")
            .bind("id", request.uploadId)
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { true }
            ?: raise(CommonDomainError("UPLOAD_NOT_FOUND", Messages.UploadNotFound.localize()))

        val doc =
            db
                .sql(
                    """
                    INSERT INTO client_docs (client_id, org_id, upload_id, name)
                    VALUES (:clientId, :orgId, :uploadId, :name)
                    RETURNING id, created_at
                    """.trimIndent(),
                )
                .bind("clientId", request.clientId)
                .bind("orgId", ctx.orgId.value)
                .bind("uploadId", request.uploadId)
                .bind("name", request.name)
                .firstOrNull { row ->
                    ClientDoc(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        uploadId = request.uploadId,
                        name = request.name,
                        createdAt =
                            row
                                .get("created_at", java.time.OffsetDateTime::class.java)!!
                                .toInstant()
                                .let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
                    )
                }!!

        audit.logCreate("client_doc", doc.id, doc.id.toString())
        doc
    }
