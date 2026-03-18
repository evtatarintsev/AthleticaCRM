package org.athletica.crm.api.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse


class ApiClient(private val http: HttpClient) {
    suspend fun login(request: LoginRequest): LoginResponse =
        http.post("/auth/login") {
            setBody(request)
        }.body()

    suspend fun me(): AuthMeResponse =
        http.get("/auth/me").body()
}
