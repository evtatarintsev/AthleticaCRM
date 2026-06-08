package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValues
import org.athletica.crm.core.entityids.ClientId

/** Состояние загрузки определений кастомных полей на экране создания клиента. */
sealed class ClientCreateDefsState {
    /** Определения загружаются. */
    data object Loading : ClientCreateDefsState()

    /** Определения загружены. */
    data class Loaded(val defs: List<CustomFieldDefinition>) : ClientCreateDefsState()
}

/**
 * ViewModel экрана создания клиента.
 * Загружает определения кастомных полей и отправляет запрос создания через [api].
 * При успехе вызывает [onCreated].
 */
class ClientCreateViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onCreated: () -> Unit,
) {
    var defsState by mutableStateOf<ClientCreateDefsState>(ClientCreateDefsState.Loading)
        private set

    var state by mutableStateOf<ClientSaveState>(ClientSaveState.Idle)
        private set

    init {
        loadDefs()
    }

    private fun loadDefs() {
        scope.launch {
            api.customFields.list(CLIENT_ENTITY_TYPE).onRight { defs ->
                defsState = ClientCreateDefsState.Loaded(defs)
            }
        }
    }

    /** Создаёт клиента по данным [form]. */
    fun onCreate(form: ClientForm) {
        scope.launch {
            state = ClientSaveState.Saving
            api.clients
                .create(
                    CreateClientRequest(
                        id = ClientId.new(),
                        name = form.name,
                        avatarId = form.avatarId,
                        birthday = form.birthday,
                        gender = form.gender,
                        leadSourceId = form.leadSourceId,
                        customFields = form.customFields.toList(),
                        contacts = form.contactInputs(),
                    ),
                ).fold(
                    ifLeft = { state = ClientSaveState.Error(it.toClientsApiError()) },
                    ifRight = {
                        state = ClientSaveState.Idle
                        onCreated()
                    },
                )
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onErrorDismissed() {
        state = ClientSaveState.Idle
    }

    /** Возвращает пустой [CustomFieldValues] для загруженных определений. */
    fun emptyCustomFields(): CustomFieldValues {
        val defs = (defsState as? ClientCreateDefsState.Loaded)?.defs ?: emptyList()
        return CustomFieldValues(defs)
    }

    private companion object {
        const val CLIENT_ENTITY_TYPE = "CLIENT"
    }
}
