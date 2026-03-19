package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

/** Ответ с данными авторизованного пользователя. */
@Serializable
data class AuthMeResponse(
    /** Уникальный идентификатор пользователя. */
    val id: Int,
    /** Имя пользователя. */
    val username: String,
)
