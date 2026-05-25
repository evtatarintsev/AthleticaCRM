package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId

/**
 * Параметры запроса списка групп.
 * [name] — поиск по подстроке в названии группы; null/пустое — без фильтра.
 * [disciplineIds] — оставить только группы, у которых есть хотя бы одна из указанных дисциплин;
 * пустой список — без фильтра.
 * [employeeIds] — оставить только группы, у которых есть хотя бы один из указанных тренеров;
 * пустой список — без фильтра.
 */
@Serializable
data class GroupListRequest(
    val name: String? = null,
    val disciplineIds: List<DisciplineId> = emptyList(),
    val employeeIds: List<EmployeeId> = emptyList(),
)
