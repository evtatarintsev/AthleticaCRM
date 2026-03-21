package org.athletica.crm

import io.ktor.client.plugins.auth.providers.BearerTokens
import org.athletica.crm.api.AccessTokenStorage
import java.io.File


/**
 * Хранилище JWT токенов на основе файловой системы для десктоп-клиента.
 * Сохраняет access и refresh токены в текстовый файл, по одному на строку.
 *
 * @param file файл для хранения токенов
 */
class FileAccessTokenStorage(private val file: File) : AccessTokenStorage {
    /**
     * Возвращает сохранённые токены или null если файл отсутствует.
     *
     * @return токены или null
     */
    fun get(): BearerTokens? {
        if (file.exists()) {
            val lines = file.readLines()
            return BearerTokens(lines[0], lines[1])
        }
        return null
    }

    /**
     * Сохраняет токены в файл.
     */
    override fun save(accessToken: String, refreshToken: String) {
        file.writeText(accessToken + "\n" + refreshToken)
    }

    /** Удаляет файл с токенами. */
    override fun clear() {
        file.delete()
    }
}
