package org.athletica.crm.api.schemas.reports

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.BranchId

/** Пункт выбора периода отчёта. */
@Serializable
enum class ReportPeriodKind {
    /** С 1-го числа текущего месяца по сегодня. */
    @SerialName("month")
    MONTH,

    /** Весь прошлый месяц. */
    @SerialName("last-month")
    LAST_MONTH,

    /** Сегодня и 29 дней до. */
    @SerialName("30d")
    LAST_30_DAYS,

    /** С начала квартала по сегодня. */
    @SerialName("quarter")
    QUARTER,

    /** Свой период `from` … `to`. */
    @SerialName("custom")
    CUSTOM,
}

/** С чем сравнивать свой период. */
@Serializable
enum class ReportCompareMode {
    /** Те же дни вплотную перед периодом. */
    @SerialName("days-before")
    DAYS_BEFORE,

    /** Тот же отрезок месяцем раньше. */
    @SerialName("month-before")
    MONTH_BEFORE,
}

/** Выбор пользователя для отчётов: период, режим сравнения своего периода и филиал. */
@Serializable
data class ReportFilter(
    /** Пункт выбора периода. */
    val period: ReportPeriodKind = ReportPeriodKind.MONTH,
    /** Начало своего периода; только для [ReportPeriodKind.CUSTOM]. */
    val from: LocalDate? = null,
    /** Конец своего периода; только для [ReportPeriodKind.CUSTOM]. */
    val to: LocalDate? = null,
    /** С чем сравнивать свой период; для остальных пунктов не используется. */
    val compare: ReportCompareMode = ReportCompareMode.DAYS_BEFORE,
    /** Филиал; `null` — все доступные сотруднику филиалы. */
    val branchId: BranchId? = null,
)
