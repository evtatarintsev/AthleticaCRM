package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Запрос на обновление профиля авторизованного пользователя. */
@Serializable
data class UpdateMeRequest(
    /** Отображаемое имя сотрудника. */
    val name: String,
    /** Идентификатор загруженного аватара; null — оставить без изменений или убрать. */
    val avatarId: Uuid? = null,
)
