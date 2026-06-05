package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clients.AddClientNoteRequest
import org.athletica.crm.api.schemas.clients.ClientNoteSchema
import org.athletica.crm.api.schemas.clients.ClientNotesListRequest
import org.athletica.crm.api.schemas.clients.ClientNotesListResponse
import org.athletica.crm.api.schemas.clients.DeleteClientNoteRequest
import org.athletica.crm.api.schemas.clients.EditClientNoteRequest
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clientnotes.ClientNote
import org.athletica.crm.domain.clientnotes.ClientNotes
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Регистрирует маршруты для работы с заметками о клиентах.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.clientNotesRoutes(notes: ClientNotes) {
    get<ClientNotesListRequest, ClientNotesListResponse>("/clients/notes/list") { request ->
        db.transaction { listResponseFor(notes, request.clientId) }
    }

    post<AddClientNoteRequest, ClientNotesListResponse>("/clients/notes/add") { request ->
        db.transaction {
            notes.add(request.clientId, request.text)
            listResponseFor(notes, request.clientId)
        }
    }

    post<EditClientNoteRequest, ClientNotesListResponse>("/clients/notes/edit") { request ->
        db.transaction {
            val updated = notes.byId(request.noteId).withText(request.text)
            updated.save()
            listResponseFor(notes, updated.clientId)
        }
    }

    post<DeleteClientNoteRequest, ClientNotesListResponse>("/clients/notes/delete") { request ->
        db.transaction {
            val note = notes.byId(request.noteId)
            note.delete()
            listResponseFor(notes, note.clientId)
        }
    }
}

/**
 * Загружает актуальный список заметок клиента и обогащает их именами авторов.
 * Имя сотрудника собирается на слое routes/projection, в домене хранится только идентификатор.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun listResponseFor(
    notes: ClientNotes,
    clientId: ClientId,
): ClientNotesListResponse {
    val notesList = notes.list(clientId)
    val authorNames = tr.loadEmployeeNames(notesList.map { it.authorId }.toSet())
    return ClientNotesListResponse(
        notes = notesList.map { it.toSchema(authorNames) },
    )
}

/** Загружает имена сотрудников по набору идентификаторов одной выборкой. */
private suspend fun Transaction.loadEmployeeNames(ids: Set<EmployeeId>): Map<EmployeeId, String> {
    if (ids.isEmpty()) return emptyMap()
    return sql("SELECT id, name FROM employees WHERE id = ANY(:ids)")
        .bind("ids", ids.toList())
        .list { row ->
            val id = row.asUuid("id").toEmployeeId()
            val name = row.asString("name")
            id to name
        }
        .toMap()
}

private fun ClientNote.toSchema(authorNames: Map<EmployeeId, String>): ClientNoteSchema =
    ClientNoteSchema(
        id = id,
        text = text,
        author = PerformedBy(id = authorId.value, name = authorNames[authorId] ?: ""),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
