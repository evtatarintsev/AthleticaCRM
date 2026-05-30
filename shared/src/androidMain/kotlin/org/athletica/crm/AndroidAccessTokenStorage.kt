package org.athletica.crm

import android.content.Context
import io.ktor.client.plugins.auth.providers.BearerTokens
import org.athletica.crm.api.AccessTokenStorage

private const val PREFS_NAME = "athletica_tokens"
private const val KEY_ACCESS = "access_token"
private const val KEY_REFRESH = "refresh_token"

/**
 * Хранилище JWT токенов на основе SharedPreferences для Android-клиента.
 * Сохраняет access и refresh токены в приватное хранилище приложения.
 */
class AndroidAccessTokenStorage(context: Context) : AccessTokenStorage {
    /** Приватное хранилище ключ-значение приложения. */
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Возвращает сохранённые токены или `null` если токены ещё не сохранялись. */
    fun get(): BearerTokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return BearerTokens(access, refresh)
    }

    /** Сохраняет [accessToken] и [refreshToken] в SharedPreferences. */
    override fun save(
        accessToken: String,
        refreshToken: String,
    ) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    /** Удаляет токены из SharedPreferences. */
    override fun clear() {
        prefs.edit().clear().apply()
    }
}
