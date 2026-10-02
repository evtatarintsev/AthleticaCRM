package org.athletica.crm.domain.attendance

import arrow.core.getOrElse
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/**
 * Состав занятия на стороне команд.
 *
 * Строка журнала создаётся лениво — при первой отметке или добавлении разового участника.
 * Поэтому состав запланированного занятия — строки журнала плюс клиенты с активной на дату
 * занятия записью в группу, а состав проведённого или отменённого — только строки журнала:
 * закрыть занятие можно лишь при отмеченных всех, так что к закрытию строка есть у каждого
 * участника, и переход на чтение строк замораживает состав без копирования данных.
 */
object SessionRoster {
    /** Участники занятия [sessionId] группы [groupId] на дату [date] со статусом [status], по клиенту. */
    context(tr: Transaction)
    suspend fun load(
        sessionId: SessionId,
        groupId: GroupId,
        date: LocalDate,
        status: SessionStatus,
    ): List<JournalParticipant> =
        tr
            .sql(
                """
                SELECT a.client_id, a.kind::text AS kind, a.presence::text AS presence,
                       COALESCE((SELECT string_agg(l.label_id::text, ',')
                                   FROM session_attendance_labels l
                                  WHERE l.attendance_id = a.id), '') AS label_ids
                  FROM session_attendance a
                 WHERE a.session_id = :sessionId
                UNION ALL
                SELECT e.client_id, 'regular', NULL, ''
                  FROM (SELECT DISTINCT client_id
                          FROM enrollments
                         WHERE group_id = :groupId
                           AND enrolled_at::date <= :date
                           AND (left_at IS NULL OR left_at::date >= :date)) e
                 WHERE :withEnrollments
                   AND NOT EXISTS (SELECT 1 FROM session_attendance a
                                    WHERE a.session_id = :sessionId AND a.client_id = e.client_id)
                 ORDER BY client_id
                """.trimIndent(),
            )
            .bind("sessionId", sessionId)
            .bind("groupId", groupId)
            .bind("date", date)
            .bind("withEnrollments", status == SessionStatus.SCHEDULED)
            .list { row ->
                JournalParticipant(
                    clientId = row.asUuid("client_id").toClientId(),
                    kind = participationKindOf(row.asString("kind")),
                    mark =
                        markOf(
                            presence = row.asStringOrNull("presence")?.let { AttendancePresence.valueOf(it.uppercase()) },
                            labelIds = row.asString("label_ids").split(',').filter { it.isNotEmpty() }.map { AttendanceLabelId(Uuid.parse(it)) },
                        ),
                )
            }

    /** Отметка, сохранённая в БД; нарушение инварианта в данных — ошибка программы, а не домена. */
    private fun markOf(presence: AttendancePresence?, labelIds: List<AttendanceLabelId>): AttendanceMark =
        AttendanceMark.from(presence, labelIds).getOrElse {
            throw IllegalStateException("Некорректная отметка в журнале: $presence $labelIds — ${it.message}")
        }
}

/** Значение enum-типа `attendance_kind` в БД. */
internal fun ParticipationKind.toDb(): String = name.lowercase()

/** [ParticipationKind] из значения enum-типа `attendance_kind` в БД. */
internal fun participationKindOf(db: String): ParticipationKind = ParticipationKind.valueOf(db.uppercase())
