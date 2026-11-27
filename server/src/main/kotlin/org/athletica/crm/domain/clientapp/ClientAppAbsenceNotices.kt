package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Предупреждения «Не придём» из кабинета: клиент профиля заранее сообщает, что пропустит занятие.
 * Отметку посещения и списания предупреждение не трогает — их по-прежнему ставит сотрудник.
 */
interface ClientAppAbsenceNotices {
    /**
     * Предупреждает в момент [now], что клиент [ctx] не придёт на занятие [sessionId]; повтор ничего не меняет.
     * Нет подключённого доступа — `CLIENT_NOT_FOUND`; клиент не числится на занятии — `SESSION_NOT_FOUND`;
     * занятие не запланировано или уже началось — `CLIENT_APP_ABSENCE_NOTICE_CLOSED`.
     */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun notify(
        sessionId: SessionId,
        now: Instant,
    )

    /** Отменяет предупреждение клиента [ctx] о занятии [sessionId] в момент [now]; ошибки — как у [notify]. */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun cancel(
        sessionId: SessionId,
        now: Instant,
    )
}
