package org.athletica.crm.domain.auth

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Тесты сессий входа: ротация токена обновления, окно гонки, завершение сессий. */
class AuthSessionsTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val otherUserId = UserId.new()
    private val sessions = DbAuthSessions()
    private val now = Clock.System.now()

    @Before
    fun setUp() {
        orThrow {
            TestPostgres.truncate()
            runBlocking {
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Org')").bind("id", orgId).execute()
                listOf(userId, otherUserId).forEach { id ->
                    TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, 'x')")
                        .bind("id", id)
                        .bind("login", "$id@example.com")
                        .execute()
                }
            }
        }
    }

    /** Открывает сессию пользователя [user] в момент [at]. */
    private suspend fun open(user: UserId = userId, at: Instant = now): IssuedSession =
        either { TestPostgres.db.transaction { sessions.new(user, orgId, "UA", "10.0.0.1", at, at - 30.days) } }
            .getOrElse { fail("Unexpected error: $it") }

    /** Меняет токен обновления сессии [session] по предъявленному [jti] в момент [at]. */
    private suspend fun rotate(session: IssuedSession, jti: Uuid, at: kotlin.time.Instant): IssuedSession? =
        either { TestPostgres.db.transaction { sessions.rotate(session.id, jti, "10.0.0.2", at) } }
            .getOrElse { fail("Unexpected error: $it") }

    /** Существует ли сессия [id]. */
    private suspend fun exists(id: AuthSessionId): Boolean = orThrow { TestPostgres.db.sql("SELECT count(*) AS n FROM auth_sessions WHERE id = :id").bind("id", id).firstOrNull { it.asLong("n") } } == 1L

    @Test
    fun `ротация по действующему токену выдаёт новый токен той же сессии`() =
        runTest {
            val session = open()
            val rotated = assertNotNull(rotate(session, session.refreshJti, now + 1.seconds))
            assertEquals(session.id, rotated.id)
            assertNotEquals(session.refreshJti, rotated.refreshJti)
        }

    @Test
    fun `прежний токен в пределах окна принимается`() =
        runTest {
            val session = open()
            assertNotNull(rotate(session, session.refreshJti, now + 1.seconds))
            assertNotNull(rotate(session, session.refreshJti, now + 30.seconds))
            assertEquals(true, exists(session.id))
        }

    @Test
    fun `прежний токен после окна завершает сессию`() =
        runTest {
            val session = open()
            assertNotNull(rotate(session, session.refreshJti, now))
            assertNull(rotate(session, session.refreshJti, now + 61.seconds))
            assertEquals(false, exists(session.id))
        }

    @Test
    fun `неизвестный токен завершает сессию`() =
        runTest {
            val session = open()
            assertNull(rotate(session, Uuid.random(), now))
            assertEquals(false, exists(session.id))
        }

    @Test
    fun `завершённая сессия не проходит проверку`() =
        runTest {
            val session = open()
            either { TestPostgres.db.transaction { sessions.deleteOthers(userId, keep = null) } }.getOrElse { fail("$it") }
            val result = either { TestPostgres.db.transaction { sessions.requireActive(session.id, userId) } }
            assertEquals(SessionRevoked, assertIs<Either.Left<AppError>>(result).value)
        }

    @Test
    fun `deleteOthers оставляет только сохраняемую сессию`() =
        runTest {
            val current = open()
            val other = open()
            val foreign = open(otherUserId)
            either { TestPostgres.db.transaction { sessions.deleteOthers(userId, keep = current.id) } }.getOrElse { fail("$it") }
            assertEquals(true, exists(current.id))
            assertEquals(false, exists(other.id))
            assertEquals(true, exists(foreign.id))
        }

    @Test
    fun `вход удаляет давно неактивные сессии только этого пользователя`() =
        runTest {
            val stale = open(at = now - 31.days)
            val foreignStale = open(otherUserId, at = now - 31.days)
            open()
            assertEquals(false, exists(stale.id))
            assertEquals(true, exists(foreignStale.id))
        }

    @Test
    fun `чужую сессию завершить нельзя`() =
        runTest {
            val foreign = open(otherUserId)
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(employeeContext(orgId = orgId, userId = userId)) { sessions.delete(foreign.id, userId) }
                    }
                }
            assertEquals("AUTH_SESSION_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
            assertEquals(true, exists(foreign.id))
        }
}
