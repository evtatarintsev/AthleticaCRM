package org.athletica.crm.api.schemas.schedule

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.HallId

/**
 * Запрос расписания текущего филиала за период. Пустой список фильтра означает,
 * что по этому измерению занятия не ограничиваются.
 */
@Serializable
data class ScheduleListRequest(
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
    /** Дисциплины группы занятия; совпадение с любой из них. */
    val disciplineIds: List<DisciplineId> = emptyList(),
    /** Залы занятия; совпадение с любым из них. */
    val hallIds: List<HallId> = emptyList(),
    /** Тренеры занятия; совпадение с любым из них. */
    val employeeIds: List<EmployeeId> = emptyList(),
)
