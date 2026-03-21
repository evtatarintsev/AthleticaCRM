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
