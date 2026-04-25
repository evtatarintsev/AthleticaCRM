package org.athletica.crm.components.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.AccessTokenStorage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.auth.LoginRequest

private val logger = Logger.withTag("LoginViewModel")

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val api: ApiClient,
    private val tokenStorage: AccessTokenStorage,
    private val scope: CoroutineScope,
    private val onAuthenticated: () -> Unit,
) {
    var state by mutableStateOf<LoginState>(LoginState.Idle)
        private set

    fun onLogin(login: String, password: String) {
        scope.launch {
            state = LoginState.Loading
            api
                .login(LoginRequest(username = login, password = password))
                .fold(
                    ifLeft = {
                        logger.e { "Ошибка входа: $login — $it" }
                        state =
                            LoginState.Error(
                                when (it) {
                                    is ApiClientError.ValidationError -> it.message
                                    is ApiClientError.Unauthenticated -> "Неверный логин или пароль"
                                    is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                                },
                            )
                    },
                    ifRight = {
                        tokenStorage.save(it.accessToken, it.refreshToken)
                        logger.i { "Вход выполнен успешно: $login" }
                        onAuthenticated()
                    },
                )
        }
    }

    fun onErrorDismissed() {
        state = LoginState.Idle
    }
}
