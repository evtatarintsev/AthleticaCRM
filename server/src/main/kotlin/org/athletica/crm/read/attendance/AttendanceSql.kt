package org.athletica.crm.read.attendance

/** SQL-фрагменты, общие для проекций посещаемости. */
internal object AttendanceSql {
    /**
     * Метки строки журнала под псевдонимом [alias] JSON-массивом в форме
     * [org.athletica.crm.api.schemas.attendance.AttendanceLabelSchema], в порядке справочника.
     * Для участника без строки журнала (`[alias].id IS NULL`) — пустой массив.
     */
    fun labelsJson(alias: String): String =
        """
        (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                    'id', l.id, 'name', l.name, 'scope', upper(l.scope::text),
                    'position', l.position, 'isArchived', l.archived_at IS NOT NULL
                ) ORDER BY l.position, l.name), '[]'::jsonb)
           FROM session_attendance_labels sal
           JOIN attendance_labels l ON l.id = sal.label_id
          WHERE sal.attendance_id = $alias.id)
        """.trimIndent()
}
