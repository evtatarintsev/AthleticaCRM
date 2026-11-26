package org.athletica.crm.usecases.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.domain.clientapp.InvitedClientAppAccess
import org.athletica.crm.domain.clientapp.auth.ClientAppAccounts
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction

/** Ответ аккаунта кабинета на приглашение организации: «Подключить» или «Это не я». */
class ClientAppConnections(
    private val db: Database,
    private val accounts: ClientAppAccounts,
    private val accesses: ClientAppAccesses,
    private val tasks: Tasks,
) {
    /**
     * Подключает аккаунт к организации [orgId]: все приглашённые доступы его номера в ней становятся
     * подключёнными. Нет приглашения — `CLIENT_APP_INVITATION_NOT_FOUND`.
     */
    context(ctx: ClientAccountContext, raise: Raise<AppError>)
    suspend fun connect(orgId: OrgId) {
        db.transaction { invited(orgId).forEach { it.connect() } }
    }

    /**
     * «Это не я»: приглашённые доступы номера аккаунта в организации [orgId] получают статус «это не я»,
     * и по каждому клиенту организации создаётся задача «Проверьте номер телефона» — не больше одной
     * открытой на доступ. Нет приглашения — `CLIENT_APP_INVITATION_NOT_FOUND`.
     */
    context(ctx: ClientAccountContext, raise: Raise<AppError>)
    suspend fun notMe(orgId: OrgId) {
        db.transaction {
            val invited = invited(orgId)
            context(SystemContexts.forOrg(orgId)) {
                invited.forEach { access ->
                    val rejected = access.rejectAsNotMe()
                    val taskId = TaskId.new()
                    if (accesses.reserveNotMeTask(rejected, taskId)) {
                        tasks.new(
                            id = taskId,
                            title = NonBlankName.from(Messages.ClientAppNotMeTaskTitle.localize()),
                            description = Messages.ClientAppNotMeTaskDescription.localize(rejected.phone.value),
                            clientId = rejected.clientId,
                            dueDate = null,
                            dueDateEnd = null,
                        )
                    }
                }
            }
        }
    }

    /** Приглашённые доступы номера аккаунта в организации [orgId]; пусто — `CLIENT_APP_INVITATION_NOT_FOUND`. */
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun invited(orgId: OrgId): List<InvitedClientAppAccess> {
        val account = accounts.byId(ctx.accountId) ?: raise(SessionRevoked)
        val invited = accesses.byPhone(account.phone).filter { it.orgId == orgId }.filterIsInstance<InvitedClientAppAccess>()
        ensure(invited.isNotEmpty()) {
            CommonDomainError("CLIENT_APP_INVITATION_NOT_FOUND", Messages.ClientAppInvitationNotFound.localize(ctx.lang))
        }
        return invited
    }
}
