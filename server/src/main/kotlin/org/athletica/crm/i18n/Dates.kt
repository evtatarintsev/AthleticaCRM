package org.athletica.crm.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.Lang
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Шаблон даты для русского языка: `12.10.2026`. */
private val RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"))

/** Шаблон даты для английского языка: `Oct 12, 2026`. */
private val EN_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/** Дата в привычном для языка [lang] формате, для текстов, которые читает человек. */
fun LocalDate.localized(lang: Lang): String =
    when (lang) {
        Lang.RU -> toJavaLocalDate().format(RU_DATE)
        Lang.EN -> toJavaLocalDate().format(EN_DATE)
    }
