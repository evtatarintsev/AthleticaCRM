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
import androidx.compose.ui.tooling.preview.Preview
import org.athletica.crm.api.client.ApiClient

enum class AuthState{
    Checking,
    Authenticated,
    Unauthenticated,
}
@Composable
fun App(api: ApiClient) {
    var authState by remember { mutableStateOf(AuthState.Checking) }

    LaunchedEffect(Unit) {
        // TODO: заменить на API-запрос к /auth/me — наличие токена не гарантирует его валидность.
        // Десктоп: отправить токен в Authorization: Bearer <token>.
        // Веб: отправить запрос без токена — браузер приложит httpOnly cookie сам.
        // Пока считаем авторизованным, если токен есть локально.
        authState = try {
            api.me()
            AuthState.Authenticated
        } catch (e: Exception) {
            println(e)
            AuthState.Unauthenticated
        }
    }

    MaterialTheme {
        when (authState) {
            AuthState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AuthState.Authenticated -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // TODO: заменить на MainScreen()
                Text("Главный экран")
            }

            AuthState.Unauthenticated -> LoginScreen(
                onLogin = { login, password ->
                    // TODO: HTTP POST /auth/login → сохранить токен → authState = true
                }
            )
        }
    }
}
