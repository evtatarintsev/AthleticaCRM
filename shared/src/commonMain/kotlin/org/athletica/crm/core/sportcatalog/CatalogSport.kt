package org.athletica.crm.core.sportcatalog

/** Вид спорта каталога — группа дисциплин. */
data class CatalogSport(
    /** Стабильный латинский ключ, уникальный в каталоге. */
    val key: String,
    /** Названия вида спорта на поддерживаемых языках. */
    val names: CatalogNames,
    /** Дисциплины вида спорта в порядке показа. */
    val disciplines: List<CatalogDiscipline>,
)

/** Дисциплина каталога. */
data class CatalogDiscipline(
    /** Стабильный латинский ключ, уникальный в каталоге. */
    val key: String,
    /** Названия дисциплины на поддерживаемых языках. */
    val names: CatalogNames,
    /** Другие написания на любом языке; используются только для поиска. */
    val aliases: List<String>,
)
