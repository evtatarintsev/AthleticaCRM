package org.athletica.crm.navigation

import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToNavigation
import kotlinx.browser.window

@OptIn(ExperimentalBrowserHistoryApi::class)
actual suspend fun applyPlatformNavSetup(navController: NavHostController) {
    window.bindToNavigation(navController) { entry ->
        AppNavUrlEncoder.entryToUrl(entry)
    }
}
