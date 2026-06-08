package org.athletica.crm.components.messaging

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.channels.ChannelIntegrationSchema
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.messaging.MessageSchema
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
     * [contacts] — контакты клиента; определяют доступность каналов и выбор адреса.
     * [selectedChannelId] — выбранный для отправки канал.
     * [selectedContactId] — выбранный адрес-получатель (когда подходящих контактов несколько).
     */
    data class Loaded(
        val messages: List<MessageSchema>,
        val channels: List<ChannelIntegrationSchema>,
        val contacts: List<ClientContactSchema>,
        val selectedChannelId: ChannelIntegrationId?,
        val selectedContactId: ClientContactId? = null,
        val draft: String = "",
        val sending: Boolean = false,
        val sendError: String? = null,
    ) : ConversationState() {
        /** Контакты, подходящие для канала [channel] (по совместимости типа с каналом). */
        fun addressOptions(channel: ChannelIntegrationSchema): List<ClientContactSchema> = contacts.filter { channel.channelType in it.type.compatibleChannels }

        /** Канал доступен, если это IN_APP или у клиента есть подходящий контакт. */
        fun isAvailable(channel: ChannelIntegrationSchema): Boolean = channel.channelType == ChannelType.IN_APP || addressOptions(channel).isNotEmpty()

        val selectedChannel: ChannelIntegrationSchema?
            get() = channels.firstOrNull { it.id == selectedChannelId }

        val canSend: Boolean
            get() = !sending && draft.isNotBlank() && selectedChannel?.let { isAvailable(it) } == true
    }
}

/**
 * ViewModel экрана диалога с клиентом.
 * Загружает ленту сообщений (с контактами клиента) и включённые каналы; отправляет сообщения
 * через выбранный канал и адрес. Композаблы экрана stateless.
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

    /** Загружает ленту с контактами и включённые каналы. */
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
            val selectedChannel = defaultChannel(channels, conversation.contacts)
            state =
                ConversationState.Loaded(
                    messages = conversation.messages,
                    channels = channels,
                    contacts = conversation.contacts,
                    selectedChannelId = selectedChannel?.id,
                    selectedContactId = selectedChannel?.let { defaultContactId(it, conversation.contacts) },
                )
        }
    }

    /** Обновляет текст черновика. */
    fun setDraft(value: String) {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(draft = value, sendError = null)
    }

    /** Выбирает канал для отправки; сбрасывает адрес-получатель на первый подходящий. */
    fun selectChannel(id: ChannelIntegrationId) {
        val loaded = state as? ConversationState.Loaded ?: return
        val channel = loaded.channels.firstOrNull { it.id == id }
        state =
            loaded.copy(
                selectedChannelId = id,
                selectedContactId = channel?.let { defaultContactId(it, loaded.contacts) },
                sendError = null,
            )
    }

    /** Выбирает контакт-получатель для отправки. */
    fun selectContact(id: ClientContactId) {
        val loaded = state as? ConversationState.Loaded ?: return
        state = loaded.copy(selectedContactId = id, sendError = null)
    }

    /** Отправляет текущий черновик через выбранный канал и адрес. */
    fun send() {
        val loaded = state as? ConversationState.Loaded ?: return
        val channel = loaded.selectedChannel ?: return
        if (!loaded.canSend) return
        state = loaded.copy(sending = true, sendError = null)
        scope.launch {
            api.messaging
                .send(SendMessageRequest(clientId, channel.id, loaded.draft.trim(), loaded.selectedContactId))
                .fold(
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

    private fun defaultChannel(
        channels: List<ChannelIntegrationSchema>,
        contacts: List<ClientContactSchema>,
    ): ChannelIntegrationSchema? {
        val reachable =
            channels.firstOrNull { c ->
                c.channelType == ChannelType.IN_APP ||
                    contacts.any { c.channelType in it.type.compatibleChannels }
            }
        return reachable ?: channels.firstOrNull()
    }

    private fun defaultContactId(
        channel: ChannelIntegrationSchema,
        contacts: List<ClientContactSchema>,
    ): ClientContactId? = contacts.firstOrNull { channel.channelType in it.type.compatibleChannels }?.id
}

/** Грубое текстовое представление ошибки для немедленного показа без @Composable-контекста. */
private fun ClientsApiError.asMessage(): String =
    when (this) {
        is ClientsApiError.ServerValidation -> message
        ClientsApiError.ServiceUnavailable -> "Сервис недоступен"
        ClientsApiError.SessionExpired -> "Сессия истекла"
    }
