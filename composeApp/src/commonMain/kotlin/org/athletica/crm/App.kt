package org.athletica.crm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.athletica.crm.api.AccessTokenStorage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.LoginRequest

private val logger = Logger.withTag("App")

/** Состояние авторизации пользователя. */
enum class AuthState {
    /** Проверка авторизации в процессе. */
    Checking,

    /** Пользователь авторизован. */
    Authenticated,

    /** Пользователь не авторизован. */
    Unauthenticated,
}

/**
 * Корневой composable приложения.
 * Проверяет авторизацию через [ApiClient.me] и отображает соответствующий экран.
 *
 * @param api клиент API
 */
private enum class UnauthScreen { Login, Register }

@Composable
fun App(
    tokenStorage: AccessTokenStorage,
    api: ApiClient
) {
    var authState by remember { mutableStateOf(AuthState.Checking) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var unauthScreen by remember { mutableStateOf(UnauthScreen.Login) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // TODO: на вебе браузер приложит httpOnly cookie автоматически
        authState =
            try {
                api.me()
                logger.i { "Сессия активна" }
                AuthState.Authenticated
            } catch (e: Exception) {
                logger.i(e) { "Сессия не найдена, требуется вход" }
                AuthState.Unauthenticated
            }
    }

    MaterialTheme {
        when (authState) {
            AuthState.Checking ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            AuthState.Authenticated ->
                MainScreen(api = api)

            AuthState.Unauthenticated ->
                when (unauthScreen) {
                    UnauthScreen.Login ->
                        LoginScreen(
                            errorMessage = loginError,
                            onErrorDismissed = { loginError = null },
                            onLogin = { login, password ->
                                scope.launch {
                                    loginError = null
                                    try {
                                        val response = api.login(LoginRequest(username = login, password = password))
                                        tokenStorage.save(response.accessToken, response.refreshToken)
                                        logger.i { "Вход выполнен успешно: $login" }
                                        authState = AuthState.Authenticated
                                    } catch (e: Exception) {
                                        logger.e(e) { "Ошибка входа: $login" }
                                        loginError = "Неверный логин или пароль"
                                    }
                                }
                            },
                            onNavigateToRegister = { unauthScreen = UnauthScreen.Register },
                        )

                    UnauthScreen.Register ->
                        RegisterScreen(
                            onNavigateToLogin = { unauthScreen = UnauthScreen.Login },
                        )
                }
        }
    }
}
