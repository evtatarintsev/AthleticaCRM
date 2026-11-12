package org.athletica.crm.routes

import org.athletica.crm.api.schemas.payment.InitiatePaymentRequest
import org.athletica.crm.api.schemas.payment.PaymentUrlResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.Payments
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.payments.initiatePayment

/**
 * Маршруты управления платежами (требуют JWT-авторизации).
 * POST /payments/initiate — создание платежа и получение URL формы оплаты ЮKassa.
 */
context(db: Database, audit: AuditLog)
fun RouteWithContext.paymentRoutes(
    payments: Payments,
    paymentGateway: PaymentGateway,
) {
    post<InitiatePaymentRequest, PaymentUrlResponse>("/payments/initiate") { request ->
        initiatePayment(db, audit, payments, paymentGateway, Money(request.amount, contextOf<EmployeeRequestContext>().currency), request.description)
    }
}
