package org.athletica.crm.components.settings.channels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.channels.ChannelIntegrationDto
import org.athletica.crm.api.schemas.channels.CreateChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.DeleteChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.UpdateChannelIntegrationRequest
import org.athletica.crm.components.clients.ClientsApiError
import org.athletica.crm.components.clients.toClientsApiError
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.messaging.ChannelType

/** Открытый в редакторе канал: новый ([id] = null) либо существующий. */
data class ChannelEditor(
    val id: ChannelIntegrationId?,
    val channelType: ChannelType,
    val name: String,
    val enabled: Boolean,
)

/** Состояние экрана настройки каналов связи. */
sealed class ChannelsState {
    /** Идёт загрузка списка. */
    data object Loading : ChannelsState()

    /** Ошибка загрузки списка. */
    data class Error(val error: ClientsApiError) : ChannelsState()

    /**
     * Список загружен.
     * [editor] — открытый редактор (null = режим списка).
     * [saving] — выполняется сохранение/удаление.
     * [saveError] — ошибка последней операции сохранения.
     */
    data class Loaded(
        val channels: List<ChannelIntegrationDto>,
        val editor: ChannelEditor? = null,
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : ChannelsState()
}

/**
 * ViewModel экрана «Каналы связи».
 * Загружает интеграции, поддерживает создание, редактирование, включение/выключение и удаление.
 */
class ChannelsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state: ChannelsState by mutableStateOf(ChannelsState.Loading)
        private set

    init {
        load()
    }

    /** Загружает список каналов. */
    fun load() {
        scope.launch {
            state = ChannelsState.Loading
            api.channels.list().fold(
                ifLeft = { state = ChannelsState.Error(it.toClientsApiError()) },
                ifRight = { state = ChannelsState.Loaded(it.channels) },
            )
        }
    }

    /** Открывает редактор для создания нового канала. */
    fun openCreate() {
        val loaded = state as? ChannelsState.Loaded ?: return
        state = loaded.copy(editor = ChannelEditor(id = null, channelType = ChannelType.SMS, name = "", enabled = true))
    }

    /** Открывает редактор существующего канала [dto]. */
    fun openEdit(dto: ChannelIntegrationDto) {
        val loaded = state as? ChannelsState.Loaded ?: return
        state =
            loaded.copy(
                editor = ChannelEditor(id = dto.id, channelType = dto.channelType, name = dto.name, enabled = dto.enabled),
            )
    }

    /** Закрывает редактор без сохранения. */
    fun closeEditor() {
        val loaded = state as? ChannelsState.Loaded ?: return
        state = loaded.copy(editor = null, saveError = null)
    }

    /** Меняет тип канала в редакторе (только при создании). */
    fun setType(type: ChannelType) {
        val loaded = state as? ChannelsState.Loaded ?: return
        val editor = loaded.editor ?: return
        state = loaded.copy(editor = editor.copy(channelType = type))
    }

    /** Меняет название канала в редакторе. */
    fun setName(name: String) {
        val loaded = state as? ChannelsState.Loaded ?: return
        val editor = loaded.editor ?: return
        state = loaded.copy(editor = editor.copy(name = name), saveError = null)
    }

    /** Меняет флаг активности канала в редакторе. */
    fun setEnabled(enabled: Boolean) {
        val loaded = state as? ChannelsState.Loaded ?: return
        val editor = loaded.editor ?: return
        state = loaded.copy(editor = editor.copy(enabled = enabled))
    }

    /** Сохраняет открытый редактор (создание или обновление). */
    fun save() {
        val loaded = state as? ChannelsState.Loaded ?: return
        val editor = loaded.editor ?: return
        if (loaded.saving || editor.name.isBlank()) return
        state = loaded.copy(saving = true, saveError = null)
        scope.launch {
            val result =
                if (editor.id == null) {
                    api.channels.create(
                        CreateChannelIntegrationRequest(
                            id = ChannelIntegrationId.new(),
                            channelType = editor.channelType,
                            name = editor.name.trim(),
                        ),
                    )
                } else {
                    api.channels.update(
                        UpdateChannelIntegrationRequest(
                            id = editor.id,
                            name = editor.name.trim(),
                            enabled = editor.enabled,
                        ),
                    )
                }
            result.fold(
                ifLeft = { error ->
                    val current = state as? ChannelsState.Loaded ?: return@fold
                    state = current.copy(saving = false, saveError = error.toClientsApiError().asMessage())
                },
                ifRight = { reloadAfterMutation() },
            )
        }
    }

    /** Удаляет канал [id]. */
    fun delete(id: ChannelIntegrationId) {
        val loaded = state as? ChannelsState.Loaded ?: return
        if (loaded.saving) return
        state = loaded.copy(saving = true, saveError = null)
        scope.launch {
            api.channels.delete(DeleteChannelIntegrationRequest(id)).fold(
                ifLeft = { error ->
                    val current = state as? ChannelsState.Loaded ?: return@fold
                    state = current.copy(saving = false, saveError = error.toClientsApiError().asMessage())
                },
                ifRight = { reloadAfterMutation() },
            )
        }
    }

    private suspend fun reloadAfterMutation() {
        api.channels.list().fold(
            ifLeft = { state = ChannelsState.Error(it.toClientsApiError()) },
            ifRight = { state = ChannelsState.Loaded(it.channels) },
        )
    }
}

/** Грубое текстовое представление ошибки для немедленного показа без @Composable-контекста. */
private fun ClientsApiError.asMessage(): String =
    when (this) {
        is ClientsApiError.ServerValidation -> message
        ClientsApiError.ServiceUnavailable -> "Сервис недоступен"
        ClientsApiError.SessionExpired -> "Сессия истекла"
    }
