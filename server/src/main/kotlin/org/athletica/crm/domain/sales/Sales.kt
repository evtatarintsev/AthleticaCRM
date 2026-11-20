package org.athletica.crm.domain.sales

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.actingEmployeeId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.entityids.SaleItemId
import org.athletica.crm.core.entityids.toClientId
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
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asUuid
import java.math.BigInteger

/** Суммы оплаты и возврата не хранятся в продаже: их источник — главная книга. */
data class Sale(
    val id: SaleId,
    val clientId: ClientId,
    val total: Money,
    val cancelled: Boolean,
)

/** Позиция продажи до записи: её сумма вычисляется из количества и цены. */
data class SaleLine(val name: String, val quantity: Int, val unitPrice: Money)

class Sales(private val ledger: Ledger = Ledger(), private val calendar: OrgCalendar = DbOrgCalendar()) {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: SaleId): Sale =
        tr.sql(
            """
            SELECT s.client_id, s.cancelled_at, COALESCE(SUM(i.amount), 0) AS total FROM sales s
            LEFT JOIN sale_items i ON i.sale_id = s.id WHERE s.id = :id AND s.org_id = :org GROUP BY s.id
            """.trimIndent(),
        ).bind("id", id).bind("org", ctx.orgId).firstOrNull {
            Sale(id, it.asUuid("client_id").toClientId(), it.asMoney("total", ctx.currency), it.asInstantOrNull("cancelled_at") != null)
        } ?: raise(ledgerError("SALE_NOT_FOUND"))

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
        if (!total.isZero) ledger.record(LedgerEntries.sale(ledger.account(LedgerOwner.Receivable(id, clientId)), ledger.account(LedgerOwner.Revenue), total), soldOn, id)
        return ids
    }
}
