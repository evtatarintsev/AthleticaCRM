package org.athletica.crm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.LoginResponse

/**
 * Создаёт [ApiClient] с движком CIO для JVM.
 * Настраивает JSON сериализацию, базовый URL, таймауты и Bearer аутентификацию с автообновлением токенов.
 *
 * @param tokenStorage хранилище JWT токенов для загрузки и сохранения
 * @return настроенный [ApiClient]
 */
fun apiClient(tokenStorage: FileAccessTokenStorage): ApiClient {
    val http =
        HttpClient(CIO).config {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest {
                url("http://127.0.0.1:8080/")
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 1000
                requestTimeoutMillis = 1000
            }
            install(Auth) {
                bearer {
                    cacheTokens = false
                    loadTokens {
                        tokenStorage.get()
                    }
                    refreshTokens {
                        val response = client.post("/api/auth/refresh-tokens").body<LoginResponse>()
                        tokenStorage.save(response.accessToken, response.refreshToken)
                        BearerTokens(response.accessToken, response.refreshToken)
                    }
                }
            }
        }
    return ApiClient(http)
}
