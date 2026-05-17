package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.LeadSourceId

/**
 * Результат валидации (dryRun) или фактического импорта клиентов.
 * [rows] перечисляет статус каждой строки CSV (нумерация с 1, без учёта заголовка).
 */
@Serializable
data class ClientImportCommitResponse(
    /** Общее количество строк-данных в файле. */
    val totalRows: Int,
    /** Сколько клиентов успешно прошли валидацию (и были записаны, если dryRun=false). */
    val imported: Int,
    /** Сколько строк пропущено из-за ошибок валидации. */
    val skipped: Int,
    /** Подробный результат по каждой строке. */
    val rows: List<RowResult>,
    /** Источники, созданные в ходе импорта (только при dryRun=false). */
    val createdLeadSources: List<CreatedLeadSource> = emptyList(),
) {
    /** Статус обработки одной строки CSV. */
    @Serializable
    data class RowResult(
        /** Номер строки в файле, начиная с 1 (без учёта строки-заголовка). */
        val rowNumber: Int,
        /** Статус строки. */
        val status: Status,
        /** Локализованные тексты ошибок при [Status.ERROR]. */
        val errors: List<String> = emptyList(),
    )

    /** Один из возможных исходов обработки строки. */
    @Serializable
    enum class Status {
        /** Строка валидна и (если не dryRun) клиент создан. */
        OK,

        /** Строка содержит ошибки и в БД не попала. */
        ERROR,
    }

    /** Источник, заведённый в справочнике в ходе импорта. */
    @Serializable
    data class CreatedLeadSource(
        val id: LeadSourceId,
        val name: String,
    )
}
