package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Сотрудник, выполнивший операцию с балансом. */

@Serializable
data class PerformedBy(
    val id: Uuid,
    val name: String,
)

/** Одна запись в журнале операций по балансу клиента. */
@Serializable
data class BalanceJournalEntry(
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

/** Ответ на запрос истории баланса клиента. */
@Serializable
data class ClientBalanceHistoryResponse(
    val entries: List<BalanceJournalEntry>,
)
