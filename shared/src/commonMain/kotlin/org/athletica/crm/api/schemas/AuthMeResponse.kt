package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Ответ с данными авторизованного пользователя. */
@Serializable
data class AuthMeResponse(
    /** Уникальный идентификатор пользователя. */
    val id: Uuid,
    /** Имя пользователя. */
    val username: String,
)
