package org.athletica.crm.api.schemas.orgbalance

import kotlinx.serialization.Serializable
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.orgbalance.OrgBalanceOperationType
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
    /** Тип операции. */
    val operationType: OrgBalanceOperationType,
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
