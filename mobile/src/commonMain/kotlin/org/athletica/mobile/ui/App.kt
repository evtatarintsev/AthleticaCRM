package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.athletica.mobile.api.ClientApi

/** Ссылки на документы для раздела «Ещё»: условия использования [terms] и политика обработки ПД [privacy]. */
data class AppLinks(
    val terms: String,
    val privacy: String,
)

/**
 * Приложение клиента через [api]: экран входа без сеанса, иначе профили и разделы.
 * Сеанс, завершённый сервером, возвращает на вход. [links] — документы в «Ещё».
 */
@Composable
fun App(api: ClientApi, links: AppLinks) {
    var signedIn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(api) {
        signedIn = api.hasSession()
        api.signedOut.collect { signedIn = false }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.safeDrawingPadding()) {
                when (signedIn) {
                    null -> {}
                    false -> SignInScreen(api) { signedIn = true }
                    true -> WithViewModels { SignedInScreen(api, links) { signedIn = false } }
                }
            }
        }
    }
}

/**
 * Свои view-модели для [content]: новый вход начинается с пустыми загрузчиками, а не с данными прежнего сеанса.
 */
@Composable
private fun WithViewModels(content: @Composable () -> Unit) {
    val owner =
        remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
