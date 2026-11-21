package org.athletica.crm.read.auth

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SessionListResponse
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.auth.DbAuthSessions
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** DB-тесты проекции активных сеансов сотрудника. */
class DbSessionListViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val foreignEmployeeId = EmployeeId.new()
    private val view = DbSessionListView()
    private val now = Clock.System.now()

    @Before
    fun setUp() {
        orThrow {
            TestPostgres.truncate()
            runBlocking {
                listOf(orgId, otherOrgId).forEach {
                    TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Org')").bind("id", it).execute()
                }
                TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, 'u@example.com', 'x')").bind("id", userId).execute()
                TestPostgres.db.sql("INSERT INTO employees (id, org_id, user_id, name) VALUES (:id, :orgId, :userId, 'Анна')")
                    .bind("id", employeeId).bind("orgId", orgId).bind("userId", userId).execute()
                TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, 'Чужой')")
                    .bind("id", foreignEmployeeId).bind("orgId", otherOrgId).execute()
            }
        }
    }

    /** Открывает сеанс сотрудника с последней активностью [at]. */
    private suspend fun open(at: Instant): AuthSessionId =
        either { TestPostgres.db.transaction { DbAuthSessions().new(userId, orgId, null, null, at, Instant.DISTANT_PAST) } }
            .getOrElse { fail("$it") }
            .id

    /** Сеансы сотрудника [employee], запрошенные из сеанса [current]. */
    private suspend fun list(employee: EmployeeId, current: AuthSessionId?): Either<AppError, SessionListResponse> =
        either {
            TestPostgres.db.transaction {
                context(employeeContext(orgId = orgId, userId = userId, employeeId = employeeId, sessionId = current)) {
                    view.list(SessionListQuery(employee, now, 30.days))
                }
            }
        }

    @Test
    fun `сеансы от недавних к давним, текущий отмечен, давние скрыты`() =
        runTest {
            val older = open(now - 2.days)
            val recent = open(now - 1.days)
            open(now - 31.days)

            val items = assertIs<Either.Right<SessionListResponse>>(list(employeeId, older)).value.items
            assertEquals(listOf(recent, older), items.map { it.id })
            assertEquals(listOf(false, true), items.map { it.current })
        }

    @Test
    fun `сотрудник без сеансов — пустой список`() =
        runTest {
            assertEquals(emptyList(), assertIs<Either.Right<SessionListResponse>>(list(employeeId, null)).value.items)
        }

    @Test
    fun `сотрудник другой организации не найден`() =
        runTest {
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<AppError>>(list(foreignEmployeeId, null)).value.code)
        }
}
