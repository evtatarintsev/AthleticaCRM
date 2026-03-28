package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Полные данные клиента, возвращаемые после создания или запроса деталей. */
@Serializable
data class ClientDetailResponse(
    /** Уникальный идентификатор клиента. */
    val id: Uuid,
    /** Отображаемое имя клиента. */
    val name: String,
)
