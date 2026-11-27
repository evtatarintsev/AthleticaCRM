package org.athletica.crm.core

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.toCurrency
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/**
 * Контекст запроса кабинета по одному клиенту организации. Строится только через
 * [ClientProfileContexts.profile] — после проверки подключённого доступа. Как и [ClientAccountContext],
 * это **не** вариант [RequestContext].
 */
data class ClientProfileContext(
    /** Аккаунт кабинета. */
    val accountId: ClientAppAccountId,
    /** Сеанс аккаунта. */
    val sessionId: ClientAppSessionId,
    /** Организация клиента. */
    val orgId: OrgId,
    /** Клиент, данные которого смотрит аккаунт. */
    val clientId: ClientId,
    /** Валюта организации. */
    val currency: Currency,
    /** Язык запроса. */
    val lang: Lang,
)

/** Фабрика [ClientProfileContext] и общий предикат подключённого доступа. */
object ClientProfileContexts {
    /**
     * Подзапрос идентификаторов доступа, который сейчас даёт данные клиента `:clientId` организации `:orgId`
     * аккаунту `:accountId`: статус `CONNECTED`, номер аккаунта, кабинет организации включён, клиент не в архиве.
     * Используют все проекции кабинета и команды от имени клиента: `EXISTS (…)` или `id IN (…)`.
     */
    const val CONNECTED_ACCESS_IDS = """
        SELECT cpa.id FROM client_app_access cpa
        JOIN client_app_accounts cpacc ON cpacc.phone_digits = cpa.phone_digits AND cpacc.deleted_at IS NULL
        JOIN organizations cpo ON cpo.id = cpa.org_id AND cpo.client_app_enabled
        JOIN clients cpc ON cpc.id = cpa.client_id AND cpc.org_id = cpa.org_id AND cpc.state = 'ACTIVE'
        WHERE cpacc.id = :accountId AND cpa.org_id = :orgId AND cpa.client_id = :clientId AND cpa.status = 'CONNECTED'
    """

    /**
     * Клиент `:clientId` числится на занятии под псевдонимом `s`: есть строка журнала (в том числе разовое
     * участие) или запись в группу занятия активна на его дату. Общий для расписания и «Не придём».
     */
    const val IN_SESSION = """
        (EXISTS (SELECT 1 FROM session_attendance isa WHERE isa.session_id = s.id AND isa.client_id = :clientId)
         OR EXISTS (SELECT 1 FROM enrollments ise
                    WHERE ise.group_id = s.group_id AND ise.client_id = :clientId
                      AND ise.enrolled_at::date <= s.date AND (ise.left_at IS NULL OR ise.left_at::date >= s.date)))
    """

    /**
     * Контекст профиля клиента [clientId] организации [orgId] для аккаунта [account]. Нет подключённого
     * доступа — `CLIENT_NOT_FOUND`, одинаково для чужого и несуществующего клиента.
     */
    context(account: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun profile(orgId: OrgId, clientId: ClientId): ClientProfileContext {
        val currency =
            tr.sql("SELECT currency FROM organizations WHERE id = :orgId AND EXISTS ($CONNECTED_ACCESS_IDS)")
                .bind("accountId", account.accountId)
                .bind("orgId", orgId)
                .bind("clientId", clientId)
                .firstOrNull { it.asString("currency").trim().toCurrency() }
                ?: raise(clientNotFound(account.lang))
        return ClientProfileContext(account.accountId, account.sessionId, orgId, clientId, currency, account.lang)
    }

    /** Ошибка `CLIENT_NOT_FOUND` на языке [lang]. */
    fun clientNotFound(lang: Lang) = CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize(lang))
}
