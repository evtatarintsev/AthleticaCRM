package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.actingEmployeeId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

/** Неизменяемые параметры абонемента, прочитанные из строки `memberships`. */
data class StoredMembershipTerms(
    override val id: MembershipId,
    override val clientId: ClientId,
    override val tariffPlanId: TariffPlanId?,
    override val name: String,
    override val sessionsTotal: Int?,
    override val duration: MembershipDuration,
    override val groups: List<MembershipGroup>,
    override val price: Money,
    override val closeMode: CloseMode,
    override val issuedOn: LocalDate,
    override val issuedBy: EmployeeId?,
) : MembershipTerms

/** Неактивированный абонемент «с даты» поверх строки `memberships`. */
data class DbInactiveOnDate(
    private val terms: StoredMembershipTerms,
    override val activation: Activation.OnDate,
    override val sessionsRemaining: Int?,
) : InactiveMembership.OnDate,
    MembershipTerms by terms {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun activate(startDate: LocalDate, reason: ActivationReason): ActiveMembership = activateMembership(this, terms, startDate, reason)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun revoke(): RevokedMembership = revokeMembership(this, terms, MembershipState.INACTIVE, null, null)
}

/** Неактивированный абонемент «при первом посещении» поверх строки `memberships`. */
data class DbInactiveOnFirstVisit(
    private val terms: StoredMembershipTerms,
    override val activation: Activation.OnFirstVisit,
    override val sessionsRemaining: Int?,
) : InactiveMembership.OnFirstVisit,
    MembershipTerms by terms {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun activate(startDate: LocalDate, reason: ActivationReason): ActiveMembership = activateMembership(this, terms, startDate, reason)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun revoke(): RevokedMembership = revokeMembership(this, terms, MembershipState.INACTIVE, null, null)
}

