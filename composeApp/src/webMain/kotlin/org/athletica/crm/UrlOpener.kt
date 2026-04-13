package org.athletica.crm

import kotlinx.browser.window

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}
