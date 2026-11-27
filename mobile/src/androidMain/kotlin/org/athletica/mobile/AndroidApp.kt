package org.athletica.mobile

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.ktor.client.engine.okhttp.OkHttp
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.ui.App
import org.athletica.mobile.ui.AppLinks

/** Клиент API процесса: один HTTP-клиент на всё время жизни приложения, а не на каждую Activity. */
@Volatile
private var api: ClientApi? = null

/** Клиент API по адресу [apiUrl] с сеансом в [AndroidSessionStore]. */
private fun clientApi(context: Context, apiUrl: String): ClientApi =
    api ?: synchronized(ClientApi::class) {
        api ?: ClientApi(OkHttp.create(), apiUrl, AndroidSessionStore.of(context), "Athletica (Android ${Build.VERSION.RELEASE})").also { api = it }
    }

/** Приложение на Android: API по адресу [apiUrl], документы [links]. */
@Composable
fun AndroidApp(apiUrl: String, links: AppLinks) {
    App(clientApi(LocalContext.current, apiUrl), links)
}
