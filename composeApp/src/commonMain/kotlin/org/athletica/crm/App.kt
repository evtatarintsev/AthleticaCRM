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
import org.athletica.crm.components.auth.LoginScreen
import org.athletica.crm.components.auth.LoginViewModel
import org.athletica.crm.components.auth.RegisterScreen
import org.athletica.crm.components.auth.RegisterViewModel
import org.athletica.crm.navigation.AppRoute
import org.athletica.crm.navigation.applyPlatformNavSetup
import org.athletica.crm.navigation.getInitialDeepLinkRoute

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
    var unauthScreen by remember { mutableStateOf(UnauthScreen.Login) }
    val scope = rememberCoroutineScope()
    val loginViewModel =
        remember {
            LoginViewModel(api, tokenStorage, scope) { authState = AuthState.Authenticated }
        }
    val registerViewModel =
        remember {
            RegisterViewModel(api, tokenStorage, scope) { authState = AuthState.Authenticated }
        }
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
                            state = loginViewModel.state,
                            onLogin = loginViewModel::onLogin,
                            onErrorDismissed = loginViewModel::onErrorDismissed,
                            onNavigateToRegister = { unauthScreen = UnauthScreen.Register },
                        )

                    UnauthScreen.Register ->
                        RegisterScreen(
                            state = registerViewModel.state,
                            timezone = registerViewModel.timezone,
                            onRegister = registerViewModel::onRegister,
                            onTimezoneChange = registerViewModel::onTimezoneChange,
                            onErrorDismissed = registerViewModel::onErrorDismissed,
                            onNavigateToLogin = { unauthScreen = UnauthScreen.Login },
                        )
                }
        }
    }
}
