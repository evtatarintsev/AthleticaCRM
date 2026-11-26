package org.athletica.crm.usecases.clientapp

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clientapp.DbClientAppAccesses
import org.athletica.crm.domain.clientapp.auth.DbClientAppAccounts
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты ответа на приглашение ([ClientAppConnections]): «Подключить» и «Это не я». */
class ClientAppConnectionsTest {
    private val mom = "+7 903 123-45-67"
    private val connections = ClientAppConnections(TestPostgres.db, DbClientAppAccounts(), DbClientAppAccesses(), DbTasks())

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Статус доступа клиента [clientId]. */
    private suspend fun status(clientId: ClientId): String = orThrow { checkNotNull(TestPostgres.db.sql("SELECT status FROM client_app_access WHERE client_id = :id").bind("id", clientId).firstOrNull { it.asString("status") }) }

    /** Число задач по клиенту [clientId]. */
    private suspend fun tasks(clientId: ClientId): Int = orThrow { checkNotNull(TestPostgres.db.sql("SELECT count(*)::int AS n FROM tasks WHERE client_id = :id").bind("id", clientId).firstOrNull { it.asInt("n") }) }

    /** Аккаунт мамы. */
    private suspend fun account(): ClientAccountContext = orThrow { ClientAppFixture.accountContext(ClientAppFixture.account(mom)) }

    @Test
    fun `оба ребёнка подключены, доступы других организаций не затронуты`() =
        runTest {
            val dynamo = orThrow { ClientAppFixture.org("Динамо") }
            val children = orThrow { listOf(ClientAppFixture.client(dynamo, mom), ClientAppFixture.client(dynamo, mom)) }
            val volnaIvan = orThrow { ClientAppFixture.client(ClientAppFixture.org("Волна"), mom) }

            orThrow { context(account()) { connections.connect(dynamo) } }

            children.forEach { assertEquals("CONNECTED", status(it)) }
            assertEquals("INVITED", status(volnaIvan))
        }

    @Test
    fun `без приглашения подключиться нельзя`() =
        runTest {
            val dynamo = orThrow { ClientAppFixture.org("Динамо").also { ClientAppFixture.client(it, mom, status = null) } }

            val result: Either<AppError, Unit> = either { context(account()) { connections.connect(dynamo) } }

            assertEquals("CLIENT_APP_INVITATION_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `это не я — статус и задача по каждому клиенту, подключённые не тронуты`() =
        runTest {
            val dynamo = orThrow { ClientAppFixture.org("Динамо") }
            val invited = orThrow { listOf(ClientAppFixture.client(dynamo, mom), ClientAppFixture.client(dynamo, mom)) }
            val connected = orThrow { ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED) }

            orThrow { context(account()) { connections.notMe(dynamo) } }

            invited.forEach {
                assertEquals("NOT_ME", status(it))
                assertEquals(1, tasks(it))
            }
            assertEquals("CONNECTED", status(connected))
            assertEquals(0, tasks(connected))
        }

    @Test
    fun `повторное это не я при открытой задаче не создаёт вторую`() =
        runTest {
            val (dynamo, ivan) = orThrow { ClientAppFixture.org("Динамо").let { it to ClientAppFixture.client(it, mom) } }
            val ctx = account()

            orThrow { context(ctx) { connections.notMe(dynamo) } }
            orThrow { TestPostgres.db.sql("UPDATE client_app_access SET status = 'INVITED'").execute() }
            orThrow { context(ctx) { connections.notMe(dynamo) } }
            assertEquals(1, tasks(ivan))

            orThrow {
                TestPostgres.db.sql("UPDATE tasks SET status = 'COMPLETED', completed_at = now()").execute()
                TestPostgres.db.sql("UPDATE client_app_access SET status = 'INVITED'").execute()
            }
            orThrow { context(ctx) { connections.notMe(dynamo) } }
            assertEquals(2, tasks(ivan))
        }
}
