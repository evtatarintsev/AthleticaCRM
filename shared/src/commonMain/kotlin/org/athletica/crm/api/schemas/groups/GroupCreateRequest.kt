package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на создание новой группы.
 * [id] генерируется клиентом (offline-first) — позволяет избежать дополнительного round-trip за идентификатором.
 */
@Serializable
data class GroupCreateRequest(
    /** Клиент-генерируемый идентификатор. Рекомендуется использовать UUIDv7. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
    /** Слоты расписания группы. */
    val schedule: List<ScheduleSlot> = emptyList(),
    /** Идентификаторы дисциплин, привязываемых к группе. */
    val disciplineIds: List<DisciplineId> = emptyList(),
    /** Идентификаторы преподавателей группы, наследуемые новыми занятиями. */
    val employeeIds: List<EmployeeId> = emptyList(),
)
