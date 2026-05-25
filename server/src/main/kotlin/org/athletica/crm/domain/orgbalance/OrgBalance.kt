package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.AdminRequestContext
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface OrgBalance {
    val totalAmount: Money
    val history: List<OrgBalanceEntry>

    /**
     * Корректирует баланс организации: [amount] > 0 — зачисление, < 0 — списание.
     * [description] — обязательное описание причины операции.
     */
    context(ctx: AdminRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun adjust(amount: Money, description: String): OrgBalance

    /**
     * Зачисляет [amount] на баланс организации из платёжного шлюза.
     * Записывает операцию с типом `replenishment`.
     * Вызывается исключительно системными обработчиками (webhook, event handlers).
     * [amount] должна быть положительной.
     */
    context(ctx: SystemRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun replenish(amount: Money, description: String): OrgBalance
}

/** Одна запись в журнале операций по балансу организации. */
data class OrgBalanceEntry(
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
