package org.athletica.crm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.appJson
import org.athletica.crm.api.schemas.auth.LoginResponse

/**
 * Создаёт [ApiClient] для веб-браузера.
 * Использует движок Js (Fetch API) — единственный поддерживаемый в браузере.
 * Токены не хранятся явно: браузер автоматически отправляет HttpOnly cookie.
 * Автообновление токенов выполняется через POST /api/auth/refresh-token.
 */
fun apiClient(): ApiClient {
    val http =
        HttpClient(Js) {
            install(ContentNegotiation) {
                json(appJson)
            }
            defaultRequest {
                url("/")
            }
            install(Auth) {
                bearer {
                    refreshTokens {
                        runCatching {
                            val response = client.post("/api/auth/refresh-token")
                            if (response.status.isSuccess()) {
                                response.body<LoginResponse>().let { BearerTokens(it.accessToken, it.refreshToken) }
                            } else {
                                null
                            }
                        }.getOrNull()
                    }
                }
            }
        }
    return ApiClient(http)
}
