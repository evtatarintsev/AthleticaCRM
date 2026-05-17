package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.Serializable

/**
 * Результат разбора CSV: заголовки, образцы строк и уникальные значения по колонкам.
 * Никаких подсказок по маппингу сервер не делает — пользователь задаёт всё вручную.
 */
@Serializable
data class ClientImportParseResponse(
    /** Имя загруженного файла. Используется в note при заведении баланса. */
    val originalName: String,
    /** Общее количество строк-данных (без заголовка). */
    val totalRows: Int,
    /** Заголовки колонок в порядке появления в CSV. */
    val columns: List<String>,
    /** Образцы первых N строк (для предпросмотра в UI), каждая строка — значения по колонкам. */
    val sampleRows: List<List<String?>>,
    /**
     * Уникальные значения каждой колонки (до [UNIQUE_VALUES_LIMIT] значений на колонку).
     * Нужны UI для построения подтаблиц маппинга пола и источника.
     */
    val uniqueValuesPerColumn: Map<String, List<String>>,
) {
    companion object {
        const val UNIQUE_VALUES_LIMIT = 200
        const val SAMPLE_ROWS_LIMIT = 10
    }
}
