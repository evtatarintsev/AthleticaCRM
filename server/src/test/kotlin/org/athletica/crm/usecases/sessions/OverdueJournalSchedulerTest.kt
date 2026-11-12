package org.athletica.crm.usecases.sessions

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.SessionsFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.read.sessions.dueJournalSessions
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * DB-тесты задачи «Закрыть журнал»: окно из настроек, часовой пояс организации,
 * однократность и отсечение давних занятий. Организация в `Asia/Yekaterinburg` (UTC+5):
 * занятие 17.10 18:00–19:00 по местному времени заканчивается в 14:00 UTC.
 */
class OverdueJournalSchedulerTest {
    private val fixture = SessionsFixture(timezone = "Asia/Yekaterinburg")
    private val scheduler = OverdueJournalScheduler(TestPostgres.db, DbTasks(), fixture.clock)
    private val day = LocalDate(2026, 10, 17)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    /** Задача организации: заголовок, описание, исполнитель и автор. */
    private data class TaskRow(
        val title: String,
        val description: String,
        val assigneeId: String?,
        val createdBy: String?,
    )

    private suspend fun tasks(): List<TaskRow> =
        orThrow {
            TestPostgres.db
                .sql("SELECT title, description, assignee_id::text AS a, created_by::text AS c FROM tasks WHERE org_id = :orgId")
                .bind("orgId", fixture.orgId)
                .list {
                    TaskRow(
                        it.get("title", String::class.java)!!,
                        it.get("description", String::class.java)!!,
                        it.get("a", String::class.java),
                        it.get("c", String::class.java),
                    )
                }
        }

    private suspend fun at(instant: String) {
        fixture.now = Instant.parse(instant)
        scheduler.processDue()
    }

    @Test
    fun `резервирование перепроверяет перенос выключение и новое окно после выборки`() =
        orThrow {
            runTest {
                val id = fixture.insertSession(fixture.insertGroup(), day)
                fixture.now = Instant.parse("2026-10-17T16:00:00Z")
                val candidate =
                    TestPostgres.db.transaction {
                        dueJournalSessions(fixture.now, day, 1.days).single()
                    }
                TestPostgres.db.sql("UPDATE sessions SET end_time = '20:00' WHERE id = :id").bind("id", id).execute()
                scheduler.processCandidate(candidate)
                assertTrue(tasks().isEmpty())

                TestPostgres.db.sql("UPDATE sessions SET end_time = '19:00' WHERE id = :id").bind("id", id).execute()
                fixture.saveSettings(enabled = false, delayMinutes = 120)
                scheduler.processCandidate(candidate)
                assertTrue(tasks().isEmpty())

                TestPostgres.db.sql("UPDATE attendance_control_settings SET overdue_task_enabled = true, overdue_task_delay_minutes = 180 WHERE org_id = :id")
                    .bind("id", fixture.orgId)
                    .execute()
                scheduler.processCandidate(candidate)
                assertTrue(tasks().isEmpty())

                fixture.now = Instant.parse("2026-10-17T17:00:00Z")
                val first = async { scheduler.processDue() }
                val second = async { scheduler.processDue() }
                first.await()
                second.await()
                assertEquals(1, tasks().size)
            }
        }

    @Test
    fun `задача без исполнителя от системы после окна`() =
        runTest {
            fixture.insertSession(fixture.insertGroup("Йога"), day)

            at("2026-10-17T16:00:00Z")

            val task = tasks().single()
            assertEquals("Закрыть журнал: Йога", task.title)
            assertTrue(task.description.contains("«Йога» 17.10.2026 18:00–19:00"), task.description)
            assertEquals(null, task.assigneeId)
            assertEquals(null, task.createdBy)
        }

    @Test
    fun `до истечения окна задачи нет`() =
        runTest {
            fixture.insertSession(fixture.insertGroup(), day)

            at("2026-10-17T15:30:00Z")

            assertTrue(tasks().isEmpty())
        }

    @Test
    fun `по проведённому и отменённому занятию задачи нет`() =
        runTest {
            val group = fixture.insertGroup()
            fixture.insertSession(group, day, status = "completed")
            fixture.insertSession(group, day, status = "cancelled")

            at("2026-10-17T17:00:00Z")

            assertTrue(tasks().isEmpty())
        }

    @Test
    fun `повторный проход не создаёт вторую задачу даже после удаления первой`() =
        orThrow {
            runTest {
                fixture.insertSession(fixture.insertGroup(), day)

                at("2026-10-17T16:00:00Z")
                at("2026-10-17T16:05:00Z")
                assertEquals(1, tasks().size)

                TestPostgres.db.sql("DELETE FROM tasks WHERE org_id = :orgId").bind("orgId", fixture.orgId).execute()
                at("2026-10-17T16:10:00Z")
                assertTrue(tasks().isEmpty())
            }
        }

    @Test
    fun `выключенная задача не создаётся`() =
        runTest {
            fixture.saveSettings(enabled = false, delayMinutes = 120)
            fixture.insertSession(fixture.insertGroup(), day)

            at("2026-10-17T17:00:00Z")

            assertTrue(tasks().isEmpty())
        }

    @Test
    fun `окно берётся из настроек`() =
        runTest {
            fixture.saveSettings(enabled = true, delayMinutes = 30)
            fixture.insertSession(fixture.insertGroup(), day)

            at("2026-10-17T14:29:00Z")
            assertTrue(tasks().isEmpty())
            at("2026-10-17T14:30:00Z")
            assertEquals(1, tasks().size)
        }

    @Test
    fun `давнее незакрытое занятие задачу не получает`() =
        runTest {
            val group = fixture.insertGroup()
            fixture.insertSession(group, LocalDate(2026, 10, 10))
            fixture.insertSession(group, LocalDate(2026, 10, 16))

            at("2026-10-17T15:00:00Z")

            val task = tasks().single()
            assertTrue(task.description.contains("16.10.2026"), task.description)
        }
}
