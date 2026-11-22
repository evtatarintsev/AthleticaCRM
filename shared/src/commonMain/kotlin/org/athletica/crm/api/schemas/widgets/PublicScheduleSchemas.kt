package org.athletica.crm.api.schemas.widgets

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** Запрос публичного расписания виджета; период вне показа обрезается сервером. */
@Serializable
data class PublicScheduleRequest(
    /** Публичный ключ виджета. */
    val key: String,
    /** Первый день периода включительно; `null` — начало периода показа. */
    val from: LocalDate? = null,
    /** Последний день периода включительно; `null` — конец периода показа. */
    val to: LocalDate? = null,
)

/** Публичное расписание виджета: без внутренних идентификаторов и служебных признаков. */
@Serializable
data class PublicScheduleResponse(
    /** Сегодняшняя дата в часовом поясе организации. */
    val today: LocalDate,
    /** Первый день периода показа (понедельник текущей недели). */
    val periodStart: LocalDate,
    /** Последний день периода показа (сегодня плюс горизонт виджета). */
    val periodEnd: LocalDate,
    /** Занятия запрошенного периода по дате и времени. */
    val sessions: List<PublicScheduleSessionSchema>,
)

/** Карточка занятия в публичном расписании; `null` у поля — поле выключено в виджете. */
@Serializable
data class PublicScheduleSessionSchema(
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
    /** Время окончания. */
    val endTime: LocalTime,
    /** Название группы. */
    val groupName: String,
    /** Название зала; `null` — зал скрыт в виджете. */
    val hall: String?,
    /** Дисциплины группы; `null` — дисциплины скрыты в виджете. */
    val disciplines: List<String>?,
    /** Имена тренеров с учётом замены на занятии; `null` — тренеры скрыты в виджете. */
    val coaches: List<String>?,
    /** Занятие отменено. */
    val cancelled: Boolean,
)
