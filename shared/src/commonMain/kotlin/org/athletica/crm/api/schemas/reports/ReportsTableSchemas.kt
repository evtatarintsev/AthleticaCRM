package org.athletica.crm.api.schemas.reports

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.api.schemas.export.ExportFormat
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.money.Money
import kotlin.math.abs

/** Значение показателя отчёта (дискриминатор `type`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ReportValue

/** Денежная сумма. */
@Serializable
@SerialName("amount")
data class AmountReportValue(
    /** Сумма. */
    val amount: Money,
) : ReportValue

/** Количество (клиентов, занятий, продаж). */
@Serializable
@SerialName("count")
data class CountReportValue(
    /** Количество. */
    val count: Long,
) : ReportValue

/** Доля: `0.82` — 82%. */
@Serializable
@SerialName("percent")
data class PercentReportValue(
    /** Доля единицы. */
    val share: Double,
) : ReportValue

/** Какое изменение показателя к лучшему. */
@Serializable
enum class ReportBetter {
    /** Рост — к лучшему (выручка, посещаемость). */
    @SerialName("up")
    UP,

    /** Снижение — к лучшему (долги, отток). */
    @SerialName("down")
    DOWN,
}

/** Строка таблицы показателей: значение за период и за прошлый период. */
@Serializable
data class ReportsTableRow(
    /** Отчёт, к которому относится показатель (`revenue`, `debts`, …). */
    val reportId: String,
    /** Название показателя на языке сотрудника. */
    val label: String,
    /** Значение за период. */
    val value: ReportValue,
    /** Значение за прошлый период; `null` — данных за прошлый период нет. */
    val base: ReportValue?,
    /** Какое изменение к лучшему. */
    val better: ReportBetter,
)

/** Таблица показателей отчётов за период [period] в сравнении с [basePeriod]. */
@Serializable
data class ReportsTableResponse(
    /** Даты периода. */
    val period: DateRange,
    /** Даты прошлого периода. */
    val basePeriod: DateRange,
    /** Строки показателей, доступные сотруднику. */
    val rows: List<ReportsTableRow>,
)

/** Запрос выгрузки таблицы показателей: фильтр [filter] и формат файла [format]. */
@Serializable
data class ReportsTableExportRequest(
    /** Период и филиал. */
    val filter: ReportFilter = ReportFilter(),
    /** Формат файла. */
    val format: ExportFormat = ExportFormat.CSV,
)

/**
 * Изменение показателя [value] к прошлому значению [base] — общее правило для веба и файла:
 * для сумм и количеств — относительное к модулю прошлого значения (`0.1` — рост на 10%; при
 * отрицательном прошлом значении рост остаётся положительным), для долей — разница в долях
 * (`0.04` — на 4 процентных пункта). `null` — сравнивать не с чем (нет прошлого значения,
 * оно нулевое или другого вида).
 */
fun reportChange(
    value: ReportValue,
    base: ReportValue?,
): Double? =
    when {
        value is AmountReportValue && base is AmountReportValue && base.amount.minorUnits != 0L ->
            (value.amount.minorUnits - base.amount.minorUnits).toDouble() / abs(base.amount.minorUnits)
        value is CountReportValue && base is CountReportValue && base.count != 0L -> (value.count - base.count).toDouble() / abs(base.count)
        value is PercentReportValue && base is PercentReportValue -> value.share - base.share
        else -> null
    }
