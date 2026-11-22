package org.athletica.crm.domain.telegramgateway

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError

/** Одноразовый код для отправки через Telegram Gateway: от 4 до 8 цифр. Создаётся только через [from]. */
@JvmInline
value class GatewayCode private constructor(val value: String) {
    /** Код не выводится в журналы при случайной интерполяции. */
    override fun toString(): String = "GatewayCode(***)"

    companion object {
        private val REGEX = Regex("[0-9]{4,8}")

        /** Код из [value]; `INVALID_GATEWAY_CODE`, если это не 4–8 цифр. */
        context(raise: Raise<DomainError>)
        fun from(value: String): GatewayCode {
            if (!value.matches(REGEX)) {
                raise(CommonDomainError("INVALID_GATEWAY_CODE", "Код должен состоять из 4–8 цифр"))
            }
            return GatewayCode(value)
        }
    }
}

/** Срок жизни сообщения с кодом в Telegram: от 30 до 3600 секунд. Создаётся только через [from]. */
@JvmInline
value class GatewayCodeTtl private constructor(val seconds: Int) {
    companion object {
        /** Допустимый диапазон срока в секундах. */
        val RANGE = 30..3600

        /** Срок в [seconds] секунд; `INVALID_GATEWAY_CODE_TTL`, если он вне [RANGE]. */
        context(raise: Raise<DomainError>)
        fun from(seconds: Int): GatewayCodeTtl {
            if (seconds !in RANGE) {
                raise(CommonDomainError("INVALID_GATEWAY_CODE_TTL", "Срок жизни кода — от 30 до 3600 секунд"))
            }
            return GatewayCodeTtl(seconds)
        }
    }
}
