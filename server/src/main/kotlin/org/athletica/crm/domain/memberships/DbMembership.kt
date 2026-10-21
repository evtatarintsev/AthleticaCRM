package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
import org.athletica.crm.core.subscription.UnfreezeReason
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
    override val freeze: MembershipFreeze?,
) : ActiveMembership,
    MembershipTerms by terms {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun close(reason: CloseReason, closedOn: LocalDate): ClosedMembership {
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships
                    SET state = 'CLOSED', closed_on = :closedOn, close_reason = :reason, $CLEAR_FREEZE
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
    override suspend fun repayDebt(sessions: List<DebtSession>, today: LocalDate): Membership {
        val count = sessions.size
        val remaining = sessionsRemaining
        if (remaining != null && count > remaining) {
            raise(CommonDomainError("DEBT_EXCEEDS_REMAINING", Messages.MembershipDebtExceedsRemaining.localize()))
        }
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships SET sessions_remaining = sessions_remaining - :count
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE'
                      AND (sessions_remaining IS NULL OR sessions_remaining >= :count)
                    """.trimIndent(),
                )
                .bind("count", count)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        if (updated != 1L) {
            raise(CommonDomainError("DEBT_EXCEEDS_REMAINING", Messages.MembershipDebtExceedsRemaining.localize()))
        }
        sessions.forEach { session ->
            try {
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
                    .bind("sessionId", session.sessionId)
                    .bind("kind", DebitKind.DEBT.name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("ALREADY_DEBITED", Messages.SessionAlreadyDebited.localize()))
            }
            recordMembershipEvent(id, MembershipEventKind.DEBITED, debitPayload(session.sessionId, session.sessionDate, DebitKind.DEBT))
        }
        val repaid = copy(sessionsRemaining = remaining?.minus(count))
        return if (closeMode == CloseMode.ON_LAST_SESSION && repaid.sessionsRemaining == 0) {
            repaid.close(CloseReason.LAST_SESSION, today)
        } else {
            repaid
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
    override suspend fun freeze(
        start: LocalDate,
        end: LocalDate,
        reason: String,
        today: LocalDate,
    ): ActiveMembership {
        freezePeriodError(start, end, reason, endDate, today)?.let { raise(it) }
        if (freeze != null) {
            raise(CommonDomainError("MEMBERSHIP_ALREADY_FROZEN", Messages.MembershipAlreadyFrozen.localize()))
        }
        val frozen = MembershipFreeze(start, end, reason.trim(), ctx.actingEmployeeId)
        val newEndDate = endDate.plus(DatePeriod(days = frozen.days))
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships
                    SET end_date = :endDate, freeze_start = :start, freeze_end = :end,
                        freeze_reason = :reason, freeze_by = :frozenBy
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE' AND freeze_start IS NULL
                    """.trimIndent(),
                )
                .bind("endDate", newEndDate)
                .bind("start", frozen.start)
                .bind("end", frozen.end)
                .bind("reason", frozen.reason)
                .bind("frozenBy", frozen.frozenBy)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        requireTransition(updated)
        recordMembershipEvent(
            id,
            MembershipEventKind.FROZEN,
            buildJsonObject {
                put("startDate", frozen.start.toString())
                put("endDate", frozen.end.toString())
                put("reason", frozen.reason)
                put("membershipEndDate", newEndDate.toString())
            },
        )
        return copy(endDate = newEndDate, freeze = frozen)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun extend(newEndDate: LocalDate, reason: String, today: LocalDate): ActiveMembership {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) {
            raise(CommonDomainError("EXTENSION_REASON_REQUIRED", Messages.MembershipExtensionReasonRequired.localize()))
        }
        if (newEndDate <= endDate) {
            raise(CommonDomainError("EXTENSION_NOT_LATER", Messages.MembershipExtensionNotLater.localize()))
        }
        if (freeze != null) {
            raise(CommonDomainError("MEMBERSHIP_FROZEN", Messages.MembershipExtensionFrozen.localize()))
        }
        val extended = copy(endDate = newEndDate)
        val notifiedAt = closeNotifiedAt?.takeIf { extended.statusAt(today).closePending }
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships SET end_date = :endDate, close_notified_at = :notifiedAt
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE' AND freeze_start IS NULL
                    """.trimIndent(),
                )
                .bind("endDate", newEndDate)
                .bind("notifiedAt", notifiedAt)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        requireTransition(updated)
        recordMembershipEvent(
            id,
            MembershipEventKind.EXTENDED,
            buildJsonObject {
                put("previousEndDate", endDate.toString())
                put("endDate", newEndDate.toString())
                put("reason", trimmed)
            },
        )
        return extended.copy(closeNotifiedAt = notifiedAt)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unfreeze(day: LocalDate, reason: UnfreezeReason): DbActiveMembership {
        val current = freeze ?: raise(CommonDomainError("MEMBERSHIP_NOT_FROZEN", Messages.MembershipNotFrozen.localize()))
        val newEndDate = endDate.minus(DatePeriod(days = current.unusedDaysFrom(day)))
        val updated =
            tr
                .sql(
                    """
                    UPDATE memberships SET end_date = :endDate, $CLEAR_FREEZE
                    WHERE id = :id AND org_id = :orgId AND state = 'ACTIVE' AND freeze_start IS NOT NULL
                    """.trimIndent(),
                )
                .bind("endDate", newEndDate)
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .execute()
        requireTransition(updated)
        recordMembershipEvent(
            id,
            MembershipEventKind.UNFROZEN,
            buildJsonObject {
                put("reason", reason.name)
                put("startDate", current.start.toString())
                put("endDate", current.end.toString())
                put("membershipEndDate", newEndDate.toString())
            },
        )
        return copy(endDate = newEndDate, freeze = null)
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

    /** Заморозка; в данных — период, причина и новая дата окончания абонемента. */
    FROZEN,

    /** Снятие заморозки; в данных — основание, период и новая дата окончания абонемента. */
    UNFROZEN,

    /** Ручное продление; в данных — прежняя и новая даты окончания и причина. */
    EXTENDED,
}

/** SQL-присваивание, снимающее текущую заморозку строки `memberships`. */
private const val CLEAR_FREEZE = "freeze_start = NULL, freeze_end = NULL, freeze_reason = NULL, freeze_by = NULL"

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
    return DbActiveMembership(terms, membership.activation, membership.sessionsRemaining, startDate, endDate, null, null)
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
            .sql("UPDATE memberships SET state = 'REVOKED', $CLEAR_FREEZE WHERE id = :id AND org_id = :orgId AND state = :from")
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
