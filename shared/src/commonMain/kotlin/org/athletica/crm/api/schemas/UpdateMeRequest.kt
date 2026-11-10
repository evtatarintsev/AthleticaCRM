package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.UploadId

/** Запрос на обновление профиля авторизованного пользователя. */
@Serializable
data class UpdateMeRequest(
    /** Отображаемое имя сотрудника. */
    val name: NonBlankName,
    /** Идентификатор загруженного аватара; null — оставить без изменений или убрать. */
    val avatarId: UploadId? = null,
)
