package org.athletica.crm.api.schemas.payment

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Ответ на инициацию платежа.
 * [paymentId] — внутренний ID транзакции (UUID v7); используется для отслеживания статуса.
 * [confirmationUrl] — URL формы оплаты ЮKassa; фронтенд редиректит пользователя на этот адрес.
 */
@Serializable
data class PaymentUrlResponse(
    val paymentId: Uuid,
    val confirmationUrl: String,
)
