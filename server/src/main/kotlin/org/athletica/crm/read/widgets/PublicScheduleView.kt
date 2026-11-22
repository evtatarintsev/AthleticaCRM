package org.athletica.crm.read.widgets

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.widgets.PublicScheduleResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Публичное расписание виджета для посетителей без входа в CRM, поэтому без контекста сотрудника:
 * организация и филиал определяются по ключу. Неизвестный, выключенный и удалённый виджет дают
 * одну и ту же ошибку `WIDGET_UNAVAILABLE`.
 */
interface PublicScheduleView {
    /** Возвращает занятия виджета за запрошенный период, обрезанный до периода показа. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun schedule(query: PublicScheduleQuery): PublicScheduleResponse
}

/** Параметры публичного расписания; [now] задаёт «сегодня» организации, [lang] — язык сообщения об ошибке. */
data class PublicScheduleQuery(
    /** Публичный ключ виджета. */
    val key: String,
    /** Первый запрошенный день; `null` — начало периода показа. */
    val from: LocalDate?,
    /** Последний запрошенный день; `null` — конец периода показа. */
    val to: LocalDate?,
    /** Текущий момент. */
    val now: Instant,
    /** Язык сообщения об ошибке. */
    val lang: Lang,
)
