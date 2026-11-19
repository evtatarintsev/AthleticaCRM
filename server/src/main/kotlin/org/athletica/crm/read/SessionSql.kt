package org.athletica.crm.read

/** SQL-фрагменты по занятиям, общие для проекций разных разделов. */
internal object SessionSql {
    /**
     * Тренеры занятия под псевдонимом `s` с учётом переопределения состава на занятии,
     * по имени — JSON-массивом в форме [org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema].
     */
    val COACHES_JSON =
        """
        (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', e.id, 'name', e.name) ORDER BY e.name, e.id), '[]'::jsonb)
           FROM session_employees se
           JOIN employees e ON e.id = se.employee_id
          WHERE se.session_id = s.id)
        """.trimIndent()

    /** Состав тренеров занятия `s` изменён вручную на самом занятии. */
    const val COACHES_OVERRIDDEN = "s.is_employee_assignment_overridden"

    /** Дата, предписанная расписанием занятию `s`, если оно перенесено на другой день; иначе `NULL`. */
    const val RESCHEDULED_FROM = "CASE WHEN s.is_rescheduled AND s.origin_date <> s.date THEN s.origin_date END"

    /** Занятие `s` создано вручную, вне расписания группы. */
    const val IS_MANUAL = "s.origin_slot_id IS NULL"
}
