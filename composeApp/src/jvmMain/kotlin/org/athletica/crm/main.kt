package org.athletica.crm

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AthleticaCRM",
    ) {
        App()
    }
}