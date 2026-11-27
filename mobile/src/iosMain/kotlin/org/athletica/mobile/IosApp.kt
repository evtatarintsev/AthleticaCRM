package org.athletica.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.engine.darwin.Darwin
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.ui.App
import org.athletica.mobile.ui.AppLinks
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController

/** Клиент API процесса: один HTTP-клиент на всё время жизни приложения. */
private var api: ClientApi? = null

/**
 * Корневой контроллер iOS-приложения для SwiftUI-оболочки: API по адресу [apiUrl], документы —
 * условия [termsUrl] и политика ПД [privacyUrl]. Вызывается с главного потока.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(apiUrl: String, termsUrl: String, privacyUrl: String): UIViewController {
    val client =
        api ?: ClientApi(Darwin.create(), apiUrl, IosSessionStore(), "Athletica (iOS ${UIDevice.currentDevice.systemVersion})").also { api = it }
    return ComposeUIViewController { IosApp(client, AppLinks(termsUrl, privacyUrl)) }
}

/** Приложение на iOS: ссылки открываются в `SFSafariViewController` поверх приложения, а не в Safari. */
@Composable
private fun IosApp(api: ClientApi, links: AppLinks) {
    val controller = LocalUIViewController.current
    val uriHandler = remember(controller) { SafariUriHandler(controller) }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) { App(api, links) }
}

/** Открывает ссылки в `SFSafariViewController`, показанном поверх [controller]. */
private class SafariUriHandler(private val controller: UIViewController) : UriHandler {
    override fun openUri(uri: String) {
        val url = NSURL.URLWithString(uri) ?: return
        controller.presentViewController(SFSafariViewController(uRL = url), animated = true, completion = null)
    }
}
