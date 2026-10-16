package org.athletica.crm.domain.clientcontacts

/**
 * Номер телефона, приведённый к виду для сравнения: только цифры, а российский номер из 11 цифр
 * с ведущей `8` записан с ведущей `7`. Так `8 (900) 123-45-67` и `+79001234567` совпадают.
 * Создаётся только через [from]: пустой после нормализации номер непредставим.
 */
@JvmInline
value class PhoneDigits private constructor(val value: String) {
    companion object {
        /** Нормализует [raw]; `null`, если в строке нет ни одной цифры. */
        fun from(raw: String): PhoneDigits? {
            val digits = raw.filter { it in '0'..'9' }
            return when {
                digits.isEmpty() -> null
                digits.length == 11 && digits.startsWith('8') -> PhoneDigits("7" + digits.drop(1))
                else -> PhoneDigits(digits)
            }
        }
    }
}
