package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest

/** Состояние загрузки настроек организации. */
sealed class OrgSettingsLoadState {
    /** Загрузка в процессе. */
    data object Loading : OrgSettingsLoadState()

    /** Настройки загружены. */
    data class Loaded(val name: String, val timezone: String) : OrgSettingsLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : OrgSettingsLoadState()
}

/** Состояние операции сохранения настроек организации. */
sealed class OrgSettingsSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : OrgSettingsSaveState()

    /** Сохранение выполняется. */
    data object Saving : OrgSettingsSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : OrgSettingsSaveState()
}

/**
 * ViewModel экрана «Основные настройки» организации.
 * Загружает текущие настройки при создании; по успешному сохранению вызывает [onSaved].
 */
class OrgBasicSettingsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
) {
    var loadState by mutableStateOf<OrgSettingsLoadState>(OrgSettingsLoadState.Loading)
        private set

    var saveState by mutableStateOf<OrgSettingsSaveState>(OrgSettingsSaveState.Idle)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            api.orgSettings().fold(
                ifLeft = { loadState = OrgSettingsLoadState.Error(it.toSettingsApiError()) },
                ifRight = { loadState = OrgSettingsLoadState.Loaded(it.name, it.timezone) },
            )
        }
    }

    /** Сохраняет обновлённые [name] и [timezone]. */
    fun onSave(
        name: String,
        timezone: String,
    ) {
        scope.launch {
            saveState = OrgSettingsSaveState.Saving
            api
                .updateOrgSettings(UpdateOrgSettingsRequest(name = name.trim(), timezone = timezone))
                .fold(
                    ifLeft = { saveState = OrgSettingsSaveState.Error(it.toSettingsApiError()) },
                    ifRight = {
                        saveState = OrgSettingsSaveState.Idle
                        onSaved()
                    },
                )
        }
    }
}
