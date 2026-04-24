package org.athletica.crm.navigation

import androidx.navigation.NavHostController

actual suspend fun applyPlatformNavSetup(navController: NavHostController) {
    // no-op on JVM desktop — no browser history API
}
