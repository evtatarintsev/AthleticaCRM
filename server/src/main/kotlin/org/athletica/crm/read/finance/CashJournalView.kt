package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.finance.CashJournalResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.storage.Transaction

/** Журнал показывает исходные операции, а сторно — признаком отмены; итоги исключают отменённые. */
interface CashJournalView {
    /** Страница журнала по фильтрам [query] с общим количеством и итогами прихода и расхода. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun page(query: CashJournalQuery): CashJournalResponse
}

/** Нормализованный запрос журнала: период проверен, лимит и смещение ограничены в routes. */
data class CashJournalQuery(
    /** Первая дата операции периода. */
    val from: LocalDate,
    /** Последняя дата операции периода, не раньше [from]. */
    val to: LocalDate,
    /** Отбор по кассам; пустой список — все доступные. */
    val cashAccountIds: List<CashAccountId>,
    /** Отбор по статьям; пустой список — все. */
    val articleIds: List<CashArticleId>,
    /** Отбор по видам операций; пустой список — все. */
    val kinds: List<LedgerKind>,
    /** Отбор по клиенту. */
    val clientId: ClientId?,
    /** Отбор по автору операции. */
    val employeeId: EmployeeId?,
    /** Показывать ли отменённые операции. */
    val includeReversed: Boolean,
    /** Размер страницы. */
    val limit: Int,
    /** Смещение страницы. */
    val offset: Int,
    /** Сегодняшняя дата организации: кассир может отменить только свою сегодняшнюю оплату. */
    val today: LocalDate,
)
