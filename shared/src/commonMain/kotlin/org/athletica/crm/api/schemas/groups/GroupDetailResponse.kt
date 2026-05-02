package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId

/** Полные данные группы, возвращаемые после создания или запроса деталей. */
@Serializable
data class GroupDetailResponse(
    /** Уникальный идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
    /** Слоты расписания группы. */
    val schedule: List<ScheduleSlot>,
    /** Дисциплины, привязанные к группе. */
    val disciplines: List<GroupDiscipline> = emptyList(),
    /** Преподаватели группы, наследуемые новыми занятиями. */
    val employeeIds: List<EmployeeId> = emptyList(),
)
