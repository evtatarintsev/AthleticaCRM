package org.athletica.crm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DataConversion.install
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

fun apiClient(): ApiClient {
    val http =
        HttpClient(CIO).config {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest {
                url("https://ktor.io/docs/")
            }
            install(Auth) {
                bearer {
                    refreshTokens {
                        val response = client.post("/api/auth/refresh-tokens").body<LoginResponse>()
                        BearerTokens(response.accessToken, response.refreshToken)
                    }
                }
            }
        }
    return ApiClient(http)
}
