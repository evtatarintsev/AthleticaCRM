package org.athletica.crm.demo

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/**
 * Сдвиг технических времён (`created_at` и подобных) в день истории.
 *
 * Доменные операции не принимают время создания: его ставит `DEFAULT now()`, `clock_timestamp()`
 * или часы приложения, то есть момент наполнения. Строки, записанные за текущий день симуляции,
 * ещё датированы не раньше начала транзакции, а строки прошлых дней уже сдвинуты в прошлое,
 * поэтому отбор `>= transaction_timestamp()` точен без учёта идентификаторов.
 * Деловые даты (продажи, оплаты, занятия) задают сами доменные операции и здесь не меняются.
 * Главная книга (`ledger_transactions`) неизменяема триггером, поэтому её время записи не сдвигается:
 * в ней значима дата операции `occurred_on`.
 */
internal object DemoTimestamps {
    /** Колонки технических времён в таблицах с `org_id`. */
    private val ORG_COLUMNS =
        listOf(
            "branches" to "created_at",
            "halls" to "created_at",
            "roles" to "created_at",
            "employees" to "joined_at",
            "disciplines" to "created_at",
            "lead_sources" to "created_at",
            "custom_field_definitions" to "created_at",
            "subscription_tariffs" to "created_at",
            "attendance_labels" to "created_at",
            "clients" to "created_at",
            "client_contacts" to "created_at",
            "client_notes" to "created_at",
            "groups" to "created_at",
            "schedule_slots" to "created_at",
            "sessions" to "created_at",
            "sessions" to "completed_at",
            "sessions" to "cancelled_at",
            "sales" to "created_at",
            "memberships" to "created_at",
            "membership_events" to "occurred_at",
            "tasks" to "created_at",
            "tasks" to "updated_at",
            "tasks" to "completed_at",
            "notifications" to "created_at",
            "audit_logs" to "created_at",
            "domain_events" to "created_at",
        )

    /** Колонки таблиц без `org_id`: таблица, колонка, родительская таблица и ссылка на неё. */
    private val CHILD_COLUMNS =
        listOf(
            ChildColumn("session_attendance", "created_at", "sessions", "session_id"),
            ChildColumn("session_attendance", "marked_at", "sessions", "session_id"),
            ChildColumn("enrollments", "enrolled_at", "groups", "group_id"),
            ChildColumn("enrollments", "left_at", "groups", "group_id"),
        )

    /** Таблицы и колонки, которые сдвигает [shift]; по ним тест проверяет отсутствие момента наполнения. */
    val columns: List<Pair<String, String>> = ORG_COLUMNS + CHILD_COLUMNS.map { it.table to it.column }

    /**
     * Сдвигает технические времена строк организации, записанных за день [day], на полдень этого дня
     * в часовом поясе организации; для сегодняшнего дня — не позже чем за минуту до начала транзакции.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun shift(day: LocalDate) {
        // shortcut: полдень по местному времени; для поясов дальше UTC+12 дата в UTC сдвинется на день — вынести время в параметр, если такие появятся.
        ORG_COLUMNS.forEach { (table, column) ->
            tr.sql("UPDATE $table SET $column = $DAY_TS WHERE org_id = :org AND $column >= transaction_timestamp()")
                .bind("org", ctx.orgId)
                .bind("day", day)
                .execute()
        }
        CHILD_COLUMNS.forEach { (table, column, parent, reference) ->
            tr.sql(
                """
                UPDATE $table c SET $column = $DAY_TS FROM $parent p
                WHERE p.id = c.$reference AND p.org_id = :org AND c.$column >= transaction_timestamp()
                """.trimIndent(),
            )
                .bind("org", ctx.orgId)
                .bind("day", day)
                .execute()
        }
    }

    /** Полдень дня `:day` в часовом поясе организации `:org`, не позже начала транзакции. */
    private const val DAY_TS =
        "(SELECT LEAST((:day::date + time '12:00') AT TIME ZONE o.timezone, transaction_timestamp() - interval '1 minute') " +
            "FROM organizations o WHERE o.id = :org)"

    /** Колонка [column] таблицы [table], строки которой относятся к организации через [parent].[reference]. */
    private data class ChildColumn(
        /** Таблица со сдвигаемой колонкой. */
        val table: String,
        /** Сдвигаемая колонка. */
        val column: String,
        /** Родительская таблица с `org_id`. */
        val parent: String,
        /** Колонка ссылки на родительскую таблицу. */
        val reference: String,
    )
}
