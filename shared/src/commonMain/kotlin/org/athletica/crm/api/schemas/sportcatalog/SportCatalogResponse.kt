package org.athletica.crm.api.schemas.sportcatalog

import kotlinx.serialization.Serializable

/** Каталог видов спорта целиком, со всеми языками и синонимами; не зависит от организации и языка запроса. */
@Serializable
data class SportCatalogResponse(
    /** Виды спорта в порядке показа. */
    val sports: List<SportCatalogSportSchema>,
)

/** Вид спорта каталога. */
@Serializable
data class SportCatalogSportSchema(
    /** Стабильный ключ, уникальный в каталоге. */
    val key: String,
    /** Названия вида спорта на поддерживаемых языках. */
    val names: SportCatalogNamesSchema,
    /** Дисциплины вида спорта в порядке показа. */
    val disciplines: List<SportCatalogDisciplineSchema>,
)

/** Дисциплина каталога. */
@Serializable
data class SportCatalogDisciplineSchema(
    /** Стабильный ключ, уникальный в каталоге. */
    val key: String,
    /** Названия дисциплины на поддерживаемых языках. */
    val names: SportCatalogNamesSchema,
    /** Другие написания на любом языке, только для поиска. */
    val aliases: List<String>,
)

/** Названия элемента каталога по языкам. */
@Serializable
data class SportCatalogNamesSchema(
    /** Название для русскоязычного интерфейса. */
    val ru: String,
    /** Название для англоязычного интерфейса. */
    val en: String,
)
