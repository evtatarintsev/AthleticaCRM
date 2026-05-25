package org.athletica.crm.domain.payment

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/** Реализация [Payments] поверх R2DBC. */
class DbPayments : Payments {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(
        gatewayName: String,
        externalPaymentId: String,
        amount: Money,
        description: String,
        confirmationUrl: String,
    ): Payment {
        val id = Uuid.generateV7()
        return tr
            .sql(
                """
                INSERT INTO payment_transactions
                    (id, org_id, gateway_name, external_payment_id, amount, currency,
                     status, description, created_by, confirmation_url)
                VALUES
                    (:id, :orgId, :gatewayName, :externalPaymentId, :amount, :currency,
                     'pending', :description, :createdBy, :confirmationUrl)
                RETURNING *
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("gatewayName", gatewayName)
            .bind("externalPaymentId", externalPaymentId)
            .bind("amount", amount)
            .bind("currency", amount.currency.name)
            .bind("description", description)
            .bind("createdBy", ctx.employeeId)
            .bind("confirmationUrl", confirmationUrl)
            .firstOrNull { row -> row.toPayment() }!!
    }

    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markAsPaid(gatewayName: String, externalPaymentId: String): Payment {
        val payment =
            tr
                .sql(
                    """
                    UPDATE payment_transactions
                    SET    status = 'paid', confirmed_at = NOW()
                    WHERE  gateway_name = :gatewayName
                      AND  external_payment_id = :externalPaymentId
                      AND  status = 'pending'
                    RETURNING *
                    """.trimIndent(),
                )
                .bind("gatewayName", gatewayName)
                .bind("externalPaymentId", externalPaymentId)
                .firstOrNull { row -> row.toPayment() }

        if (payment == null) {
            raise(PaymentAlreadyProcessed(externalPaymentId))
        }

        return payment
    }
}

private fun Row.toPayment(): Payment {
    val currency = Currency.valueOf(asString("currency"))
    return Payment(
        id = asUuid("id"),
        orgId = asUuid("org_id").toOrgId(),
        gatewayName = asString("gateway_name"),
        externalPaymentId = asString("external_payment_id"),
        amount = asMoney("amount", currency),
        status = PaymentStatus.valueOf(asString("status").uppercase()),
        description = asString("description"),
        createdBy = asUuid("created_by").toEmployeeId(),
        createdAt = asInstant("created_at"),
        confirmedAt = asInstantOrNull("confirmed_at"),
        confirmationUrl = asStringOrNull("confirmation_url"),
    )
}
