package org.athletica.crm.storage.sql

import org.athletica.crm.storage.QueryBuilder
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Правило срока закрытия журнала занятия — выражения над `sessions s`, `organizations o`
 * и `attendance_control_settings acs`. Одно место для создания задач по незакрытому журналу
 * (`OverdueJournalTask`) и выборки занятий планировщика (`dueJournalSessions`);
 * пакет нейтральный, не зависит ни от `domain/`, ни от `read/`.
 * Параметры привязывает [bindJournalWindow].
 */
object JournalWindowSql {
    /** Окно закрытия журнала по умолчанию в минутах, если организация его не настраивала. */
    const val DEFAULT_DELAY_MINUTES = 120

    /** Создавать ли задачу по незакрытому журналу, если организация это не настраивала. */
    const val DEFAULT_ENABLED = true

    /** Срок закрытия журнала: конец занятия в часовом поясе организации плюс окно. */
    const val DEADLINE =
        "((s.date + s.end_time) AT TIME ZONE o.timezone) + make_interval(mins => COALESCE(acs.overdue_task_delay_minutes, :defaultDelay))"

    /** Включено ли создание задач по незакрытому журналу. */
    const val ENABLED = "COALESCE(acs.overdue_task_enabled, :defaultEnabled)"

    /** Срок `deadline` наступил в окне `(:now - :lookbackSeconds, :now]`. */
    const val DUE =
        "deadline <= CAST(:now AS timestamptz) AND deadline > CAST(:now AS timestamptz) - make_interval(secs => :lookbackSeconds)"
}

/** Привязывает параметры [JournalWindowSql]: момент [now] и глубину просмотра [lookback]. */
fun QueryBuilder.bindJournalWindow(now: Instant, lookback: Duration) =
    bind("defaultDelay", JournalWindowSql.DEFAULT_DELAY_MINUTES)
        .bind("defaultEnabled", JournalWindowSql.DEFAULT_ENABLED)
        .bind("now", now)
        .bind("lookbackSeconds", lookback.inWholeSeconds.toDouble())
