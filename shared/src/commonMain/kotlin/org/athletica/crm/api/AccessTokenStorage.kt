package org.athletica.crm.api


interface AccessTokenStorage {
    fun save(accessToken: String, refreshToken: String)
    fun clear()
}

class DummyAccessTokenStorage : AccessTokenStorage {
    override fun save(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}
