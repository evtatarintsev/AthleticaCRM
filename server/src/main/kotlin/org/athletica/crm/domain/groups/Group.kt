package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

interface Group {
    val id: GroupId

    /** Идентификатор филиала, к которому относится группа. */
    val branchId: BranchId

    /** Название группы. */
    val name: String

    /** Слоты расписания группы. */
    val schedule: List<ScheduleSlot>

    /** Дисциплины, привязанные к группе. */
    val disciplines: List<DisciplineId>

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNewSchedule(schedule: List<ScheduleSlot>): Group

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNewDisciplines(disciplines: List<DisciplineId>): Group

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNewName(name: String): Group

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNew(name: String, disciplines: List<DisciplineId>, schedule: List<ScheduleSlot>): Group =
        withNewName(name)
            .withNewDisciplines(disciplines)
            .withNewSchedule(schedule)
}

@Serializable
data class ScheduleSlot(
    val dayOfWeek: DayOfWeek,
    val startAt: LocalTime,
    val endAt: LocalTime,
    val hallId: HallId,
) {
    context(ctx: RequestContext, raise: Raise<DomainError>)
    fun validate() {
        if (endAt <= startAt) {
            raise(
                CommonDomainError(
                    "INVALID_SCHEDULE_TIME",
                    Messages.ScheduleEndBeforeStart.localize(ctx.lang, startAt, endAt),
                ),
            )
        }
    }
}
