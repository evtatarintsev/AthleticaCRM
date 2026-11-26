package org.athletica.crm.domain.clientapp.codes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import java.security.SecureRandom

/** Одноразовый код входа в кабинет клиента: ровно 6 цифр. Создаётся только через [from]. */
@JvmInline
value class VerificationCode private constructor(val value: String) {
    /** Код не выводится в журналы при случайной интерполяции. */
    override fun toString(): String = "VerificationCode(***)"

    companion object {
        private val REGEX = Regex("[0-9]{6}")
        private val random = SecureRandom()

        /** Новый случайный код из криптостойкого генератора. */
        fun random(): VerificationCode = VerificationCode(random.nextInt(1_000_000).toString().padStart(6, '0'))

        /** Код из [value]; `INVALID_VERIFICATION_CODE`, если это не 6 цифр. */
        context(raise: Raise<DomainError>)
        fun from(value: String): VerificationCode {
            if (!value.matches(REGEX)) {
                raise(CommonDomainError("INVALID_VERIFICATION_CODE", "Код должен состоять из 6 цифр"))
            }
            return VerificationCode(value)
        }
    }
}
