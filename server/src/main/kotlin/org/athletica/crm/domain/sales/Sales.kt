package org.athletica.crm.domain.sales

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.actingEmployeeId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.entityids.SaleItemId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toLedgerTransactionId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.SaleItemKind
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.ledger.Ledger
import org.athletica.crm.domain.ledger.LedgerEntries
import org.athletica.crm.domain.ledger.LedgerOwner
import org.athletica.crm.domain.ledger.ledgerError
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import java.math.BigInteger
import kotlin.time.Instant

/** Продажа клиенту. Суммы оплаты и возврата не хранятся в продаже: их источник — главная книга. */
sealed interface Sale {
    /** Идентификатор продажи. */
    val id: SaleId

    /** Клиент продажи. */
    val clientId: ClientId

    /** Итог продажи — сумма позиций. */
    val total: Money
}

/** Действующая продажа: по ней принимают оплаты и возвраты, её можно отменить. */
data class ActiveSale(
    override val id: SaleId,
    override val clientId: ClientId,
    override val total: Money,
) : Sale

/** Отменённая продажа — конечное состояние. */
data class CancelledSale(
    override val id: SaleId,
    override val clientId: ClientId,
    override val total: Money,
    /** Момент отмены. */
    val cancelledAt: Instant,
    /** Сотрудник, отменивший продажу. */
    val cancelledBy: EmployeeId,
    /** Причина отмены. */
    val reason: String,
) : Sale

/** Позиция продажи до записи: её сумма вычисляется из количества и цены. */
data class SaleLine(
    /** Название позиции. */
    val name: String,
    /** Количество, положительное целое. */
    val quantity: Int,
    /** Цена за единицу, не отрицательная. */
    val unitPrice: Money,
)

