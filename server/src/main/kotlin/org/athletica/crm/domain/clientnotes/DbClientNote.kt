package org.athletica.crm.domain.clientnotes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ClientNoteId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

internal data class DbClientNote(
    override val id: ClientNoteId,
    override val orgId: OrgId,
    override val clientId: ClientId,
    override val authorId: EmployeeId,
    override val text: ClientNoteText,
    override val createdAt: Instant,
    override val updatedAt: Instant?,
) : ClientNote {
    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            UPDATE client_notes
            SET text = :text, updated_at = :updatedAt
            WHERE id = :id AND org_id = :orgId AND deleted_at IS NULL
            """.trimIndent(),
        )
            .bind("text", text.value)
            .bind("updatedAt", updatedAt)
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() {
        requireAuthor()
        val now = Clock.System.now()
        tr.sql(
            """
            UPDATE client_notes
            SET deleted_at = :now
            WHERE id = :id AND org_id = :orgId AND deleted_at IS NULL
            """.trimIndent(),
        )
            .bind("now", now)
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun withText(newText: ClientNoteText): ClientNote {
        requireAuthor()
        return copy(text = newText, updatedAt = Clock.System.now())
    }

    /** Проверяет, что заметку правит/удаляет её автор. Иначе — `PERMISSION_DENIED`. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    private fun requireAuthor() {
        if (authorId != ctx.employeeId) {
            raise(CommonDomainError("PERMISSION_DENIED", "Заметку может изменять только её автор"))
        }
    }
}
