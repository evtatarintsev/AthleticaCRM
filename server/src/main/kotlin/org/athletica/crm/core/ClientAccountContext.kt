package org.athletica.crm.core

import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId

/**
 * Контекст запроса из кабинета клиента: аккаунт и сеанс из клиентского токена. У аккаунта нет
 * организации, поэтому это **не** вариант [RequestContext]: функции контура сотрудников по типам
 * не вызываются из клиентских маршрутов. Записи в другие домены кабинет делает в
 * [SystemRequestContext] организации через [SystemContexts.forOrg].
 */
data class ClientAccountContext(
    /** Аккаунт кабинета из claim `accountId`. */
    val accountId: ClientAppAccountId,
    /** Сеанс из claim `sid`. */
    val sessionId: ClientAppSessionId,
    /** Язык запроса. */
    val lang: Lang,
)
