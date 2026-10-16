package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuidOrNull

/** Участие клиента в занятии — то, что нужно для решения о списании абонемента. */
data class Participation(
    /** Занятие. */
    val sessionId: SessionId,
    /** Клиент-участник. */
    val clientId: ClientId,
    /** Группа занятия; `null`, если группа удалена. */
    val groupId: GroupId?,
    /** Дата занятия. */
    val sessionDate: LocalDate,
    /** Занятие проведено. */
    val completed: Boolean,
    /** Отметка «пришёл». */
    val present: Boolean,
) {
    /** Должно ли за участие быть действующее списание: занятие проведено, отметка «пришёл». */
    val shouldBeDebited: Boolean
        get() = completed && present && groupId != null
}

/**
 * Участия клиентов в занятиях организации.
 * Блокировка участия упорядочивает списание и погашение долга за одно и то же участие.
 */
interface Participations {
    /**
     * Возвращает участие клиента [clientId] в занятии [sessionId] по текущему состоянию журнала
     * и блокирует строку отметки до конца транзакции. `null` — клиента нет в журнале занятия.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun lock(sessionId: SessionId, clientId: ClientId): Participation?
}

/** Реализация [Participations] поверх `session_attendance` и `sessions`. */
class DbParticipations : Participations {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun lock(sessionId: SessionId, clientId: ClientId): Participation? =
        tr
            .sql(
                """
                SELECT s.group_id, s.date, s.status::text AS status, a.presence::text AS presence
                FROM session_attendance a
                JOIN sessions s ON s.id = a.session_id
                WHERE a.session_id = :sessionId AND a.client_id = :clientId AND s.org_id = :orgId
                FOR UPDATE OF a
                """.trimIndent(),
            )
            .bind("sessionId", sessionId)
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull {
                Participation(
                    sessionId = sessionId,
                    clientId = clientId,
                    groupId = it.asUuidOrNull("group_id")?.toGroupId(),
                    sessionDate = it.asLocalDate("date"),
                    completed = it.asString("status") == "completed",
                    present = it.asStringOrNull("presence") == "present",
                )
            }
}
