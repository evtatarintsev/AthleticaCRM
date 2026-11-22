package org.athletica.crm.read.widgets

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema
import org.athletica.crm.api.schemas.widgets.PublicScheduleResponse
import org.athletica.crm.api.schemas.widgets.PublicScheduleSessionSchema
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.SessionSql
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/**
 * Реализация [PublicScheduleView] двумя запросами: настройки виджета по ключу, затем занятия
 * филиала за период. Выключенные поля отсекаются `CASE` в SQL и не покидают базу.
 */
class DbPublicScheduleView : PublicScheduleView {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun schedule(query: PublicScheduleQuery): PublicScheduleResponse {
        val widget =
            tr
                .sql(
                    """
                    SELECT w.id, w.org_id, w.branch_id, w.show_coaches, w.show_hall, w.show_disciplines,
                           t.today, date_trunc('week', t.today)::date AS period_start,
                           t.today + w.horizon_days AS period_end
                    FROM schedule_widgets w
                    JOIN organizations o ON o.id = w.org_id
                    CROSS JOIN LATERAL (SELECT (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today) t
                    WHERE w.public_key = :key AND w.enabled
                    """.trimIndent(),
                )
                .bind("key", query.key)
                .bind("now", query.now)
                .firstOrNull { row -> row.toWidget() }
                ?: raise(CommonDomainError("WIDGET_UNAVAILABLE", Messages.WidgetUnavailable.localize(query.lang)))

        val from = maxOf(query.from ?: widget.periodStart, widget.periodStart)
        val to = minOf(query.to ?: widget.periodEnd, widget.periodEnd)
        val sessions =
            tr
                .sql(SESSIONS_SQL)
                .bind("widgetId", widget.id)
                .bind("orgId", widget.orgId)
                .bind("branchId", widget.branchId)
                .bind("from", from)
                .bind("to", to)
                .bind("showHall", widget.showHall)
                .bind("showDisciplines", widget.showDisciplines)
                .bind("showCoaches", widget.showCoaches)
                .list { row -> row.toSession() }
        return PublicScheduleResponse(today = widget.today, periodStart = widget.periodStart, periodEnd = widget.periodEnd, sessions = sessions)
    }

    /** Настройки виджета, нужные запросу занятий, и границы периода показа. */
    private data class WidgetRow(
        val id: Uuid,
        val orgId: Uuid,
        val branchId: Uuid,
        val showCoaches: Boolean,
        val showHall: Boolean,
        val showDisciplines: Boolean,
        val today: LocalDate,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
    )

    private fun Row.toWidget() =
        WidgetRow(
            id = asUuid("id"),
            orgId = asUuid("org_id"),
            branchId = asUuid("branch_id"),
            showCoaches = asBoolean("show_coaches"),
            showHall = asBoolean("show_hall"),
            showDisciplines = asBoolean("show_disciplines"),
            today = asLocalDate("today"),
            periodStart = asLocalDate("period_start"),
            periodEnd = asLocalDate("period_end"),
        )

    private fun Row.toSession() =
        PublicScheduleSessionSchema(
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            groupName = asString("group_name"),
            hall = asStringOrNull("hall"),
            disciplines = asStringOrNull("disciplines")?.let { Json.decodeFromString<List<String>>(it) },
            coaches = asStringOrNull("coaches")?.let { json -> Json.decodeFromString<List<ScheduleCoachSchema>>(json).map { it.name } },
            cancelled = asBoolean("cancelled"),
        )

    private companion object {
        /** Занятия филиала за период без скрытых и исключённых в виджете групп. */
        val SESSIONS_SQL =
            """
            SELECT s.date, s.start_time, s.end_time, g.name AS group_name,
                   s.status = 'cancelled' AS cancelled,
                   CASE WHEN :showHall THEN h.name END AS hall,
                   CASE WHEN :showDisciplines THEN
                       (SELECT COALESCE(jsonb_agg(d.name ORDER BY d.name), '[]'::jsonb)
                          FROM group_disciplines gd
                          JOIN disciplines d ON d.id = gd.discipline_id
                         WHERE gd.group_id = s.group_id)
                   END AS disciplines,
                   CASE WHEN :showCoaches THEN ${SessionSql.COACHES_JSON} END AS coaches
            FROM sessions s
            JOIN groups g ON g.id = s.group_id AND g.org_id = s.org_id AND g.branch_id = :branchId AND g.shown_in_widgets
            JOIN halls h ON h.id = s.hall_id
            WHERE s.org_id = :orgId AND s.date BETWEEN :from AND :to
              AND NOT EXISTS (SELECT 1 FROM schedule_widget_excluded_groups eg
                               WHERE eg.widget_id = :widgetId AND eg.group_id = s.group_id)
            ORDER BY s.date, s.start_time, g.name, s.id
            """.trimIndent()
    }
}
