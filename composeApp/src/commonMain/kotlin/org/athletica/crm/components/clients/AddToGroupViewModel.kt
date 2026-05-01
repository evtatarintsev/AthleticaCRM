package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId

/** Состояние шторки добавления клиентов в группу. */
sealed class AddToGroupState {
    /** Загрузка списка групп. */
    data object Loading : AddToGroupState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : AddToGroupState()

    /**
     * Список групп загружен.
     * [isAdding] — запрос добавления в выбранную группу выполняется.
     */
    data class Loaded(
        val groups: List<GroupSelectItem>,
        val isAdding: Boolean = false,
    ) : AddToGroupState()
}

/**
 * ViewModel шторки добавления клиентов в группу.
 * Загружает список доступных групп и выполняет добавление [clientIds] в выбранную.
 * При успехе вызывает [onGroupAdded].
 */
class AddToGroupViewModel(
    private val api: ApiClient,
    private val clientIds: List<ClientId>,
    private val existingGroupIds: Set<GroupId>,
    private val scope: CoroutineScope,
    private val onGroupAdded: () -> Unit,
) {
    var state by mutableStateOf<AddToGroupState>(AddToGroupState.Loading)
        private set

    init {
        loadGroups()
    }

    private fun loadGroups() {
        scope.launch {
            api.groups.listForSelect().fold(
                ifLeft = { state = AddToGroupState.Error(it.toClientsApiError()) },
                ifRight = { groups ->
                    val filtered = groups.filter { it.id !in existingGroupIds }
                    state = AddToGroupState.Loaded(filtered)
                },
            )
        }
    }

    /** Добавляет [clientIds] в группу [groupId]. */
    fun onGroupSelected(groupId: GroupId) {
        val loaded = state as? AddToGroupState.Loaded ?: return
        state = loaded.copy(isAdding = true)
        scope.launch {
            api.clients
                .addToGroup(AddClientsToGroupRequest(clientIds = clientIds, groupId = groupId))
                .onRight { onGroupAdded() }
                .onLeft { state = loaded.copy(isAdding = false) }
        }
    }
}
