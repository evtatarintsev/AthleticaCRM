package org.athletica.crm.api.schemas.orgbalance

import kotlinx.serialization.Serializable
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
    /** Тип операции: admin_credit, admin_debit, system_fee, replenishment, bonus. */
    val operationType: String,
    /** Текстовое описание операции. */
    val description: String,
    /** Время операции. */
    val createdAt: Instant,
)

/** Ответ на запрос детального баланса организации: сумма + история операций. */
@Serializable
data class OrgBalanceDetailResponse(
    val totalAmount: Money,
    val history: List<OrgBalanceJournalEntry>,
)
