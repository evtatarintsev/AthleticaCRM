package org.athletica.crm.api.schemas.groups

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
    /** Расписание группы. */
    val schedule: List<ScheduleSlot>,
    /** Преподаватели группы. */
    val employees: List<GroupEmployee>,
)
