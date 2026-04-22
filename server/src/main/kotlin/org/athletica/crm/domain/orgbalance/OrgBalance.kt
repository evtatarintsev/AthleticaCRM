package org.athletica.crm.domain.orgbalance

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clients.PerformedBy
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface OrgBalance {
    val totalAmount: Double
    val history: List<OrgBalanceEntry>
}

@Serializable
data class OrgBalanceEntry(
    val id: Uuid,
    /** Изменение баланса: положительное — пополнение, отрицательное — списание. */
    val amount: Double,
    /** Баланс организации после операции. */
    val balanceAfter: Double,
    /** Тип операции: replenishment, bonus, system_fee, admin_credit, admin_debit. */
    val operationType: String,
    /** Метод оплаты — заполняется только для типа replenishment. */
    val paymentMethod: String?,
    /** Текстовое описание операции. */
    val description: String?,
    /** Сотрудник, выполнивший операцию, либо null если данные удалены. */
    val performedBy: PerformedBy?,
    /** Время операции. */
    val createdAt: Instant,
)
