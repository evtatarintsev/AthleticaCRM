package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.UploadId

/** Полные данные клиента, возвращаемые после создания или запроса деталей. */
@Serializable
data class ClientDetailResponse(
    /** Уникальный идентификатор клиента. */
    val id: ClientId,
    /** Отображаемое имя клиента. */
    val name: String,
    /** Идентификатор загрузки аватарки клиента, либо null если аватарка не задана. */
    val avatarId: UploadId? = null,
    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate? = null,
    /** Пол клиента. */
    val gender: Gender,
    /** Группы в которых состоит клиент. */
    val groups: List<ClientGroup>,
    /** Баланс личного счёта клиента (отрицательный — задолженность). */
    val balance: Double,
    /** Документы, прикреплённые к клиенту. */
    val docs: List<ClientDoc> = emptyList(),
)
