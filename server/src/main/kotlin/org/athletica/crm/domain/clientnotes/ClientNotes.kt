package org.athletica.crm.domain.clientnotes

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ClientNoteId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Репозиторий заметок о клиентах. */
interface ClientNotes {
    /**
     * Возвращает не удалённую заметку по идентификатору.
     * Бросает `CLIENT_NOTE_NOT_FOUND`, если заметка не найдена либо принадлежит другой организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ClientNoteId): ClientNote

    /**
     * Возвращает список не удалённых заметок клиента [clientId] в порядке `created_at DESC`.
     * Изоляция по организации обеспечивается фильтрацией по `org_id = ctx.orgId`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(clientId: ClientId): List<ClientNote>

    /**
     * Создаёт новую заметку у клиента [clientId] от лица текущего сотрудника и сохраняет её в БД.
     * Возвращает созданную заметку с проставленным [ClientNote.createdAt].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun add(clientId: ClientId, text: ClientNoteText): ClientNote
}
