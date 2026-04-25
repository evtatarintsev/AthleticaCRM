package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.CreateRoleRequest
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.api.schemas.employees.UpdateRoleRequest
import org.athletica.crm.core.permissions.Permission
import kotlin.uuid.Uuid

/** Состояние загрузки списка ролей. */
sealed class RolesLoadState {
    /** Загрузка в процессе. */
    data object Loading : RolesLoadState()

    /** Список загружен. */
    data class Loaded(val roles: List<RoleItem>) : RolesLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : RolesLoadState()
}

/** Состояние операции сохранения роли. */
sealed class RolesSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : RolesSaveState()

    /** Сохранение выполняется. */
    data object Saving : RolesSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : RolesSaveState()
}

/**
 * ViewModel экрана «Роли».
 * Загружает список ролей при создании; поддерживает создание и редактирование.
 */
class RolesViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<RolesLoadState>(RolesLoadState.Loading)
        private set

    var saveState by mutableStateOf<RolesSaveState>(RolesSaveState.Idle)
        private set

    init {
        load()
    }

    /** Загружает список ролей. */
    fun load() {
        scope.launch {
            loadState = RolesLoadState.Loading
            api.roles().fold(
                ifLeft = { loadState = RolesLoadState.Error(it.toSettingsApiError()) },
                ifRight = { loadState = RolesLoadState.Loaded(it.roles) },
            )
        }
    }

    /** Создаёт роль; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onCreate(
        name: String,
        permissions: Set<Permission>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = RolesSaveState.Saving
            api.createRole(CreateRoleRequest(Uuid.random(), name, permissions)).fold(
                ifLeft = { saveState = RolesSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = RolesSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Обновляет роль; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onUpdate(
        id: Uuid,
        name: String,
        permissions: Set<Permission>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = RolesSaveState.Saving
            api.updateRole(UpdateRoleRequest(id, name, permissions)).fold(
                ifLeft = { saveState = RolesSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = RolesSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }
}
