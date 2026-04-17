package org.athletica.crm.domain.mail

import org.athletica.crm.core.OrgEmailId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.db.Transaction

interface OrgEmails {
    /** Сохраняет письмо в той же транзакции что и бизнес-операция. */
    context(ctx: RequestContext, tr: Transaction)
    suspend fun schedule(email: OrgEmail)

    /** Возвращает очередь писем ожидающих отправки. Вызывается фоновым диспетчером. */
    suspend fun pending(limit: Int = 50): List<OrgEmail>

    suspend fun markSent(id: OrgEmailId)

    suspend fun markFailed(id: OrgEmailId, error: String)
}