/** Продажи клиентам: документ продажи и его запись в главную книгу. */
class Sales(private val ledger: Ledger = Ledger(), private val calendar: OrgCalendar = DbOrgCalendar()) {
    /** Продажа [id] организации с итогом по позициям; если её нет — ошибка. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: SaleId): Sale =
        tr.sql(
            """
            SELECT s.client_id, s.cancelled_at, s.cancelled_by, s.cancel_reason, COALESCE(SUM(i.amount), 0) AS total FROM sales s
            LEFT JOIN sale_items i ON i.sale_id = s.id WHERE s.id = :id AND s.org_id = :org GROUP BY s.id
            """.trimIndent(),
        ).bind("id", id).bind("org", ctx.orgId).firstOrNull {
            val clientId = it.asUuid("client_id").toClientId()
            val total = it.asMoney("total", ctx.currency)
            when (val cancelledAt = it.asInstantOrNull("cancelled_at")) {
                null -> ActiveSale(id, clientId, total)
                else -> CancelledSale(id, clientId, total, cancelledAt, it.asUuid("cancelled_by").toEmployeeId(), it.asString("cancel_reason"))
            }
        } ?: raise(ledgerError("SALE_NOT_FOUND"))

    /**
     * Блокирует клиента продажи [id] и его счета аванса и долга в общем порядке блокировок,
     * затем перечитывает продажу; отменённая продажа — ошибка `SALE_CANCELLED`.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun lockActive(id: SaleId): ActiveSale {
        val clientId = byId(id).clientId
        tr.sql("SELECT id FROM clients WHERE id = :client AND org_id = :org FOR UPDATE")
            .bind("client", clientId).bind("org", ctx.orgId).firstOrNull { 1 }
        ledger.lock(listOf(ledger.account(LedgerOwner.Advance(clientId)), ledger.account(LedgerOwner.Receivable(id, clientId))))
        return when (val sale = byId(id)) {
            is ActiveSale -> sale
            is CancelledSale -> raise(ledgerError("SALE_CANCELLED"))
        }
    }

    /** Абонементы, выданные позициями продажи [sale], в порядке идентификаторов. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun membershipIds(sale: ActiveSale): List<MembershipId> =
        tr.sql(
            """
            SELECT m.id FROM memberships m JOIN sale_items i ON i.id = m.sale_item_id
            JOIN sales s ON s.id = i.sale_id WHERE s.id = :sale AND s.org_id = :org ORDER BY m.id
            """.trimIndent(),
        ).bind("sale", sale.id).bind("org", ctx.orgId).list { it.asUuid("id").toMembershipId() }

    /** Несторнированные проводки по продаже [sale] — продажа, оплаты, возвраты и выплаты — в порядке идентификаторов. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun openEntries(sale: ActiveSale): List<LedgerTransactionId> =
        tr.sql(
            """
            SELECT t.id FROM ledger_transactions t WHERE t.sale_id = :sale AND t.org_id = :org AND t.kind <> 'reversal'
            AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id) ORDER BY t.id
            """.trimIndent(),
        ).bind("sale", sale.id).bind("org", ctx.orgId).list { it.asUuid("id").toLedgerTransactionId() }

    /**
     * Переводит продажу [sale] в отменённую от имени текущего сотрудника с причиной [reason].
     * Если продажу уже отменили — ошибка `SALE_CANCELLED`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun cancel(sale: ActiveSale, reason: String): CancelledSale {
        val cancelledAt =
            tr.sql(
                """
                UPDATE sales SET cancelled_at = clock_timestamp(), cancelled_by = :actor, cancel_reason = :reason
                WHERE id = :sale AND org_id = :org AND cancelled_at IS NULL RETURNING cancelled_at
                """.trimIndent(),
            ).bind("actor", ctx.employee.id).bind("reason", reason.trim()).bind("sale", sale.id).bind("org", ctx.orgId)
                .firstOrNull { it.asInstant("cancelled_at") } ?: raise(ledgerError("SALE_CANCELLED"))
        return CancelledSale(sale.id, sale.clientId, sale.total, cancelledAt, ctx.employee.id, reason.trim())
    }

    /**
     * Оформляет продажу [id] активному клиенту [clientId] датой [soldOn] с позициями [items] вида [kind]
     * и возвращает идентификаторы позиций. Ненулевой итог записывает долг клиента и выручку.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun create(
        id: SaleId,
        clientId: ClientId,
        soldOn: LocalDate,
        items: List<SaleLine>,
        kind: SaleItemKind = SaleItemKind.SERVICE,
    ): List<SaleItemId> {
        ensure(soldOn <= calendar.today()) { ledgerError("FINANCE_FUTURE_DATE") }
        ensure(items.isNotEmpty()) { ledgerError("SALE_ITEMS_REQUIRED") }
        ensure(items.all { it.name.isNotBlank() && it.name.trim().length <= 255 && it.quantity > 0 && it.unitPrice.currency == ctx.currency && !it.unitPrice.isNegative }) { ledgerError("SALE_ITEM_INVALID") }
        val totalMinor = items.fold(BigInteger.ZERO) { sum, item -> sum + item.unitPrice.minorUnits.toBigInteger() * item.quantity.toBigInteger() }
        ensure(totalMinor <= Long.MAX_VALUE.toBigInteger() && totalMinor <= BigInteger("999999999999999999")) { ledgerError("SALE_AMOUNT_TOO_LARGE") }
        ensure(tr.sql("SELECT id FROM clients WHERE id = :id AND org_id = :org AND state = 'ACTIVE' FOR UPDATE").bind("id", clientId).bind("org", ctx.orgId).firstOrNull { 1 } != null) { ledgerError("SALE_CLIENT_NOT_ACTIVE") }
        tr.sql("INSERT INTO sales (id, org_id, client_id, sold_on, created_by) VALUES (:id, :org, :client, :on, :actor)")
            .bind("id", id).bind("org", ctx.orgId).bind("client", clientId).bind("on", soldOn).bind("actor", ctx.actingEmployeeId).execute()
        val ids =
            items.map { item ->
                val itemId = SaleItemId.new()
                tr.sql("INSERT INTO sale_items (id, sale_id, kind, name, quantity, unit_price, amount) VALUES (:id, :sale, :kind, :name, :quantity, :price, :amount)")
                    .bind("id", itemId).bind("sale", id).bind("kind", kind.name.lowercase()).bind("name", item.name.trim()).bind("quantity", item.quantity)
                    .bind("price", item.unitPrice).bind("amount", Money(item.unitPrice.minorUnits * item.quantity, ctx.currency)).execute()
                itemId
            }
        val total = Money(totalMinor.toLong(), ctx.currency)
        if (!total.isZero) {
            ledger.record(LedgerEntries.sale(ledger.account(LedgerOwner.Receivable(id, clientId)), ledger.account(LedgerOwner.Revenue), total), soldOn, id)
        }
        return ids
    }
}
