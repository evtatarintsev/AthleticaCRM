package org.athletica.crm.navigation

import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToNavigation
import kotlinx.browser.window

actual fun getInitialDeepLinkRoute(): AppRoute = AppNavUrlEncoder.decodeUrl(window.location.pathname)

@OptIn(ExperimentalBrowserHistoryApi::class)
actual suspend fun applyPlatformNavSetup(navController: NavHostController) {
    // Reset pathname to "/" so that appAddress inside bindToNavigation is always
    // "origin + /". Without this, navigating after refresh on /clients produces
    // "/clients" + "employees" = "/clientsemployees".
    window.history.replaceState(null, "", "/")
    window.bindToNavigation(navController) { entry ->
        AppNavUrlEncoder.entryToUrl(entry)
    }
}
