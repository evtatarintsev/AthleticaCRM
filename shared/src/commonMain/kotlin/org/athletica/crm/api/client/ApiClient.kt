package org.athletica.crm.api.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse

class ApiClient(private val http: HttpClient) {
    suspend fun login(request: LoginRequest): LoginResponse =
        http.post("/auth/login") {
            setBody(request)
        }.body()

    suspend fun me(): AuthMeResponse = http.get("/auth/me").body()
}
