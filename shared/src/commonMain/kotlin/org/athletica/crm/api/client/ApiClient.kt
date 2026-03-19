package org.athletica.crm.api.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse

/**
 * Клиент для взаимодействия с API сервера.
 *
 * @param http настроенный Ktor HTTP клиент с аутентификацией и сериализацией
 */
class ApiClient(private val http: HttpClient) {
    /**
     * Выполняет вход пользователя и возвращает JWT токены.
     *
     * @param request данные для входа (логин и пароль)
     * @return access и refresh токены
     */
    suspend fun login(request: LoginRequest): LoginResponse =
        http.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * Возвращает данные текущего авторизованного пользователя.
     *
     * @return данные пользователя
     */
    suspend fun me(): AuthMeResponse = http.get("/auth/me").body()
}
