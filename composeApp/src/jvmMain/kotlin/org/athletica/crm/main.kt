package org.athletica.crm

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File

fun main() = application {
    val tokenStorage = FileAccessTokenStorage(
        File(System.getProperty("user.home"), ".athletica_crm_token")
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "AthleticaCRM",
    ) {
        App(apiClient(tokenStorage))
    }
}
