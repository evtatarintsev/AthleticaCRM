package org.athletica.crm.api


interface AccessTokenStorage {
    fun save(accessToken: String, refreshToken: String)
}

class DummyAccessTokenStorage : AccessTokenStorage {
    override fun save(accessToken: String, refreshToken: String){}
}
