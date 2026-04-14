package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.UploadId

/**
 * Запрос на редактирование существующего клиента.
 */
@Serializable
data class EditClientRequest(
    /** Идентификатор редактируемого клиента. */
    val id: ClientId,
    /** Отображаемое имя клиента. */
    val name: String,
    /** Идентификатор загрузки аватарки клиента, полученный через POST /api/upload. */
    val avatarId: UploadId? = null,
    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate? = null,
    /** Пол клиента. */
    val gender: Gender,
)
