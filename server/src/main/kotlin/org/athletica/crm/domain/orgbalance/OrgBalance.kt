package org.athletica.crm.domain.orgbalance

import org.athletica.crm.core.entityids.EmployeeId
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface OrgBalance {
    val totalAmount: Double
    val history: List<OrgBalanceEntry>
}

/** Одна запись в журнале операций по балансу организации. */
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
    /** Идентификатор сотрудника, выполнившего операцию. */
    val performedBy: EmployeeId,
    /** Время операции. */
    val createdAt: Instant,
)
