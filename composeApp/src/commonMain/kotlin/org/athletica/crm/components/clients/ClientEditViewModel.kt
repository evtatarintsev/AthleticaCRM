package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.api.schemas.customfields.CustomFieldValues
import org.athletica.crm.core.entityids.ClientId

/** Состояние загрузки данных клиента перед редактированием. */
sealed class ClientEditLoadState {
    /** Загрузка в процессе. */
    data object Loading : ClientEditLoadState()

    /** Данные загружены и форма готова к редактированию. */
    data class Loaded(
        val client: ClientDetailResponse,
        val defs: List<CustomFieldDefinitionSchema>,
    ) : ClientEditLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : ClientEditLoadState()
}

/**
 * ViewModel экрана редактирования клиента.
 * Загружает данные клиента по [clientId] и определения кастомных полей параллельно,
 * затем обрабатывает сохранение изменений.
 * При успешном сохранении вызывает [onSaved] с обновлёнными данными.
 */
class ClientEditViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
    private val onSaved: (ClientDetailResponse) -> Unit,
) {
    var loadState by mutableStateOf<ClientEditLoadState>(ClientEditLoadState.Loading)
        private set

    var saveState by mutableStateOf<ClientSaveState>(ClientSaveState.Idle)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            loadState = ClientEditLoadState.Loading
            var client: ClientDetailResponse? = null
            var defs: List<CustomFieldDefinitionSchema>? = null
            var error: ClientsApiError? = null

            val clientJob =
                launch {
                    api.clients.detail(clientId).fold(
                        ifLeft = { error = it.toClientsApiError() },
                        ifRight = { client = it },
                    )
                }
            val defsJob =
                launch {
                    api.customFields.list(CLIENT_ENTITY_TYPE).onRight { defs = it }
                }
            clientJob.join()
            defsJob.join()

            loadState =
                when {
                    error != null -> ClientEditLoadState.Error(error)
                    else -> ClientEditLoadState.Loaded(client!!, defs ?: emptyList())
                }
        }
    }

    /** Сохраняет изменения формы [form]. */
    fun onSave(form: ClientForm) {
        val loaded = loadState as? ClientEditLoadState.Loaded ?: return
        scope.launch {
            saveState = ClientSaveState.Saving
            api.clients
                .edit(
                    EditClientRequest(
                        id = loaded.client.id,
                        name = form.name,
                        avatarId = form.avatarId,
                        birthday = form.birthday,
                        gender = form.gender,
                        leadSourceId = form.leadSourceId,
                        customFields = form.customFields.toList(),
                    ),
                ).fold(
                    ifLeft = { saveState = ClientSaveState.Error(it.toClientsApiError()) },
                    ifRight = { updated: ClientDetailResponse ->
                        saveState = ClientSaveState.Idle
                        onSaved(updated)
                    },
                )
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        saveState = ClientSaveState.Idle
    }

    /** Строит [CustomFieldValues] из данных клиента и загруженных определений. */
    fun buildCustomFields(client: ClientDetailResponse, defs: List<CustomFieldDefinitionSchema>): CustomFieldValues = CustomFieldValues(defs).with(client.customFields).fold({ CustomFieldValues(defs) }, { it })

    private companion object {
        const val CLIENT_ENTITY_TYPE = "CLIENT"
    }
}
