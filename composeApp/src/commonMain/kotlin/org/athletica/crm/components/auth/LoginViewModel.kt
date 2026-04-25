package org.athletica.crm.components.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.AccessTokenStorage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.auth.LoginRequest

sealed class LoginError {
    data object InvalidCredentials : LoginError()
    data object ServiceUnavailable : LoginError()
    data class ServerValidation(val message: String) : LoginError()
}

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Error(val error: LoginError) : LoginState()
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
                        state =
                            LoginState.Error(
                                when (it) {
                                    is ApiClientError.ValidationError -> LoginError.ServerValidation(it.message)
                                    is ApiClientError.Unauthenticated -> LoginError.InvalidCredentials
                                    is ApiClientError.Unavailable -> LoginError.ServiceUnavailable
                                },
                            )
                    },
                    ifRight = {
                        tokenStorage.save(it.accessToken, it.refreshToken)
                        onAuthenticated()
                    },
                )
        }
    }

    fun onErrorDismissed() {
        state = LoginState.Idle
    }
}
