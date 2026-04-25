package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListRequest

/** Состояние экрана списка групп. */
sealed class GroupsState {
    /** Загрузка в процессе. */
    data object Loading : GroupsState()

    /** Список загружен. */
    data class Loaded(val groups: List<GroupListItem>) : GroupsState()

    /** Ошибка загрузки. */
    data class Error(val error: GroupsApiError) : GroupsState()
}

/**
 * ViewModel экрана списка групп.
 * Загружает список через [api]; повторная загрузка — через [load].
 */
class GroupsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<GroupsState>(GroupsState.Loading)
        private set

    /** Загружает (или перезагружает) список групп. */
    fun load() {
        scope.launch {
            state = GroupsState.Loading
            api.groupList(GroupListRequest()).fold(
                ifLeft = { state = GroupsState.Error(it.toGroupsApiError()) },
                ifRight = { state = GroupsState.Loaded(it.groups) },
            )
        }
    }
}
