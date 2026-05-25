package org.athletica.crm.domain.payment

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction

/** Репозиторий платёжных транзакций. */
interface Payments {
    /**
     * Создаёт транзакцию в статусе PENDING.
     * Требует [EmployeeRequestContext]: платёж инициируется только аутентифицированным сотрудником.
     * [ctx.employeeId] автоматически используется как [Payment.createdBy].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
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
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun markAsPaid(gatewayName: String, externalPaymentId: String): Payment
}

/** Транзакция не найдена или уже обработана (статус != PENDING). */
class PaymentAlreadyProcessed(
    externalPaymentId: String,
) : DomainError {
    override val code = "PAYMENT_ALREADY_PROCESSED"
    override val message = "Транзакция не найдена или уже обработана: $externalPaymentId"
}
