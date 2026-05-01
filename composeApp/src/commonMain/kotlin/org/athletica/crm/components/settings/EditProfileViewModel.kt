package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.core.entityids.UploadId

/** Состояние загрузки профиля. */
sealed class EditProfileLoadState {
    /** Загрузка в процессе. */
    data object Loading : EditProfileLoadState()

    /** Профиль загружен. */
    data class Loaded(val name: String, val avatarId: UploadId?) : EditProfileLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : EditProfileLoadState()
}

/** Состояние операции сохранения профиля. */
sealed class EditProfileSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : EditProfileSaveState()

    /** Сохранение выполняется. */
    data object Saving : EditProfileSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : EditProfileSaveState()
}

/**
 * ViewModel экрана редактирования профиля.
 * Загружает данные при создании; по успешному сохранению вызывает [onSaved].
 */
class EditProfileViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
) {
    var loadState by mutableStateOf<EditProfileLoadState>(EditProfileLoadState.Loading)
        private set

    var saveState by mutableStateOf<EditProfileSaveState>(EditProfileSaveState.Idle)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            api.profile.me().fold(
                ifLeft = { loadState = EditProfileLoadState.Error(it.toSettingsApiError()) },
                ifRight = { loadState = EditProfileLoadState.Loaded(it.name, it.avatarId) },
            )
        }
    }

    /** Сохраняет обновлённые [name] и [avatarId]. */
    fun onSave(
        name: String,
        avatarId: UploadId?,
    ) {
        scope.launch {
            saveState = EditProfileSaveState.Saving
            api
                .profile.update(UpdateMeRequest(name = name.trim(), avatarId = avatarId))
                .fold(
                    ifLeft = { saveState = EditProfileSaveState.Error(it.toSettingsApiError()) },
                    ifRight = {
                        saveState = EditProfileSaveState.Idle
                        onSaved()
                    },
                )
        }
    }
}
