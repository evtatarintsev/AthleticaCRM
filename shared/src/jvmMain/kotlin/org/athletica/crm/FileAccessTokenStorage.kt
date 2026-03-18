package org.athletica.crm

import java.io.File

class FileAccessTokenStorage : AccessTokenStorage {

    private val file = File(System.getProperty("user.home"), ".athletica_crm_token")

    override fun get(): String? =
        if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null

    override fun save(token: String) {
        file.writeText(token)
    }

    override fun clear() {
        file.delete()
    }
}

actual fun getAccessTokenStorage(): AccessTokenStorage = FileAccessTokenStorage()
