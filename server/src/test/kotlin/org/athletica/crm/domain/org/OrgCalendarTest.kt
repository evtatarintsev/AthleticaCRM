package org.athletica.crm.domain.org

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.domain.memberships.MembershipFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Тесты «сегодня» в часовом поясе организации. */
class OrgCalendarTest {
    private val fixture = MembershipFixture(timezone = "Asia/Yekaterinburg")

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `23-30 UTC в поясе UTC+5 — уже следующая дата`() {
        assertEquals(LocalDate(2026, 10, 16), dateIn("Asia/Yekaterinburg", Instant.parse("2026-10-15T23:30:00Z")))
        assertEquals(LocalDate(2026, 10, 15), dateIn("UTC", Instant.parse("2026-10-15T23:30:00Z")))
    }

    @Test
    fun `today берёт часовой пояс организации и подменённое время`() =
        runTest {
            fixture.setUp()
            fixture.clock.now = Instant.parse("2026-10-15T23:30:00Z")
            val calendar = DbOrgCalendar(fixture.clock)

            assertEquals(LocalDate(2026, 10, 16), fixture.tx { calendar.today() })
        }
}
