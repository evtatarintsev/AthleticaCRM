package org.athletica.crm.domain.events.handlers

import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.systemContext
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.events.DomainEventHandler
import org.athletica.crm.domain.events.GroupCreated
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.generateSessions
import org.athletica.crm.usecases.sessions.generationHorizon
import java.time.LocalDate

/**
 * Обработчик события [GroupCreated] — генерирует занятия для новой группы.
 *
 * При создании группы автоматически создаёт занятия по расписанию на горизонт 8 недель.
 * Запускается асинхронно после завершения транзакции создания группы.
 */
class GroupCreatedHandler(
    private val database: Database,
    private val groups: Groups,
    private val sessions: Sessions,
    private val employees: Employees,
) : DomainEventHandler<GroupCreated> {
    override suspend fun handle(orgId: OrgId, event: GroupCreated) {
        val ctx = systemContext(orgId)
        val today = LocalDate.now().toKotlinLocalDate()
        database.transaction {
            arrow.core.raise.either {
                context(ctx, this@transaction, this) {
                    generateSessions(groups, sessions, employees, event.groupId, today, generationHorizon())
                }
            }
        }
    }
}
