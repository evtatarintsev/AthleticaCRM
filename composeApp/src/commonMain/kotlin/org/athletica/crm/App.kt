package org.athletica.crm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.athletica.crm.api.client.ApiClient

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
@Composable
fun App(api: ApiClient) {
    var authState by remember { mutableStateOf(AuthState.Checking) }

    LaunchedEffect(Unit) {
        // TODO: на вебе браузер приложит httpOnly cookie автоматически
        authState =
            try {
                api.me()
                AuthState.Authenticated
            } catch (e: Exception) {
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // TODO: заменить на MainScreen()
                    Text("Главный экран")
                }

            AuthState.Unauthenticated ->
                LoginScreen(
                    onLogin = { _, _ ->
                        // TODO: HTTP POST /auth/login → сохранить токен → authState = Authenticated
                    },
                )
        }
    }
}
