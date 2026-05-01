package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.ChangePasswordRequest

/** Состояние операции смены пароля. */
sealed class ChangePasswordSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : ChangePasswordSaveState()

    /** Сохранение выполняется. */
    data object Saving : ChangePasswordSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : ChangePasswordSaveState()
}

/**
 * ViewModel экрана смены пароля.
 * По успешной смене вызывает [onChanged].
 */
class ChangePasswordViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    var saveState by mutableStateOf<ChangePasswordSaveState>(ChangePasswordSaveState.Idle)
        private set

    /** Отправляет запрос на смену пароля. */
    fun onSave(
        oldPassword: String,
        newPassword: String,
    ) {
        scope.launch {
            saveState = ChangePasswordSaveState.Saving
            api
                .profile.changePassword(ChangePasswordRequest(oldPassword = oldPassword, newPassword = newPassword))
                .fold(
                    ifLeft = { saveState = ChangePasswordSaveState.Error(it.toSettingsApiError()) },
                    ifRight = {
                        saveState = ChangePasswordSaveState.Idle
                        onChanged()
                    },
                )
        }
    }

    /** Сбрасывает ошибку при редактировании полей. */
    fun onErrorDismissed() {
        if (saveState is ChangePasswordSaveState.Error) {
            saveState = ChangePasswordSaveState.Idle
        }
    }
}
