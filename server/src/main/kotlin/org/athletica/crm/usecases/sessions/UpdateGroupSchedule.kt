package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.ScheduleSlot
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

private const val GENERATE_WEEKS_AHEAD = 8

/**
 * Обновляет расписание группы и синхронизирует занятия:
 * 1. Отменяет будущие `scheduled`-занятия из удалённых слотов (без посещений).
 * 2. Сохраняет новое расписание.
 * 3. Генерирует занятия из новых слотов на [GENERATE_WEEKS_AHEAD] недель вперёд.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun updateGroupSchedule(
    groups: Groups,
    sessions: Sessions,
    groupId: GroupId,
    newSchedule: List<ScheduleSlot>,
) {
    val group = groups.byId(groupId)
    val today = java.time.LocalDate.now().toKotlinLocalDate()

    val removedSlots =
        group.schedule.filter { old ->
            newSchedule.none { new -> new.dayOfWeek == old.dayOfWeek && new.startAt == old.startAt }
        }

    removedSlots.forEach { slot ->
        val toCancel =
            sessions.futureScheduledBySlot(
                groupId = groupId,
                dayOfWeek = slot.dayOfWeek.name,
                startTime = slot.startAt,
                from = today,
            )
        toCancel.forEach { session -> session.cancel() }
    }

    group.withNewSchedule(newSchedule).save()

    val generateTo = today.plus(GENERATE_WEEKS_AHEAD * 7, DateTimeUnit.DAY)
    generateSessions(groups, sessions, groupId, today, generateTo)
}

/** Граница горизонта генерации (сегодня + [GENERATE_WEEKS_AHEAD] недель). */
fun generationHorizon(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate().plus(GENERATE_WEEKS_AHEAD * 7, DateTimeUnit.DAY)
