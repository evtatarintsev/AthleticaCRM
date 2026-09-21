package org.athletica.crm.domain.sessions

/**
 * Единственное определение «занятия, которого не касался человек».
 *
 * Такое занятие принадлежит правилу расписания и управляется сверкой: создаётся,
 * обновляется и удаляется вместе с породившей его версией слота. Любой след
 * человеческого решения — отмена, завершение, перенос, свой состав тренеров,
 * заметка — выводит занятие из-под управления сверки навсегда.
 */
object UntouchedSessions {
    /** SQL-предикат нетронутости для таблицы `sessions` под псевдонимом [alias]. */
    fun predicate(alias: String): String =
        """
        $alias.origin_slot_id IS NOT NULL
        AND $alias.status = 'scheduled'
        AND $alias.is_rescheduled = false
        AND $alias.is_employee_assignment_overridden = false
        AND $alias.notes IS NULL
        """.trimIndent()
}
