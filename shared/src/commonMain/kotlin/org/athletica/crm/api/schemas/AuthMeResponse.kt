package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Ответ с данными авторизованного пользователя. */
@Serializable
data class AuthMeResponse(
    /** Уникальный идентификатор пользователя в строковом формате UUID. */
    val id: String,
    /** Логин. */
    val username: String,
    /** Имя . */
    val name: String,
    /** Имя пользователя. */
    val avatarId: Uuid? = null,
)
