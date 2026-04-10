package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable
import org.athletica.crm.core.UserId
import kotlin.uuid.Uuid

/** Ответ с данными авторизованного пользователя. */
@Serializable
data class AuthMeResponse(
    /** Уникальный идентификатор пользователя в строковом формате UUID. */
    val id: UserId,
    /** Логин. */
    val username: String,
    /** Имя . */
    val name: String,
    /** Имя пользователя. */
    val avatarId: Uuid? = null,
)
