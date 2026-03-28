package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

/** Ответ с данными авторизованного пользователя. */
@Serializable
data class AuthMeResponse(
    /** Уникальный идентификатор пользователя в строковом формате UUID. */
    val id: String,
    /** Имя пользователя. */
    val username: String,
)
