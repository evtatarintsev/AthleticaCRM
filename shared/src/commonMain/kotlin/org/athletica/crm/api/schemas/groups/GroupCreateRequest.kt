package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Запрос на создание новой группы.
 * [id] генерируется клиентом (offline-first) — позволяет избежать дополнительного round-trip за идентификатором.
 */
@Serializable
data class GroupCreateRequest(
    /** Клиент-генерируемый идентификатор. Рекомендуется использовать UUIDv7. */
    val id: Uuid,
    /** Название группы. */
    val name: String,
    /** Слоты расписания группы. */
    val schedule: List<ScheduleSlot> = emptyList(),
)
