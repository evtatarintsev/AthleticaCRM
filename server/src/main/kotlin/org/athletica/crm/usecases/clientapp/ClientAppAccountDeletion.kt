package org.athletica.crm.usecases.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.domain.clientapp.ConnectedClientAppAccess
import org.athletica.crm.domain.clientapp.InvitedClientAppAccess
import org.athletica.crm.domain.clientapp.auth.ClientAppAccounts
import org.athletica.crm.domain.clientapp.auth.ClientAppSessions
import org.athletica.crm.storage.Database
import kotlin.time.Instant

/**
 * Удаление аккаунта кабинета самим человеком (отзыв согласия): данные учёта остаются у организаций,
 * у аккаунта не остаётся ни сеансов, ни доступов.
 */
class ClientAppAccountDeletion(
    private val db: Database,
    private val accounts: ClientAppAccounts,
    private val sessions: ClientAppSessions,
    private val accesses: ClientAppAccesses,
) {
    /**
     * Удаляет аккаунт запроса в момент [now] одной транзакцией: завершает все его сеансы, переводит
     * приглашённые и подключённые доступы его номера во всех организациях в «отключён» и обезличивает номер.
     * Доступы «это не я» и уже отключённые не меняются: данных они и так не дают.
     */
    context(ctx: ClientAccountContext, raise: Raise<AppError>)
    suspend fun delete(now: Instant) {
        db.transaction {
            val account = accounts.byId(ctx.accountId) ?: raise(SessionRevoked)
            accesses.allByPhone(account.phone).forEach {
                when (it) {
                    is InvitedClientAppAccess -> it.disable()
                    is ConnectedClientAppAccess -> it.disable()
                    else -> Unit
                }
            }
            sessions.deleteAll(account.id)
            accounts.delete(account.id, now)
        }
    }
}
