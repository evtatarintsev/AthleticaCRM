package org.athletica.crm.domain

import kotlinx.collections.immutable.PersistentList
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.groups.GroupDiscipline
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.core.GroupId

@Serializable
data class Group(
    /** Уникальный идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
    /** Слоты расписания группы. */
    val schedule: PersistentList<ScheduleSlot>,
    /** Дисциплины, привязанные к группе. */
    val disciplines: PersistentList<GroupDiscipline>,
)
