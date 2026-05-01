package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/**
 * Генерирует занятия для группы на основе её расписания за период [from]..[to].
 * Идемпотентна: повторный вызов не создаёт дубли (ON CONFLICT DO NOTHING).
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun generateSessions(
    groups: Groups,
    sessions: Sessions,
    groupId: GroupId,
    from: LocalDate,
    to: LocalDate,
) {
    val group = groups.byId(groupId)
    group.schedule.forEach { slot ->
        var current = from
        while (current <= to) {
            if (current.dayOfWeek.name == slot.dayOfWeek.name) {
                sessions.new(
                    id = SessionId.new(),
                    groupId = groupId,
                    date = current,
                    startTime = slot.startAt,
                    endTime = slot.endAt,
                    hallId = slot.hallId,
                    notes = null,
                    originDayOfWeek = slot.dayOfWeek.name,
                    originStartTime = slot.startAt,
                    originDate = current,
                )
            }
            current = current.plus(1, DateTimeUnit.DAY)
        }
    }
}
