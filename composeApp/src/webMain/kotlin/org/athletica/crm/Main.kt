package org.athletica.crm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.athletica.crm.api.DummyAccessTokenStorage

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(DummyAccessTokenStorage(), apiClient())
    }
}
