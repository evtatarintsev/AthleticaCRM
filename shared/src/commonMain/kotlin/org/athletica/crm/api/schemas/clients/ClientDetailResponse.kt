package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.Gender
import kotlin.uuid.Uuid

/** Полные данные клиента, возвращаемые после создания или запроса деталей. */
@Serializable
data class ClientDetailResponse(
    /** Уникальный идентификатор клиента. */
    val id: Uuid,
    /** Отображаемое имя клиента. */
    val name: String,
    /** Идентификатор загрузки аватарки клиента, либо null если аватарка не задана. */
    val avatarId: Uuid? = null,
    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate? = null,
    /** Пол клиента. */
    val gender: Gender,
    /** Группы в которых состоит клиент. */
    val groups: List<ClientGroup>,
    /** Баланс личного счёта клиента (отрицательный — задолженность). */
    val balance: Double,
)
