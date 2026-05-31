package org.athletica.crm.components.messaging

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.channels.ChannelIntegrationDto
import org.athletica.crm.api.schemas.clients.AddClientContactRequest
import org.athletica.crm.api.schemas.clients.ClientContactDto
import org.athletica.crm.api.schemas.clients.DeleteClientContactRequest
import org.athletica.crm.api.schemas.messaging.MessageDto
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.components.clients.ClientsApiError
import org.athletica.crm.components.clients.toClientsApiError
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.messaging.ChannelType

/** Состояние экрана диалога с клиентом. */
sealed class ConversationState {
    /** Идёт первичная загрузка. */
    data object Loading : ConversationState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : ConversationState()

    /**
     * Диалог загружен.
     * [channels] — только включённые интеграции.
     * [contacts] — контакты клиента по каналам.
     * [selectedChannelId] — выбранный для отправки канал.
     * [showContacts] — раскрыта ли панель управления контактами.
     */
    data class Loaded(
        val messages: List<MessageDto>,
        val channels: List<ChannelIntegrationDto>,
        val contacts: List<ClientContactDto>,
        val selectedChannelId: ChannelIntegrationId?,
        val draft: String = "",
        val sending: Boolean = false,
        val sendError: String? = null,
        val showContacts: Boolean = false,
        val newContactType: ChannelType = ChannelType.SMS,
        val newContactAddress: String = "",
    ) : ConversationState() {
        /** Канал доступен, если это IN_APP или у клиента есть контакт нужного типа. */
        fun isAvailable(channel: ChannelIntegrationDto): Boolean = channel.channelType == ChannelType.IN_APP || contacts.any { it.channelType == channel.channelType }

        val selectedChannel: ChannelIntegrationDto?
            get() = channels.firstOrNull { it.id == selectedChannelId }

        val canSend: Boolean
            get() = !sending && draft.isNotBlank() && selectedChannel?.let { isAvailable(it) } == true
    }
}

/**
 * ViewModel экрана диалога с клиентом.
 * Загружает ленту сообщений, включённые каналы и контакты клиента; отправляет сообщения
 * и управляет контактами. Композаблы экрана stateless.
 */
class ConversationViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
) {
    var state: ConversationState by mutableStateOf(ConversationState.Loading)
        private set

    init {
        load()
    }

    /** Загружает ленту, каналы и контакты. */
    fun load() {
        scope.launch {
            state = ConversationState.Loading
            val conversation =
                api.messaging.conversation(clientId).fold(
                    ifLeft = {
                        state = ConversationState.Error(it.toClientsApiError())
                        return@launch
                    },
                    ifRight = { it },
                )
            val channels =
                api.channels.list().fold(
                    ifLeft = {
                        state = ConversationState.Error(it.toClientsApiError())
                        return@launch
                    },
                    ifRight = { it.channels.filter { c -> c.enabled } },
                )
            val contacts =
                api.clients.contactsList(clientId).fold(
                    ifLeft = {
                        state = ConversationState.Error(it.toClientsApiError())
                        return@launch
                    },
                    ifRight = { it.contacts },
                )
            state =
                ConversationState.Loaded(
                    messages = conversation.messages,
                    channels = channels,
                    contacts = contacts,
                    selectedChannelId = defaultChannelId(channels, contacts),
                )
        }
    }

    /** Обновляет текст черновика. */
    fun setDraft(value: String) {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(draft = value, sendError = null)
    }

    /** Выбирает канал для отправки. */
    fun selectChannel(id: ChannelIntegrationId) {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(selectedChannelId = id, sendError = null)
    }

    /** Отправляет текущий черновик через выбранный канал. */
    fun send() {
        val loaded = state as? ConversationState.Loaded ?: return
        val channel = loaded.selectedChannel ?: return
        if (!loaded.canSend) return
        state = loaded.copy(sending = true, sendError = null)
        scope.launch {
            api.messaging.send(SendMessageRequest(clientId, channel.id, loaded.draft.trim())).fold(
                ifLeft = { error ->
                    val current = state as? ConversationState.Loaded ?: return@fold
                    state = current.copy(sending = false, sendError = error.toClientsApiError().asMessage())
                },
                ifRight = { response ->
                    val current = state as? ConversationState.Loaded ?: return@fold
                    state = current.copy(messages = response.messages, draft = "", sending = false)
                },
            )
        }
    }

    /** Показывает/скрывает панель контактов. */
    fun toggleContacts() {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(showContacts = !loaded.showContacts)
    }

    /** Меняет тип нового контакта. */
    fun setNewContactType(type: ChannelType) {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(newContactType = type)
    }

    /** Меняет адрес нового контакта. */
    fun setNewContactAddress(value: String) {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(newContactAddress = value)
    }

    /** Добавляет новый контакт клиенту. */
    fun addContact() {
        val loaded = state as? ConversationState.Loaded ?: return
        if (loaded.newContactAddress.isBlank()) return
        scope.launch {
            api.clients
                .addContact(AddClientContactRequest(clientId, loaded.newContactType, loaded.newContactAddress.trim()))
                .fold(
                    ifLeft = { error ->
                        val current = state as? ConversationState.Loaded ?: return@fold
                        state = current.copy(sendError = error.toClientsApiError().asMessage())
                    },
                    ifRight = { response ->
                        val current = state as? ConversationState.Loaded ?: return@fold
                        state = current.copy(contacts = response.contacts, newContactAddress = "")
                    },
                )
        }
    }

    /** Удаляет контакт клиента. */
    fun deleteContact(contactId: ClientContactId) {
        val loaded = state as? ConversationState.Loaded ?: return
        scope.launch {
            api.clients.deleteContact(DeleteClientContactRequest(clientId, contactId)).fold(
                ifLeft = { error ->
                    val current = state as? ConversationState.Loaded ?: return@fold
                    state = current.copy(sendError = error.toClientsApiError().asMessage())
                },
                ifRight = { response ->
                    val current = state as? ConversationState.Loaded ?: return@fold
                    state = current.copy(contacts = response.contacts)
                },
            )
        }
    }

    private fun defaultChannelId(
        channels: List<ChannelIntegrationDto>,
        contacts: List<ClientContactDto>,
    ): ChannelIntegrationId? {
        val reachable =
            channels.firstOrNull { c ->
                c.channelType == ChannelType.IN_APP || contacts.any { it.channelType == c.channelType }
            }
        return (reachable ?: channels.firstOrNull())?.id
    }
}

/** Грубое текстовое представление ошибки для немедленного показа без @Composable-контекста. */
private fun ClientsApiError.asMessage(): String =
    when (this) {
        is ClientsApiError.ServerValidation -> message
        ClientsApiError.ServiceUnavailable -> "Сервис недоступен"
        ClientsApiError.SessionExpired -> "Сессия истекла"
    }
