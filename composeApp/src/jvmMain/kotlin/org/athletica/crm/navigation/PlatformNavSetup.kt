package org.athletica.crm.navigation

import androidx.navigation.NavHostController

actual fun getInitialDeepLinkRoute(): AppRoute = AppRoute.Home

actual suspend fun applyPlatformNavSetup(navController: NavHostController) {
    // no-op on JVM desktop — no browser history API
}
