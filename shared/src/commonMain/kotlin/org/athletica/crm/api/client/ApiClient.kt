package org.athletica.crm.api.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.athletica.crm.SERVER_PORT
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse

expect fun createHttpClient(): HttpClient

class ApiClient {

    private val baseUrl = "http://localhost:$SERVER_PORT"

    private val http = createHttpClient().config {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun login(request: LoginRequest): LoginResponse =
        http.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun me(token: String): AuthMeResponse =
        http.get("$baseUrl/auth/me") {
            bearerAuth(token)
        }.body()
}
