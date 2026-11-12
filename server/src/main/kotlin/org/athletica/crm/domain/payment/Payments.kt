package org.athletica.crm.domain.payment

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction

/** Репозиторий платёжных транзакций. */
interface Payments {
    /**
     * Создаёт транзакцию в статусе PENDING.
     * [amount] должна быть положительной (см. [ensurePaymentAmountPositive]).
     * Требует [EmployeeRequestContext]: платёж инициируется только аутентифицированным сотрудником.
     * [ctx.employee.id] автоматически используется как [Payment.createdBy].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun create(
        gatewayName: String,
        externalPaymentId: String,
        amount: Money,
        description: String,
        confirmationUrl: String,
    ): Payment

    /**
     * Атомарно переводит транзакцию в статус PAID и проставляет [Payment.confirmedAt].
     * Поиск по глобально уникальному ключу ([gatewayName], [externalPaymentId]) — `org_id` не нужен.
     * Если транзакция не найдена или уже не в статусе PENDING — бросает [PaymentAlreadyProcessed].
     * Не требует [org.athletica.crm.core.RequestContext]: не фильтрует по org_id.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun markAsPaid(gatewayName: String, externalPaymentId: String): Payment
}

/** Транзакция не найдена или уже обработана (статус != PENDING). */
class PaymentAlreadyProcessed(
    externalPaymentId: String,
) : DomainError {
    override val code = "PAYMENT_ALREADY_PROCESSED"
    override val message = "Транзакция не найдена или уже обработана: $externalPaymentId"
}

/**
 * Проверяет, что сумма платежа [amount] положительна; иначе поднимает `PAYMENT_AMOUNT_NOT_POSITIVE`.
 * Общее правило для инициации платежа до обращения к шлюзу и для [Payments.create].
 */
context(raise: Raise<DomainError>)
fun ensurePaymentAmountPositive(amount: Money) {
    ensure(amount.isPositive) {
        CommonDomainError("PAYMENT_AMOUNT_NOT_POSITIVE", "Сумма платежа должна быть положительной")
    }
}
