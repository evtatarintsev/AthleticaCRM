package org.athletica.crm.api.schemas.payment

import kotlinx.serialization.Serializable

/**
 * Запрос на инициацию платежа через внешний шлюз.
 * [amount] — сумма в минорных единицах (копейки/центы).
 * [description] — назначение платежа, отображается пользователю на форме оплаты.
 */
@Serializable
data class InitiatePaymentRequest(
    val amount: Long,
    val description: String,
)
