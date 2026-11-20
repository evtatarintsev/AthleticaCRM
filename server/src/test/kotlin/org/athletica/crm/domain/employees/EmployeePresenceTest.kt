package org.athletica.crm.domain.employees

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.asInstant
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Тесты учёта активности: запись в БД не чаще раза в минуту на сотрудника. */
class EmployeePresenceTest {
    private val orgId = OrgId.new()
    private val employeeId = EmployeeId.new()
    private val presence = EmployeePresence()
    private val start = Instant.parse("2026-11-20T12:00:00Z")

    @Before
    fun setUp() {
        TestPostgres.truncate()
        orThrow {
            kotlinx.coroutines.runBlocking {
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Org')").bind("id", orgId).execute()
                TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, 'Анна')")
                    .bind("id", employeeId).bind("orgId", orgId).execute()
            }
        }
    }

    /** Отмечает активность в момент [now]. */
    private suspend fun touch(now: Instant) = orThrow { TestPostgres.db.transaction { presence.touch(employeeId, now) } }

    /** Записанное в БД время последней активности. */
    private suspend fun lastSeen(): Instant? =
        orThrow {
            TestPostgres.db.sql("SELECT last_seen_at FROM employees WHERE id = :id AND last_seen_at IS NOT NULL").bind("id", employeeId)
                .firstOrNull { it.asInstant("last_seen_at") }
        }

    @Test
    fun `в пределах минуты пишется одна запись, через минуту — вторая`() =
        runTest {
            touch(start)
            touch(start + 30.seconds)
            assertEquals(start, lastSeen())

            touch(start + 1.minutes)
            assertEquals(start + 1.minutes, lastSeen())
        }
}
