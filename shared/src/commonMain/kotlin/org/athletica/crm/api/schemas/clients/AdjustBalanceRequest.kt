package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.money.Money

/** Запрос на административную корректировку баланса клиента. */
@Serializable
data class AdjustBalanceRequest(
    /** Идентификатор клиента. */
    val clientId: ClientId,
    /**
     * Сумма корректировки.
     * Положительная — пополнение счёта (admin_credit), отрицательная — списание (admin_debit).
     * Не может быть равна нулю; валюта должна совпадать с валютой организации.
     */
    val amount: Money,
    /** Обязательный комментарий к операции. */
    val note: String,
)
