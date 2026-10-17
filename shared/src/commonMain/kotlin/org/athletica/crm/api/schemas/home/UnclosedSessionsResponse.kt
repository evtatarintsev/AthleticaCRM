package org.athletica.crm.api.schemas.home

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.SessionId

/** Запрос прошедших занятий с незакрытым журналом для виджета главной. */
@Serializable
data class UnclosedSessionsRequest(
    /** Максимум занятий в ответе. */
    val limit: Int = 10,
)

/** Прошедшие по времени занятия текущего филиала, журнал которых не закрыт. */
@Serializable
data class UnclosedSessionsResponse(
    /** Занятия от недавних к давним, не больше запрошенного лимита. */
    val sessions: List<UnclosedSessionItem>,
    /** Сколько всего таких занятий в филиале. */
    val total: Int,
)

/** Прошедшее занятие с незакрытым журналом. */
@Serializable
data class UnclosedSessionItem(
    /** Идентификатор занятия. */
    val sessionId: SessionId,
    /** Название группы. */
    val groupName: String,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала занятия. */
    val startTime: LocalTime,
    /** Время окончания занятия. */
    val endTime: LocalTime,
    /** Название зала. */
    val hallName: String,
)