/** Активный абонемент поверх строки `memberships`. */
data class DbActiveMembership(
    private val terms: StoredMembershipTerms,
    override val activation: Activation,
    override val sessionsRemaining: Int?,
    override val startDate: LocalDate,
    override val endDate: LocalDate,
    override val closeNotifiedAt: Instant?,
) : ActiveMembership,
    MembershipTerms by terms {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun close(reason: CloseReason, closedOn: LocalDate): ClosedMembership {
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships
                    SET state = 'CLOSED', closed_on = :closedOn, close_reason = :reason
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE'
                    """.trimIndent(),
                )
                .bind("closedOn", closedOn)
                .bind("reason", reason.name)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        requireTransition(updated)
        recordMembershipEvent(
            id,
            MembershipEventKind.CLOSED,
            buildJsonObject {
                put("reason", reason.name)
                put("closedOn", closedOn.toString())
            },
        )
        return DbClosedMembership(terms, activation, sessionsRemaining, startDate, endDate, closedOn, reason)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun revoke(): RevokedMembership {
        val hasDebits =
            tr
                .sql("SELECT 1 FROM membership_debits WHERE membership_id = :id AND reversed_at IS NULL LIMIT 1")
                .bind("id", id)
                .firstOrNull { 1 } != null
        if (hasDebits) {
            raise(CommonDomainError("MEMBERSHIP_HAS_DEBITS", Messages.MembershipHasDebits.localize()))
        }
        return revokeMembership(this, terms, MembershipState.ACTIVE, startDate, endDate)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun debit(sessionId: SessionId, sessionDate: LocalDate, today: LocalDate): Membership {
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships SET sessions_remaining = sessions_remaining - 1
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE'
                      AND (sessions_remaining IS NULL OR sessions_remaining > 0)
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        if (updated != 1L) {
            throw MembershipDebitConflict("Абонемент $id изменён параллельно при списании занятия $sessionId")
        }
        tr
            .sql(
                """
                INSERT INTO membership_debits (org_id, membership_id, client_id, session_id, kind)
                VALUES (:orgId, :membershipId, :clientId, :sessionId, :kind)
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("membershipId", id)
            .bind("clientId", clientId)
            .bind("sessionId", sessionId)
            .bind("kind", DebitKind.REGULAR.name)
            .execute()
        recordMembershipEvent(id, MembershipEventKind.DEBITED, debitPayload(sessionId, sessionDate, DebitKind.REGULAR))
        val debited = copy(sessionsRemaining = sessionsRemaining?.minus(1))
        return if (closeMode == CloseMode.ON_LAST_SESSION && debited.sessionsRemaining == 0) {
            debited.close(CloseReason.LAST_SESSION, today)
        } else {
            debited
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun reverse(debit: MembershipDebit, today: LocalDate): ActiveMembership {
        reverseDebit(this, debit)
        val returned = copy(sessionsRemaining = sessionsRemaining?.plus(1))
        if (returned.closeNotifiedAt == null || returned.statusAt(today).closePending) {
            return returned
        }
        tr
            .sql("UPDATE memberships SET close_notified_at = NULL WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
        return returned.copy(closeNotifiedAt = null)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markCloseNotified(): ActiveMembership {
        val now = Clock.System.now()
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships SET close_notified_at = :now
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE' AND close_notified_at IS NULL
                    """.trimIndent(),
                )
                .bind("now", now)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        requireTransition(updated)
        recordMembershipEvent(
            id,
            MembershipEventKind.CLOSE_NOTIFIED,
            buildJsonObject {
                issuedBy?.let { put("recipientId", it.value.toString()) }
            },
        )
        return copy(closeNotifiedAt = now)
    }
}

/** Закрытый абонемент поверх строки `memberships`. */
data class DbClosedMembership(
    private val terms: StoredMembershipTerms,
    override val activation: Activation,
    override val sessionsRemaining: Int?,
    override val startDate: LocalDate,
    override val endDate: LocalDate,
    override val closedOn: LocalDate,
    override val closeReason: CloseReason,
) : ClosedMembership,
    MembershipTerms by terms {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun reverse(debit: MembershipDebit): ClosedMembership {
        reverseDebit(this, debit)
        return copy(sessionsRemaining = sessionsRemaining?.plus(1))
    }
}

/** Отозванный абонемент поверх строки `memberships`. */
data class DbRevokedMembership(
    private val terms: StoredMembershipTerms,
    override val activation: Activation,
    override val sessionsRemaining: Int?,
    override val startDate: LocalDate?,
    override val endDate: LocalDate?,
) : RevokedMembership,
    MembershipTerms by terms

/** Сохранённое состояние абонемента — значение колонки `memberships.state`. */
enum class MembershipState {
    /** Неактивирован. */
    INACTIVE,

    /** Активный. */
    ACTIVE,

    /** Закрыт. */
    CLOSED,

    /** Отозван. */
    REVOKED,
}

/** Вид записи истории абонемента — значение колонки `membership_events.kind`. */
enum class MembershipEventKind {
    /** Выдача. */
    ISSUED,

    /** Активация; в данных — причина и даты. */
    ACTIVATED,

    /** Отправка уведомления «к закрытию». */
    CLOSE_NOTIFIED,

    /** Закрытие; в данных — причина и дата. */
    CLOSED,

    /** Отзыв. */
    REVOKED,

    /** Списание занятия; в данных — занятие, его дата и вид списания. */
    DEBITED,

    /** Возврат занятия; в данных — занятие и его дата. */
    DEBIT_REVERSED,
}

/**
 * Возвращает занятие списания [debit] на абонемент [membership]: отменяет списание,
 * увеличивает остаток (кроме безлимита) и пишет возврат в историю.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun reverseDebit(membership: Membership, debit: MembershipDebit) {
    val reversed =
        tr
            .sql(
                """
                UPDATE membership_debits SET reversed_at = now()
                WHERE id = :id AND membership_id = :membershipId AND org_id = :orgId AND reversed_at IS NULL
                """.trimIndent(),
            )
            .bind("id", debit.id)
            .bind("membershipId", membership.id)
            .bind("orgId", ctx.orgId)
            .execute()
    requireTransition(reversed)
    tr
        .sql("UPDATE memberships SET sessions_remaining = sessions_remaining + 1 WHERE id = :id AND org_id = :orgId")
        .bind("id", membership.id)
        .bind("orgId", ctx.orgId)
        .execute()
    recordMembershipEvent(
        membership.id,
        MembershipEventKind.DEBIT_REVERSED,
        buildJsonObject {
            put("sessionId", debit.sessionId.value.toString())
            put("sessionDate", debit.sessionDate.toString())
        },
    )
}

/** Данные записи истории о списании занятия [sessionId] от [sessionDate] вида [kind]. */
internal fun debitPayload(sessionId: SessionId, sessionDate: LocalDate, kind: DebitKind): JsonObject =
    buildJsonObject {
        put("sessionId", sessionId.value.toString())
        put("sessionDate", sessionDate.toString())
        put("kind", kind.name)
    }

/** Активирует неактивированный абонемент [membership] с датой начала [startDate] по причине [reason]. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun activateMembership(
    membership: InactiveMembership,
    terms: StoredMembershipTerms,
    startDate: LocalDate,
    reason: ActivationReason,
): ActiveMembership {
    val endDate = terms.duration.endDateFrom(startDate)
    val updated =
        tr
            .sql(
                """
                UPDATE memberships SET state = 'ACTIVE', start_date = :startDate, end_date = :endDate
                WHERE id = :id AND org_id = :orgId AND state = 'INACTIVE'
                """.trimIndent(),
            )
            .bind("startDate", startDate)
            .bind("endDate", endDate)
            .bind("id", membership.id)
            .bind("orgId", ctx.orgId)
            .execute()
    requireTransition(updated)
    recordActivation(membership.id, reason, startDate, endDate)
    return DbActiveMembership(terms, membership.activation, membership.sessionsRemaining, startDate, endDate, null)
}

/** Отзывает абонемент [membership] из состояния [from]; [startDate]/[endDate] — его даты, если были. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun revokeMembership(
    membership: Membership,
    terms: StoredMembershipTerms,
    from: MembershipState,
    startDate: LocalDate?,
    endDate: LocalDate?,
): RevokedMembership {
    val updated =
        tr
            .sql("UPDATE memberships SET state = 'REVOKED' WHERE id = :id AND org_id = :orgId AND state = :from")
            .bind("id", membership.id)
            .bind("orgId", ctx.orgId)
            .bind("from", from.name)
            .execute()
    requireTransition(updated)
    recordMembershipEvent(membership.id, MembershipEventKind.REVOKED)
    return DbRevokedMembership(terms, membership.activation, membership.sessionsRemaining, startDate, endDate)
}

/** Пишет в историю активацию абонемента [id] по причине [reason] со сроком [startDate]..[endDate]. */
context(ctx: RequestContext, tr: Transaction)
internal suspend fun recordActivation(
    id: MembershipId,
    reason: ActivationReason,
    startDate: LocalDate,
    endDate: LocalDate,
) {
    recordMembershipEvent(
        id,
        MembershipEventKind.ACTIVATED,
        buildJsonObject {
            put("reason", reason.name)
            put("startDate", startDate.toString())
            put("endDate", endDate.toString())
        },
    )
}

/**
 * Пишет запись истории [kind] с данными [payload] абонемента [id] в текущей транзакции.
 * Автор — сотрудник из [ctx]; для системного контекста — `NULL`.
 */
context(ctx: RequestContext, tr: Transaction)
internal suspend fun recordMembershipEvent(id: MembershipId, kind: MembershipEventKind, payload: JsonObject = JsonObject(emptyMap())) {
    tr
        .sql(
            """
            INSERT INTO membership_events (org_id, membership_id, actor_id, kind, payload)
            VALUES (:orgId, :membershipId, :actorId, :kind, :payload::jsonb)
            """.trimIndent(),
        )
        .bind("orgId", ctx.orgId)
        .bind("membershipId", id)
        .bind("actorId", ctx.actingEmployeeId)
        .bind("kind", kind.name)
        .bind("payload", payload.toString())
        .execute()
}

/** Отклоняет переход, если строка абонемента не обновлена: состояние изменилось параллельно. */
context(ctx: RequestContext, raise: Raise<DomainError>)
private fun requireTransition(updatedRows: Long) {
    if (updatedRows != 1L) {
        raise(CommonDomainError("MEMBERSHIP_CHANGED_CONCURRENTLY", Messages.MembershipChangedConcurrently.localize()))
    }
}
