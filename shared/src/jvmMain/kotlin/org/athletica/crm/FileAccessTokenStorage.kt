package org.athletica.crm

import io.ktor.client.plugins.auth.providers.BearerTokens
import java.io.File

/**
 * Хранилище JWT токенов на основе файловой системы для десктоп-клиента.
 * Сохраняет access и refresh токены в текстовый файл, по одному на строку.
 *
 * @param file файл для хранения токенов
 */
class FileAccessTokenStorage(private val file: File) {
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
     *
     * @param tokens JWT токены для сохранения
     */
    fun save(tokens: BearerTokens) {
        file.writeText(tokens.accessToken + "\n" + tokens.refreshToken)
    }

    /** Удаляет файл с токенами. */
    fun clear() {
        file.delete()
    }
}
