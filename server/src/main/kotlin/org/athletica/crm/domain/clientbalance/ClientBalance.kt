package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction
import org.athletica.crm.domain.audit.AuditLog
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ClientBalance {
    val clientId: ClientId
    val totalAmount: Double
    val history: List<ClientBalanceEntry>

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>, audit: AuditLog)
    suspend fun adjust(amount: Double, note: String): ClientBalance
}


/** Одна запись в журнале операций по балансу клиента. */
@Serializable
data class ClientBalanceEntry(
    val id: Uuid,
    /** Изменение баланса: положительное — пополнение, отрицательное — списание. */
    val amount: Double,
    /** Баланс клиента после операции. */
    val balanceAfter: Double,
    /** Тип операции: admin_credit, admin_debit, sale_overpayment, sale_payment, refund. */
    val operationType: String,
    /** Комментарий к операции (обязателен для admin_credit / admin_debit). */
    val note: String?,
    /** Сотрудник, выполнивший операцию, либо null если данные удалены. */
    val performedBy: PerformedBy?,
    /** Время операции. */
    val createdAt: Instant,
)
