package org.athletica.crm.api.schemas.groups

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/**
 * Ответ на запрос списка групп.
 * [groups] — группы, удовлетворяющие фильтрам запроса.
 * [total] — общее количество групп в организации без учёта фильтров (для подзаголовков и счётчиков).
 */
@Serializable
data class GroupListResponse(
    val groups: List<GroupListItem>,
    val total: UInt,
)

@Serializable
data class GroupListItem(
    /** Уникальный идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
    /** Расписание группы, действующее сегодня. */
    val schedule: List<ScheduleSlot>,
    /** Дата ближайшего запланированного изменения расписания; `null` — изменений не запланировано. */
    val scheduleChangeAt: LocalDate? = null,
    /** Преподаватели группы. */
    val employees: List<GroupEmployee>,
)
