package org.athletica.crm.api.schemas.attendancecontrol

import kotlinx.serialization.Serializable

/** Настройки раздела «Контроль посещаемости» организации. */
@Serializable
data class AttendanceControlSettingsResponse(
    /** Создавать ли задачу «Закрыть журнал», если журнал занятия не закрыт вовремя. */
    val overdueTaskEnabled: Boolean,
    /** Через сколько минут после окончания занятия по расписанию создаётся задача. */
    val overdueTaskDelayMinutes: Int,
)

/** Запрос на изменение настроек раздела «Контроль посещаемости». */
@Serializable
data class UpdateAttendanceControlSettingsRequest(
    /** Создавать ли задачу «Закрыть журнал», если журнал занятия не закрыт вовремя. */
    val overdueTaskEnabled: Boolean,
    /** Через сколько минут после окончания занятия по расписанию создаётся задача: от 15 минут до недели. */
    val overdueTaskDelayMinutes: Int,
)
