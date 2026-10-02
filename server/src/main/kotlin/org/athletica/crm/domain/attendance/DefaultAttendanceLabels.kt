package org.athletica.crm.domain.attendance

import org.athletica.crm.core.Lang
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.i18n.LocalizationKey
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Предустановленный набор меток посещаемости, создаваемый каждой новой организации.
 * Дальше организация правит справочник сама: предустановленные метки ничем не отличаются
 * от созданных вручную.
 */
object DefaultAttendanceLabels {
    /** Название и применимость предустановленных меток в порядке справочника. */
    private val defaults: List<Pair<LocalizationKey, AttendanceLabelScope>> =
        listOf(
            Messages.DefaultAttendanceLabelLate to AttendanceLabelScope.PRESENT,
            Messages.DefaultAttendanceLabelLeftEarly to AttendanceLabelScope.PRESENT,
            Messages.DefaultAttendanceLabelSick to AttendanceLabelScope.ABSENT,
            Messages.DefaultAttendanceLabelExcused to AttendanceLabelScope.ABSENT,
            Messages.DefaultAttendanceLabelTruant to AttendanceLabelScope.ABSENT,
        )

    /** Создаёт предустановленные метки в организации [orgId] с названиями на языке [lang]. */
    context(tr: Transaction)
    suspend fun createFor(orgId: OrgId, lang: Lang) {
        defaults.forEachIndexed { index, (name, scope) ->
            tr
                .sql(
                    """
                    INSERT INTO attendance_labels (id, org_id, name, scope, position)
                    VALUES (:id, :orgId, :name, :scope::attendance_label_scope, :position)
                    """.trimIndent(),
                )
                .bind("id", AttendanceLabelId.new())
                .bind("orgId", orgId)
                .bind("name", name.localize(lang))
                .bind("scope", scope.toDb())
                .bind("position", index + 1)
                .execute()
        }
    }
}
