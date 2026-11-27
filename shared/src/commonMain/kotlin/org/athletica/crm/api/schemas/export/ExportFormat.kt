package org.athletica.crm.api.schemas.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Формат файла выгрузки таблицы. */
@Serializable
enum class ExportFormat(
    /** Расширение имени файла и значение параметра запроса `format`. */
    val extension: String,
) {
    /** CSV в UTF-8 с BOM. */
    @SerialName("csv")
    CSV("csv"),

    /** Книга Excel. */
    @SerialName("xlsx")
    XLSX("xlsx"),
    ;

    companion object {
        /** Формат по значению параметра запроса [value]; неизвестное или пустое значение — CSV. */
        fun byExtension(value: String?): ExportFormat = entries.firstOrNull { it.extension == value?.lowercase() } ?: CSV
    }
}
