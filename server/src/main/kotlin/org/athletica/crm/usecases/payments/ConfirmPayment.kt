package org.athletica.crm.usecases.payments

import arrow.core.raise.context.Raise
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.domain.payment.PaymentAlreadyProcessed
import org.athletica.crm.domain.payment.Payments
import org.athletica.crm.storage.Database

/**
 * Подтверждает оплату платежа [externalPaymentId] шлюза [gatewayName] и зачисляет его сумму
 * на баланс организации.
 *
 * Отметка оплаты и зачисление выполняются в одной транзакции: любой отказ, поднятый внутри,
 * откатывает обе записи, и платёж остаётся ожидающим.
 * Неизвестный или уже оплаченный платёж поднимается как [PaymentAlreadyProcessed] без изменения данных —
 * вызывающий трактует это как допустимое повторное уведомление.
 */
context(raise: Raise<AppError>)
suspend fun confirmPayment(
    db: Database,
    payments: Payments,
    orgBalances: OrgBalances,
    gatewayName: String,
    externalPaymentId: String,
) {
    db.transaction {
        val payment = payments.markAsPaid(gatewayName, externalPaymentId)
        context(SystemContexts.forOrg(payment.orgId)) {
            orgBalances.current().replenish(
                amount = payment.amount,
                description = "Пополнение баланса, платёж #${payment.externalPaymentId}",
            )
        }
    }
}
