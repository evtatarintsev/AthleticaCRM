package org.athletica.crm.components.clients.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.getOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.AddClientNoteRequest
import org.athletica.crm.api.schemas.clients.ClientNoteSchema
import org.athletica.crm.api.schemas.clients.DeleteClientNoteRequest
import org.athletica.crm.api.schemas.clients.EditClientNoteRequest
import org.athletica.crm.components.clients.ClientsApiError
import org.athletica.crm.components.clients.toClientsApiError
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ClientNoteId
import org.athletica.crm.core.entityids.EmployeeId

/** Состояние вкладки «Заметки» карточки клиента. */
sealed class ClientNotesState {
    /** Первичная загрузка списка заметок. */
    data object Loading : ClientNotesState()

    /** Ошибка загрузки списка. */
    data class Error(val error: ClientsApiError) : ClientNotesState()

    /**
     * Список заметок загружен.
     * [draft] — текущее значение поля ввода новой заметки.
     * [submitting] — идёт ли запрос add/edit/delete.
     * [editingId] — заметка в режиме редактирования; null если добавляется новая.
     * [draftError] — ошибка валидации текущего черновика для отображения под полем.
     */
    data class Loaded(
        val notes: List<ClientNoteSchema>,
        val draft: String = "",
        val submitting: Boolean = false,
        val editingId: ClientNoteId? = null,
        val draftError: String? = null,
    ) : ClientNotesState()
}

/**
 * ViewModel вкладки «Заметки» в карточке клиента.
 * Бизнес-логика и работа с API живут здесь; композаблы вкладки stateless.
 * Проверка авторства для UI делается на стороне композабла через [isAuthoredBy];
 * серверная проверка прав остаётся источником правды.
 *
 * [clientId] — клиент, чьи заметки отображаем.
 */
class ClientNotesViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
) {
    var state: ClientNotesState by mutableStateOf(ClientNotesState.Loading)
        private set

    init {
        load()
    }

    /** Загружает или перезагружает список заметок клиента. */
    fun load() {
        scope.launch {
            state = ClientNotesState.Loading
            api.clients.notesList(clientId).fold(
                ifLeft = { state = ClientNotesState.Error(it.toClientsApiError()) },
                ifRight = { state = ClientNotesState.Loaded(it.notes) },
            )
        }
    }

    /** Обновляет текст черновика и сбрасывает ошибку валидации. */
    fun onDraftChange(value: String) {
        val loaded = state as? ClientNotesState.Loaded ?: return
        state = loaded.copy(draft = value, draftError = null)
    }

    /** Переключает заметку в режим редактирования, подставляя её текст в поле ввода. */
    fun onStartEdit(note: ClientNoteSchema) {
        val loaded = state as? ClientNotesState.Loaded ?: return
        state =
            loaded.copy(
                draft = note.text.value,
                editingId = note.id,
                draftError = null,
            )
    }

    /** Отменяет редактирование, очищая черновик. */
    fun onCancelEdit() {
        val loaded = state as? ClientNotesState.Loaded ?: return
        state = loaded.copy(draft = "", editingId = null, draftError = null)
    }

    /**
     * Отправляет текущий черновик: добавляет новую заметку либо сохраняет редактируемую.
     * При ошибке валидации текста заполняет [ClientNotesState.Loaded.draftError]; при ошибке API
     * переводит состояние в [ClientNotesState.Error].
     */
    fun onSubmit() {
        val loaded = state as? ClientNotesState.Loaded ?: return
        if (loaded.submitting) return
        val parsed =
            ClientNoteText.from(loaded.draft).getOrElse { error ->
                state = loaded.copy(draftError = error.message)
                return
            }
        state = loaded.copy(submitting = true, draftError = null)
        scope.launch {
            val result =
                if (loaded.editingId != null) {
                    api.clients.editNote(EditClientNoteRequest(loaded.editingId, parsed))
                } else {
                    api.clients.addNote(AddClientNoteRequest(clientId, parsed))
                }
            result.fold(
                ifLeft = { state = ClientNotesState.Error(it.toClientsApiError()) },
                ifRight = {
                    state = ClientNotesState.Loaded(it.notes)
                },
            )
        }
    }

    /** Удаляет заметку [noteId] (soft-delete на сервере). */
    fun onDelete(noteId: ClientNoteId) {
        val loaded = state as? ClientNotesState.Loaded ?: return
        if (loaded.submitting) return
        state = loaded.copy(submitting = true)
        scope.launch {
            api.clients.deleteNote(DeleteClientNoteRequest(noteId)).fold(
                ifLeft = { state = ClientNotesState.Error(it.toClientsApiError()) },
                ifRight = {
                    val draftPreserved = if (loaded.editingId == noteId) "" else loaded.draft
                    val editingPreserved = if (loaded.editingId == noteId) null else loaded.editingId
                    state =
                        ClientNotesState.Loaded(
                            notes = it.notes,
                            draft = draftPreserved,
                            editingId = editingPreserved,
                        )
                },
            )
        }
    }
}

/** Удобная проверка авторства заметки для отображения кнопок редактирования. */
fun ClientNoteSchema.isAuthoredBy(employeeId: EmployeeId): Boolean = author.id == employeeId.value
