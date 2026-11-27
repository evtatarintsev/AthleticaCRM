package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asUuid
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [ClientAppAbsenceNotices] в таблице `client_app_absence_notices`: действующее предупреждение — одно на клиента
 * в занятии, отмена проставляет `cancelled_at` и оставляет строку в истории.
 */
class DbClientAppAbsenceNotices : ClientAppAbsenceNotices {
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun notify(
        sessionId: SessionId,
        now: Instant,
    ) {
        val accessId = openSessionAccess(sessionId, now)
        tr.sql(
            """
            INSERT INTO client_app_absence_notices (session_id, client_id, access_id, created_at)
            VALUES (:sessionId, :clientId, :accessId, :now)
            ON CONFLICT (session_id, client_id) WHERE cancelled_at IS NULL DO NOTHING
            """.trimIndent(),
        )
            .bind("sessionId", sessionId)
            .bind("clientId", ctx.clientId)
            .bind("accessId", accessId)
            .bind("now", now)
            .execute()
    }

    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun cancel(
        sessionId: SessionId,
        now: Instant,
    ) {
        openSessionAccess(sessionId, now)
        tr.sql(
            """
            UPDATE client_app_absence_notices SET cancelled_at = :now
            WHERE session_id = :sessionId AND client_id = :clientId AND cancelled_at IS NULL
            """.trimIndent(),
        )
            .bind("now", now)
            .bind("sessionId", sessionId)
            .bind("clientId", ctx.clientId)
            .execute()
    }

    /**
     * Подключённый доступ профиля [ctx], если клиент числится на запланированном и ещё не начавшемся к [now]
     * занятии [sessionId]; иначе — ошибка из KDoc [ClientAppAbsenceNotices.notify].
     */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun openSessionAccess(
        sessionId: SessionId,
        now: Instant,
    ): Uuid {
        val accessId =
            tr.sql("${ClientProfileContexts.CONNECTED_ACCESS_IDS} LIMIT 1")
                .bind("accountId", ctx.accountId)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .firstOrNull { it.asUuid("id") }
                ?: raise(ClientProfileContexts.clientNotFound(ctx.lang))
        val open =
            tr.sql(
                """
                SELECT s.status = 'scheduled' AND (s.date + s.start_time) AT TIME ZONE o.timezone > CAST(:now AS timestamptz) AS open
                FROM sessions s JOIN organizations o ON o.id = s.org_id
                WHERE s.id = :sessionId AND s.org_id = :orgId AND ${ClientProfileContexts.IN_SESSION}
                """.trimIndent(),
            )
                .bind("now", now)
                .bind("sessionId", sessionId)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .firstOrNull { it.asBoolean("open") }
                ?: raise(CommonDomainError("SESSION_NOT_FOUND", Messages.SessionNotFound.localize(ctx.lang)))
        ensure(open) { CommonDomainError("CLIENT_APP_ABSENCE_NOTICE_CLOSED", Messages.ClientAppAbsenceNoticeClosed.localize(ctx.lang)) }
        return accessId
    }
}
