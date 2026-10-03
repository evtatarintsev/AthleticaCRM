package org.athletica.crm.domain.leadSource

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages

/**
 * Пояснение к источнику привлечения: что относится к этому источнику.
 * Хранится без пробелов по краям и не длиннее [MAX_LENGTH] символов; отсутствие пояснения — [EMPTY].
 * Конструктор приватный: из пользовательского ввода пояснение создаётся только через [from].
 */
@JvmInline
value class LeadSourceDescription private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Наибольшая длина пояснения в символах. */
        const val MAX_LENGTH = 500

        /** Пустое пояснение. */
        val EMPTY = LeadSourceDescription("")

        /**
         * Пояснение из [raw] без пробелов по краям.
         * Ошибка `LEAD_SOURCE_DESCRIPTION_TOO_LONG` с сообщением на языке [lang], если оно длиннее [MAX_LENGTH].
         */
        fun from(raw: String, lang: Lang): Either<DomainError, LeadSourceDescription> {
            val trimmed = raw.trim()
            return if (trimmed.length > MAX_LENGTH) {
                CommonDomainError(
                    "LEAD_SOURCE_DESCRIPTION_TOO_LONG",
                    Messages.LeadSourceDescriptionTooLong.localize(lang, MAX_LENGTH),
                ).left()
            } else {
                LeadSourceDescription(trimmed).right()
            }
        }

        /** Пояснение из [raw], как [from] с языком текущего запроса. */
        context(ctx: RequestContext)
        fun from(raw: String): Either<DomainError, LeadSourceDescription> = from(raw, ctx.lang)

        /** Пояснение из колонки БД [value]: туда оно попадает только через [from], поэтому повторно не проверяется. */
        internal fun fromDb(value: String): LeadSourceDescription = LeadSourceDescription(value)
    }
}
