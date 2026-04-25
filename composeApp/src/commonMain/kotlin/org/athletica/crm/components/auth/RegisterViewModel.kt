package org.athletica.crm.components.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.AccessTokenStorage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.platformCurrentTimezone

sealed class RegisterError {
    data object RegistrationFailed : RegisterError()
    data object ServiceUnavailable : RegisterError()
    data class ServerValidation(val message: String) : RegisterError()
}

data class RegisterForm(
    val organizationName: String = "",
    val name: String = "",
    val email: String = "",
    val password: String = "",
) {
    val isValid: Boolean
        get() = organizationName.isNotBlank() && name.isNotBlank() && email.isNotBlank() && password.isNotBlank()
}

sealed class RegisterState {
    data object Idle : RegisterState()
    data object Loading : RegisterState()
    data class Error(val error: RegisterError) : RegisterState()
}

class RegisterViewModel(
    private val api: ApiClient,
    private val tokenStorage: AccessTokenStorage,
    private val scope: CoroutineScope,
    private val onAuthenticated: () -> Unit,
) {
    var state by mutableStateOf<RegisterState>(RegisterState.Idle)
        private set

    var timezone by mutableStateOf(platformCurrentTimezone())
        private set

    fun onTimezoneChange(tz: String) {
        timezone = tz
    }

    fun onRegister(form: RegisterForm) {
        scope.launch {
            state = RegisterState.Loading
            api
                .signUp(
                    SignUpRequest(
                        companyName = form.organizationName,
                        userName = form.name,
                        login = form.email,
                        password = form.password,
                        timezone = timezone,
                    ),
                ).fold(
                    ifLeft = {
                        state =
                            RegisterState.Error(
                                when (it) {
                                    is ApiClientError.ValidationError -> RegisterError.ServerValidation(it.message)
                                    is ApiClientError.Unauthenticated -> RegisterError.RegistrationFailed
                                    is ApiClientError.Unavailable -> RegisterError.ServiceUnavailable
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
        state = RegisterState.Idle
    }
}
