package org.athletica.crm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform