package org.athletica.crm.domain.events.handlers

import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.systemContext
import org.athletica.crm.domain.events.DomainEventHandler
import org.athletica.crm.domain.events.GroupScheduleChanged
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.generateSessions
import org.athletica.crm.usecases.sessions.generationHorizon
import java.time.LocalDate

/**
 * Обработчик события [GroupScheduleChanged] — перегенерирует занятия при изменении расписания.
 *
 * При изменении расписания группы автоматически пересоздаёт занятия по новым слотам на горизонт 8 недель.
 * Запускается асинхронно после завершения транзакции изменения расписания.
 *
 * @param database база данных для транзакций
 * @param groups репозиторий групп
 * @param sessions репозиторий занятий
 */
class GroupScheduleChangedHandler(
    private val database: Database,
    private val groups: Groups,
    private val sessions: Sessions,
) : DomainEventHandler<GroupScheduleChanged> {
    override suspend fun handle(orgId: OrgId, event: GroupScheduleChanged) {
        val ctx = systemContext(orgId)
        val today = LocalDate.now().toKotlinLocalDate()
        database.transaction {
            arrow.core.raise.either {
                context(ctx, this@transaction, this) {
                    generateSessions(groups, sessions, event.groupId, today, generationHorizon())
                }
            }
        }
    }
}
