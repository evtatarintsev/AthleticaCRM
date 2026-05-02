package org.athletica.crm.api.schemas.home

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.SessionId

/** Ответ с занятиями на текущую дату. */
@Serializable
data class TodaySessionsResponse(
    /** Дата, за которую возвращены занятия. */
    val date: LocalDate,
    /** Список занятий, отсортированных по времени начала. */
    val sessions: List<TodaySessionItem>,
)

/** Элемент занятия для главной страницы. */
@Serializable
data class TodaySessionItem(
    /** Идентификатор занятия. */
    val sessionId: SessionId,
    /** Название группы. */
    val groupName: String,
    /** Время начала занятия. */
    val startTime: LocalTime,
    /** Время окончания занятия. */
    val endTime: LocalTime,
    /** Название зала. */
    val hallName: String,
)
