package org.athletica.crm.domain.events

import com.github.ajalt.clikt.command.test
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.admin.AdminDi
import org.athletica.crm.admin.athleticaCommand
import org.athletica.crm.core.entityids.OrgId
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Тесты повторов обработки доменных событий и перезапуска неуспешных. */
class DomainEventWorkerTest {
    private val orgId = OrgId.new()
    private var calls = 0
    private var failing = true
    private val bus =
        DomainEventBus().apply {
            on<SessionCancelled> { _, _ ->
                calls++
                if (failing) {
                    error("временный сбой")
                }
            }
        }
    private val worker = DomainEventWorker(TestPostgres.db, bus)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        calls = 0
        failing = true
    }

    private suspend fun publish(): Uuid {
        TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Org')").bind("id", orgId).execute()
        val id = Uuid.generateV7()
        TestPostgres.db
            .sql(
                """
                INSERT INTO domain_events (id, org_id, event_type, payload)
                VALUES (:id, :orgId, 'SessionCancelled', :payload::jsonb)
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind(
                "payload",
                """{"type":"SessionCancelled","sessionId":"${Uuid.generateV7()}","groupId":"${Uuid.generateV7()}",""" +
                    """"date":"2026-10-16","cancelledBy":"${Uuid.generateV7()}"}""",
            )
            .execute()
        return id
    }

    private data class Row(
        val retryCount: Int,
        val processed: Boolean,
        val failed: Boolean,
        val delayedSeconds: Long,
    )

    private suspend fun row(id: Uuid): Row =
        TestPostgres.db
            .sql(
                """
                SELECT retry_count, processed_at IS NOT NULL AS processed, failed_at IS NOT NULL AS failed,
                       EXTRACT(EPOCH FROM next_attempt_at - now())::bigint AS delayed
                FROM domain_events WHERE id = :id
                """.trimIndent(),
            )
            .bind("id", id)
            .firstOrNull {
                Row(
                    it.get("retry_count", Integer::class.java)!!.toInt(),
                    it.get("processed", java.lang.Boolean::class.java)!!.booleanValue(),
                    it.get("failed", java.lang.Boolean::class.java)!!.booleanValue(),
                    it.get("delayed", java.lang.Long::class.java)!!.toLong(),
                )
            }!!

    @Test
    fun `задержка растёт вдвое и ограничена часом`() {
        assertEquals(10.seconds, DomainEventWorker.retryDelay(0))
        assertEquals(20.seconds, DomainEventWorker.retryDelay(1))
        assertEquals(2560.seconds, DomainEventWorker.retryDelay(8))
        assertEquals(1.hours, DomainEventWorker.retryDelay(9))
        assertEquals(1.hours, DomainEventWorker.retryDelay(40))
    }

    @Test
    fun `после ошибки событие откладывается, а не повторяется на следующем опросе`() =
        runTest {
            val id = publish()

            worker.processBatch()
            worker.processBatch()

            assertEquals(1, calls)
            val after = row(id)
            assertEquals(1, after.retryCount)
            assertTrue(after.delayedSeconds in 5..10)
            assertEquals(false, after.processed)
        }

    @Test
    fun `исчерпанные повторы — событие неуспешное, не обработанное и не выбирается`() =
        runTest {
            val id = publish()
            TestPostgres.db.sql("UPDATE domain_events SET retry_count = 11 WHERE id = :id").bind("id", id).execute()

            worker.processBatch()
            val failed = row(id)
            assertTrue(failed.failed)
            assertEquals(false, failed.processed)

            TestPostgres.db.sql("UPDATE domain_events SET next_attempt_at = now() WHERE id = :id").bind("id", id).execute()
            worker.processBatch()
            assertEquals(1, calls)
        }

    @Test
    fun `retry-failed возвращает событие в обработку`() =
        runTest {
            val id = publish()
            TestPostgres.db
                .sql("UPDATE domain_events SET retry_count = 12, failed_at = now() WHERE id = :id")
                .bind("id", id)
                .execute()

            val result = athleticaCommand(adminDi = { AdminDi { TestPostgres.db } }, startServer = {}).test(listOf("admin", "events", "retry-failed", "--org", orgId.value.toString()))
            assertEquals(0, result.statusCode)
            assertTrue(result.stdout.contains("1"))

            failing = false
            worker.processBatch()

            val after = row(id)
            assertTrue(after.processed)
            assertEquals(0, after.retryCount)
            assertEquals(false, after.failed)
        }

    @Test
    fun `успешная обработка помечает событие выполненным`() =
        runTest {
            failing = false
            val id = publish()

            worker.processBatch()

            assertTrue(row(id).processed)
            assertNull(TestPostgres.db.sql("SELECT last_error FROM domain_events WHERE id = :id").bind("id", id).firstOrNull { it.get("last_error", String::class.java) ?: "" }?.ifEmpty { null })
            assertNotNull(id)
        }
}
