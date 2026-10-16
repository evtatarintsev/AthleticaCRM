package org.athletica.crm.domain.org

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Календарь организации: «сегодня» в её часовом поясе.
 * Доменные правила получают дату параметром, а не читают часы сами; источник даты — этот календарь.
 */
interface OrgCalendar {
    /** Текущая дата в часовом поясе организации из [ctx]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun today(): LocalDate
}

/**
 * Реализация [OrgCalendar] поверх `organizations.timezone`.
 * Время берётся из [clock], который подменяется в тестах.
 */
class DbOrgCalendar(private val clock: Clock = Clock.System) : OrgCalendar {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun today(): LocalDate {
        val timezone =
            tr
                .sql("SELECT timezone FROM organizations WHERE id = :orgId")
                .bind("orgId", ctx.orgId)
                .firstOrNull { it.asString("timezone") }
                ?: raise(CommonDomainError("ORG_NOT_FOUND", Messages.OrgNotFound.localize()))
        return dateIn(timezone, clock.now())
    }
}

/** Дата момента [now] в часовом поясе [timezone] (идентификатор IANA, например `Asia/Yekaterinburg`). */
fun dateIn(timezone: String, now: Instant): LocalDate = now.toLocalDateTime(TimeZone.of(timezone)).date
