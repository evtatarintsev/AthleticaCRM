package org.athletica.crm

interface AccessTokenStorage {
    fun get(): String?
    fun save(token: String)
    fun clear()
}

expect fun getAccessTokenStorage(): AccessTokenStorage
