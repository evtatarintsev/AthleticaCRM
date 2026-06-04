package org.athletica.crm.domain.clientnotes

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ClientNoteId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toClientNoteId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.time.Clock

/** Реализация репозитория заметок о клиентах на основе PostgreSQL через R2DBC. */
class DbClientNotes : ClientNotes {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ClientNoteId): ClientNote {
        val note =
            tr.sql(
                """
                SELECT id, org_id, client_id, author_id, text, created_at, updated_at
                FROM client_notes
                WHERE id = :id AND org_id = :orgId AND deleted_at IS NULL
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.toDbClientNote() }

        return note
            ?: raise(CommonDomainError("CLIENT_NOTE_NOT_FOUND", "Заметка не найдена"))
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(clientId: ClientId): List<ClientNote> =
        tr.sql(
            """
            SELECT id, org_id, client_id, author_id, text, created_at, updated_at
            FROM client_notes
            WHERE client_id = :clientId AND org_id = :orgId AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            """.trimIndent(),
        )
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toDbClientNote() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun add(clientId: ClientId, text: ClientNoteText): ClientNote {
        val now = Clock.System.now()
        val id = ClientNoteId.new()
        tr.sql(
            """
            INSERT INTO client_notes (id, org_id, client_id, author_id, text, created_at)
            VALUES (:id, :orgId, :clientId, :authorId, :text, :now)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .bind("authorId", ctx.employeeId)
            .bind("text", text.value)
            .bind("now", now)
            .execute()

        return DbClientNote(
            id = id,
            orgId = ctx.orgId,
            clientId = clientId,
            authorId = ctx.employeeId,
            text = text,
            createdAt = now,
            updatedAt = null,
        )
    }

    context(raise: Raise<DomainError>)
    private fun io.r2dbc.spi.Row.toDbClientNote(): DbClientNote {
        val rawText = asString("text")
        val parsedText =
            ClientNoteText.from(rawText).getOrElse {
                raise(CommonDomainError("CLIENT_NOTE_TEXT_CORRUPTED", "Невалидный текст заметки в БД"))
            }
        return DbClientNote(
            id = asUuid("id").toClientNoteId(),
            orgId = OrgId(asUuid("org_id")),
            clientId = asUuid("client_id").toClientId(),
            authorId = asUuid("author_id").toEmployeeId(),
            text = parsedText,
            createdAt = asInstant("created_at"),
            updatedAt = asInstantOrNull("updated_at"),
        )
    }
}
