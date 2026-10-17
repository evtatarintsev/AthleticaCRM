package org.athletica.crm.read.home

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.SessionsFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.home.UnclosedSessionsResponse
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Тесты проекции незакрытых занятий: только закончившиеся запланированные занятия своего
 * филиала, порядок от недавних, лимит и общее число, окончание в часовом поясе организации
 * (`Asia/Yekaterinburg`, UTC+5).
 */
class DbUnclosedSessionsViewTest {
    private val fixture = SessionsFixture(timezone = "Asia/Yekaterinburg")
    private val view = DbUnclosedSessionsView()
    private val day = LocalDate(2026, 10, 17)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private suspend fun unclosed(now: String, limit: Int = 10): UnclosedSessionsResponse =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(employeeContext(orgId = fixture.orgId, branchId = fixture.branchId), this, raise) {
                    view.unclosed(UnclosedSessionsQuery(Instant.parse(now), limit))
                }
            }
        }.getOrElse { kotlin.test.fail("Unexpected error: $it") }

    @Test
    fun `только закончившиеся запланированные занятия своего филиала`() =
        runTest {
            val group = fixture.insertGroup("Йога")
            val ended = fixture.insertSession(group, day, LocalTime(17, 0), LocalTime(18, 0))
            fixture.insertSession(group, day, LocalTime(18, 0), LocalTime(19, 30))
            fixture.insertSession(group, day.minusDays(1), status = "completed")
            fixture.insertSession(group, day.minusDays(2), status = "cancelled")
            val otherBranch = BranchId.new()
            fixture.insertBranch(otherBranch)
            fixture.insertSession(fixture.insertGroup("Бокс", otherBranch), day.minusDays(1))

            val result = unclosed("2026-10-17T14:00:00Z")

            assertEquals(listOf(ended), result.sessions.map { it.sessionId })
            assertEquals(1, result.total)
            assertEquals("Йога", result.sessions.single().groupName)
            assertEquals("Зал", result.sessions.single().hallName)
        }

    @Test
    fun `от недавних к давним с лимитом и общим числом`() =
        runTest {
            val group = fixture.insertGroup()
            val sessions = (1..5).map { fixture.insertSession(group, day.minusDays(it)) }

            val result = unclosed("2026-10-17T12:00:00Z", limit = 3)

            assertEquals(sessions.take(3), result.sessions.map { it.sessionId })
            assertEquals(5, result.total)
        }
}

private fun LocalDate.minusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() - days)
