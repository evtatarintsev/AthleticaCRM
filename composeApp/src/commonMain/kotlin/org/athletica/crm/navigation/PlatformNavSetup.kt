package org.athletica.crm.navigation

import androidx.navigation.NavHostController

expect fun getInitialDeepLinkRoute(): AppRoute

expect suspend fun applyPlatformNavSetup(navController: NavHostController)
