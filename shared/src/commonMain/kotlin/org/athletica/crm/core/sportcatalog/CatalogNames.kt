package org.athletica.crm.core.sportcatalog

import org.athletica.crm.core.Lang

/**
 * Названия элемента каталога на каждом поддерживаемом языке.
 * Название на языке — принятое в организациях этого языка, а не обязательно перевод:
 * русское название может быть записано латиницей.
 */
data class CatalogNames(
    /** Название для русскоязычных организаций. */
    val ru: String,
    /** Название для англоязычных организаций. */
    val en: String,
) {
    /** Название на языке [lang]. */
    fun of(lang: Lang): String =
        when (lang) {
            Lang.RU -> ru
            Lang.EN -> en
        }
}
