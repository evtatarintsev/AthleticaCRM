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
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.athletica.crm.api.AccessTokenStorage
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.components.auth.LoginScreen
import org.athletica.crm.components.auth.RegisterScreen
import org.athletica.crm.navigation.applyPlatformNavSetup
import org.athletica.crm.navigation.getInitialDeepLinkRoute
import org.athletica.crm.navigation.AppRoute

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
 * Принимает [api] — клиент API для проверки сессии и выполнения запросов.
 */
private enum class UnauthScreen { Login, Register }

@Composable
fun App(
    tokenStorage: AccessTokenStorage,
    api: ApiClient,
) {
    var authState by remember { mutableStateOf(AuthState.Checking) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var registerError by remember { mutableStateOf<String?>(null) }
    var unauthScreen by remember { mutableStateOf(UnauthScreen.Login) }
    var timezone by remember { mutableStateOf(platformCurrentTimezone()) }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    // Read initial URL before applyPlatformNavSetup resets it to "/"
    val initialRoute: AppRoute = remember { getInitialDeepLinkRoute() }

    LaunchedEffect(navController) { applyPlatformNavSetup(navController) }

    LaunchedEffect(Unit) {
        authState =
            api.me().fold(
                ifLeft = { error ->
                    if (error !is ApiClientError.Unauthenticated) {
                        logger.w { "Не удалось проверить сессию: $error" }
                    }
                    AuthState.Unauthenticated
                },
                ifRight = {
                    logger.i { "Сессия активна" }
                    AuthState.Authenticated
                },
            )
    }

    MaterialTheme {
        when (authState) {
            AuthState.Checking ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            AuthState.Authenticated ->
                MainScreen(
                    api = api,
                    navController = navController,
                    initialRoute = initialRoute,
                    onLogout = {
                        scope.launch {
                            api
                                .logout()
                                .onLeft { logger.e { "Ошибка при выходе: $it" } }
                            tokenStorage.clear()
                            authState = AuthState.Unauthenticated
                        }
                    },
                )

            AuthState.Unauthenticated ->
                when (unauthScreen) {
                    UnauthScreen.Login ->
                        LoginScreen(
                            errorMessage = loginError,
                            onErrorDismissed = { loginError = null },
                            onLogin = { login, password ->
                                scope.launch {
                                    loginError = null
                                    api
                                        .login(LoginRequest(username = login, password = password))
                                        .fold(
                                            ifLeft = {
                                                logger.e { "Ошибка входа: $login — $it" }
                                                loginError =
                                                    when (it) {
                                                        is ApiClientError.ValidationError -> it.message
                                                        is ApiClientError.Unauthenticated -> "Неверный логин или пароль"
                                                        is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                                                    }
                                            },
                                            ifRight = {
                                                tokenStorage.save(it.accessToken, it.refreshToken)
                                                logger.i { "Вход выполнен успешно: $login" }
                                                authState = AuthState.Authenticated
                                            },
                                        )
                                }
                            },
                            onNavigateToRegister = { unauthScreen = UnauthScreen.Register },
                        )

                    UnauthScreen.Register ->
                        RegisterScreen(
                            errorMessage = registerError,
                            onErrorDismissed = { registerError = null },
                            timezone = timezone,
                            onTimezoneChange = { timezone = it },
                            onRegister = { organizationName, name, email, password, selectedTimezone ->
                                scope.launch {
                                    registerError = null
                                    api
                                        .signUp(
                                            SignUpRequest(
                                                companyName = organizationName,
                                                userName = name,
                                                login = email,
                                                password = password,
                                                timezone = selectedTimezone,
                                            ),
                                        ).fold(
                                            ifLeft = {
                                                logger.e { "Ошибка регистрации: $email — $it" }
                                                registerError =
                                                    when (it) {
                                                        is ApiClientError.ValidationError -> it.message
                                                        is ApiClientError.Unauthenticated -> "Ошибка регистрации. Попробуйте ещё раз"
                                                        is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                                                    }
                                            },
                                            ifRight = {
                                                tokenStorage.save(it.accessToken, it.refreshToken)
                                                logger.i { "Регистрация выполнена успешно: $email" }
                                                authState = AuthState.Authenticated
                                            },
                                        )
                                }
                            },
                            onNavigateToLogin = { unauthScreen = UnauthScreen.Login },
                        )
                }
        }
    }
}
