package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Запрос на создание нового клиента.
 * [id] генерируется клиентом (offline-first) — позволяет избежать дополнительного round-trip за идентификатором.
 */
@Serializable
data class CreateClientRequest(
    /** Клиент-генерируемый идентификатор. Рекомендуется использовать UUIDv7. */
    val id: Uuid,
    /** Отображаемое имя клиента. */
    val name: String,
    /** Идентификатор загрузки аватарки клиента, полученный через POST /api/upload. */
    val avatarId: Uuid? = null,
    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate? = null,
)
