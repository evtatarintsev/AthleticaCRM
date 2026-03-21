package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

/** Запрос на вход в систему. */
@Serializable
data class LoginRequest(
    /** Имя пользователя. */
    val username: String,
    /** Пароль пользователя. */
    val password: String,
)


/** Ответ на успешный вход. Содержит JWT токены для авторизации запросов. */
@Serializable
data class LoginResponse(
    /** JWT access токен для авторизации запросов. */
    val accessToken: String,
    /** JWT refresh токен для получения нового access токена. */
    val refreshToken: String,
)
