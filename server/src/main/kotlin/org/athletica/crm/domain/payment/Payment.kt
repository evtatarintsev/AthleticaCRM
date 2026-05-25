package org.athletica.crm.domain.payment

import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Money
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Платёжная транзакция. Жизненный цикл: PENDING → PAID | CANCELLED. */
data class Payment(
    /** Внутренний идентификатор транзакции (UUID v7). */
    val id: Uuid,
    /** Организация, инициировавшая платёж. */
    val orgId: OrgId,
    /** Название шлюза: "yookassa", "cloudpayments" и т.п. */
    val gatewayName: String,
    /** ID платежа на стороне шлюза; используется для сопоставления с webhook. */
    val externalPaymentId: String,
    /** Сумма платежа. */
    val amount: Money,
    /** Текущий статус транзакции. */
    val status: PaymentStatus,
    /** Назначение платежа, отображается пользователю на форме оплаты. */
    val description: String,
    /** Сотрудник, инициировавший платёж. */
    val createdBy: EmployeeId,
    /** Время создания транзакции. */
    val createdAt: Instant,
    /** Время подтверждения оплаты (из webhook); null пока не оплачен. */
    val confirmedAt: Instant?,
    /** URL формы оплаты шлюза для редиректа пользователя. */
    val confirmationUrl: String?,
)

/** Статус платёжной транзакции. */
enum class PaymentStatus {
    /** Ожидает оплаты пользователем. */
    PENDING,

    /** Успешно оплачен, средства зачислены на баланс организации. */
    PAID,

    /** Отменён пользователем или по таймауту. */
    CANCELLED,
}
