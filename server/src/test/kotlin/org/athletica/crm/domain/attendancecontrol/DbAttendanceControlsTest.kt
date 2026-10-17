package org.athletica.crm.domain.attendancecontrol

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.SessionsFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/** DB-тесты настроек контроля посещаемости: значения по умолчанию, сохранение, изоляция, диапазон окна. */
class DbAttendanceControlsTest {
    private val first = SessionsFixture()
    private val second = SessionsFixture()
    private val repository = DbAttendanceControls()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            first.setUp()
            second.setUp()
        }
    }

    private suspend fun current(fixture: SessionsFixture): AttendanceControl =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(employeeContext(orgId = fixture.orgId), this, raise) { repository.current() }
            }
        }.getOrElse { fail("Unexpected error: $it") }

    private suspend fun save(fixture: SessionsFixture, enabled: Boolean, minutes: Int) {
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(employeeContext(orgId = fixture.orgId), this, raise) {
                    val delay = JournalCloseDelay.from(minutes).getOrElse { fail("Unexpected error: $it") }
                    repository.current().withNew(enabled, delay).save()
                }
            }
        }.getOrElse { fail("Unexpected error: $it") }
    }

    @Test
    fun `без сохранения действуют значения по умолчанию`() =
        runTest {
            val settings = current(first)
            assertEquals(true, settings.overdueTaskEnabled)
            assertEquals(120, settings.overdueTaskDelay.minutes)
        }

    @Test
    fun `сохранённые настройки читаются и не влияют на другую организацию`() =
        runTest {
            save(first, enabled = false, minutes = 240)
            save(first, enabled = false, minutes = 300)

            val saved = current(first)
            assertEquals(false, saved.overdueTaskEnabled)
            assertEquals(300, saved.overdueTaskDelay.minutes)
            assertEquals(true, current(second).overdueTaskEnabled)
        }

    @Test
    fun `окно вне диапазона отклоняется`() {
        listOf(5, 14, 10081).forEach {
            val error = assertIs<Either.Left<DomainError>>(JournalCloseDelay.from(it)).value
            assertEquals("INVALID_JOURNAL_CLOSE_DELAY", assertIs<CommonDomainError>(error).code)
        }
        listOf(15, 10080).forEach { assertIs<Either.Right<JournalCloseDelay>>(JournalCloseDelay.from(it)) }
    }
}
