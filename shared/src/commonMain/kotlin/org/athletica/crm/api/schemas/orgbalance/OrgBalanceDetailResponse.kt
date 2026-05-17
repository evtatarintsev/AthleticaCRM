package org.athletica.crm.api.schemas.orgbalance

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.core.money.Money
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Одна запись в журнале операций по балансу организации. */
@Serializable
data class OrgBalanceJournalEntry(
    val id: Uuid,
    /** Изменение баланса: положительное — пополнение, отрицательное — списание. */
    val amount: Money,
    /** Баланс организации после операции. */
    val balanceAfter: Money,
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

/** Ответ на запрос детального баланса организации: сумма + история операций. */
@Serializable
data class OrgBalanceDetailResponse(
    val totalAmount: Money,
    val history: List<OrgBalanceJournalEntry>,
)
