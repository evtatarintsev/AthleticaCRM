package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на редактирование существующей группы.
 */
@Serializable
data class EditGroupRequest(
    /** Идентификатор редактируемой группы. */
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
