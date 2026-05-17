package org.athletica.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_retry
import org.athletica.crm.generated.resources.error_server_unavailable_title
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.navigation.AppRoute
import org.athletica.crm.navigation.applyPlatformNavSetup
import org.athletica.crm.navigation.getInitialDeepLinkRoute
import org.jetbrains.compose.resources.stringResource

private val logger = Logger.withTag("App")

/** Состояние авторизации пользователя. */
enum class AuthState {
    /** Проверка авторизации в процессе. */
    Checking,

    /** Пользователь авторизован. */
    Authenticated,

    /** Пользователь не авторизован. */
    Unauthenticated,

    /** Сервер недоступен или вернул ошибку — нет смысла перенаправлять на вход. */
    ServerError,
}

/**
 * Экран ошибки подключения к серверу.
 * Отображается вместо экрана входа при 5xx-ответах и сетевых сбоях.
 * [onRetry] вызывается при нажатии на кнопку «Повторить».
 */
@Composable
private fun ServerErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.error_server_unavailable_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.error_service_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(stringResource(Res.string.action_retry))
        }
    }
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
    var retryCount by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(retryCount) {
        authState = AuthState.Checking
        authState =
            api.profile.me().fold(
                ifLeft = { error ->
                    when (error) {
                        is ApiClientError.Unauthenticated -> AuthState.Unauthenticated
                        else -> {
                            logger.w { "Не удалось проверить сессию: $error" }
                            AuthState.ServerError
                        }
                    }
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
                            api.auth
                                .logout()
                                .onLeft { logger.e { "Ошибка при выходе: $it" } }
                            tokenStorage.clear()
                            authState = AuthState.Unauthenticated
                        }
                    },
                )

            AuthState.ServerError ->
                ServerErrorScreen(onRetry = { retryCount++ })

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
                            currency = registerViewModel.currency,
                            onRegister = registerViewModel::onRegister,
                            onTimezoneChange = registerViewModel::onTimezoneChange,
                            onCurrencyChange = registerViewModel::onCurrencyChange,
                            onErrorDismissed = registerViewModel::onErrorDismissed,
                            onNavigateToLogin = { unauthScreen = UnauthScreen.Login },
                        )
                }
        }
    }
}
