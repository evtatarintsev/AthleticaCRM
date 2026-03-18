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

@Composable
@Preview
fun App() {
    val storage = remember { getAccessTokenStorage() }

    // null — проверяем, true — авторизован, false — нет
    var authState by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        // TODO: заменить на API-запрос к /auth/me — наличие токена не гарантирует его валидность.
        // Десктоп: отправить токен в Authorization: Bearer <token>.
        // Веб: отправить запрос без токена — браузер приложит httpOnly cookie сам.
        // Пока считаем авторизованным, если токен есть локально.
        authState = storage.get() != null
    }

    MaterialTheme {
        when (authState) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            true -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // TODO: заменить на MainScreen()
                Text("Главный экран")
            }

            false -> LoginScreen(
                onLogin = { login, password ->
                    // TODO: HTTP POST /auth/login → сохранить токен → authState = true
                }
            )
        }
    }
}
