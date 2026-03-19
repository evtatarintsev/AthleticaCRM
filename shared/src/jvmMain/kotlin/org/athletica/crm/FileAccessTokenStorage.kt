package org.athletica.crm

import io.ktor.client.plugins.auth.providers.BearerTokens
import java.io.File

class FileAccessTokenStorage(private val file: File) {
    fun get(): BearerTokens? {
        if (file.exists()) {
            val lines = file.readLines()
            return BearerTokens(lines[0], lines[1])
        }
        return null
    }

    fun save(tokens: BearerTokens) {
        file.writeText(tokens.accessToken + "\n" + tokens.refreshToken)
    }

    fun clear() {
        file.delete()
    }
}
