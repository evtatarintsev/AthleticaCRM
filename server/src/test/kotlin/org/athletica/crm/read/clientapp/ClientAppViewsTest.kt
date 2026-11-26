package org.athletica.crm.read.clientapp

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clientapp.ClientAppHomeResponse
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.Lang
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clientapp.DbClientAppAccesses
import org.athletica.crm.domain.clientapp.auth.DbClientAppSessions
import org.athletica.crm.storage.asInstantOrNull
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** DB-тесты проекций кабинета клиента и проверки подключённого доступа. */
class ClientAppViewsTest {
    private val mom = "+7 903 123-45-67"

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Контекст профиля клиента [clientId] организации [orgId] для аккаунта [account]. */
    private suspend fun profile(account: ClientAccountContext, orgId: OrgId, clientId: ClientId): Either<AppError, ClientProfileContext> = either { TestPostgres.db.transaction { context(account) { ClientProfileContexts.profile(orgId, clientId) } } }

    /** Время последней активности доступа клиента [clientId]. */
    private suspend fun lastSeen(clientId: ClientId) = orThrow { TestPostgres.db.sql("SELECT last_seen_at FROM client_app_access WHERE client_id = :id").bind("id", clientId).firstOrNull { listOfNotNull(it.asInstantOrNull("last_seen_at")) }?.firstOrNull() }

    @Test
    fun `приглашение без имени клиента, с числом клиентов`() =
        runTest {
            val response =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    ClientAppFixture.client(dynamo, mom, name = "Иван Петров")
                    ClientAppFixture.client(dynamo, "8 (903) 123-45-67", name = "Мария Петрова")
                    val ctx = ClientAppFixture.accountContext(ClientAppFixture.account(mom))
                    TestPostgres.db.transaction { context(ctx) { DbClientAppInvitationsView().invitations() } }
                }

            val invitation = response.invitations.single()
            assertEquals("Динамо", invitation.orgName)
            assertEquals(2, invitation.clientCount)
            assertFalse(response.toString().contains("Иван") || response.toString().contains("Мария"))
        }

    @Test
    fun `без приглашения организации нет`() =
        runTest {
            val response =
                orThrow {
                    ClientAppFixture.client(ClientAppFixture.org("Динамо"), mom, status = null)
                    ClientAppFixture.client(ClientAppFixture.org("Волна", cabinet = false), mom)
                    ClientAppFixture.client(ClientAppFixture.org("Спартак"), mom, status = ClientAppAccessStatus.NOT_ME)
                    val ctx = ClientAppFixture.accountContext(ClientAppFixture.account(mom))
                    TestPostgres.db.transaction { context(ctx) { DbClientAppInvitationsView().invitations() } }
                }

            assertEquals(emptyList(), response.invitations)
        }

    @Test
    fun `новый браузер видит подключённые профили и счётчик приглашений`() =
        runTest {
            val (first, second) =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED, name = "Иван")
                    ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED, name = "Мария")
                    ClientAppFixture.client(ClientAppFixture.org("Волна"), mom, name = "Пётр")
                    val account = ClientAppFixture.account(mom)
                    val view = DbClientAppProfilesView()
                    val old = TestPostgres.db.transaction { context(ClientAppFixture.accountContext(account)) { view.profiles() } }
                    val new = TestPostgres.db.transaction { context(ClientAppFixture.accountContext(account)) { view.profiles() } }
                    old to new
                }

            assertEquals(listOf("Иван", "Мария"), second.profiles.map { it.clientName })
            assertEquals(1, second.newInvitations)
            assertEquals(first, second)
        }

    @Test
    fun `профилей нет — пустой список и ноль приглашений`() =
        runTest {
            val response =
                orThrow {
                    val ctx = ClientAppFixture.accountContext(ClientAppFixture.account(mom))
                    TestPostgres.db.transaction { context(ctx) { DbClientAppProfilesView().profiles() } }
                }

            assertEquals(emptyList(), response.profiles)
            assertEquals(0, response.newInvitations)
        }

    @Test
    fun `главная приветствует клиента по подключённому доступу`() =
        runTest {
            val home =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    val ivan = ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED, name = "Иван Петров")
                    val ctx = ClientAppFixture.accountContext(ClientAppFixture.account(mom))
                    TestPostgres.db.transaction { context(ctx) { context(ClientProfileContexts.profile(dynamo, ivan)) { DbClientAppHomeView().home() } } }
                }

            assertEquals(ClientAppHomeResponse("Иван Петров", "Динамо"), home)
        }

    @Test
    fun `баланс — тип и сумма корректировки без примечания`() =
        runTest {
            val (balance, stranger) =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    val ivan = ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED)
                    val other = ClientAppFixture.client(dynamo, "+7 999 000-00-00", ClientAppAccessStatus.CONNECTED)
                    TestPostgres.db.transaction {
                        sql("INSERT INTO ledger_accounts (org_id, kind) VALUES (:org, 'adjustment')").bind("org", dynamo).execute()
                        sql("INSERT INTO ledger_accounts (org_id, kind, client_id) VALUES (:org, 'advance', :client)").bind("org", dynamo).bind("client", ivan).execute()
                        sql(
                            """
                            WITH t AS (
                                INSERT INTO ledger_transactions (org_id, kind, occurred_on, comment)
                                VALUES (:org, 'adjustment', CURRENT_DATE, 'компенсация за отмену') RETURNING id
                            )
                            INSERT INTO ledger_postings (transaction_id, account_id, amount)
                            SELECT t.id, a.id, CASE WHEN a.kind = 'advance' THEN -500 ELSE 500 END FROM t, ledger_accounts a WHERE a.org_id = :org
                            """.trimIndent(),
                        ).bind("org", dynamo).execute()
                    }
                    val ctx = ClientAppFixture.accountContext(ClientAppFixture.account(mom))
                    val balance = TestPostgres.db.transaction { context(ctx) { context(ClientProfileContexts.profile(dynamo, ivan)) { DbClientAppBalanceView().balance() } } }
                    val forged = ClientProfileContext(ctx.accountId, ctx.sessionId, dynamo, other, Currency.RUB, ctx.lang)
                    balance to either { TestPostgres.db.transaction { context(forged) { DbClientAppBalanceView().balance() } } }
                }

            assertEquals(Money(50_000, Currency.RUB), balance.balance)
            assertEquals(0, balance.unpaidSessions)
            assertEquals(listOf(LedgerKind.ADJUSTMENT to Money(50_000, Currency.RUB)), balance.entries.map { it.kind to it.amount })
            assertFalse(balance.toString().contains("компенсация"))
            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<AppError>>(stranger).value.code)
        }

    @Test
    fun `подмена clientId — CLIENT_NOT_FOUND`() =
        runTest {
            val (ctx, dynamo, strangers) =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED)
                    val stranger = ClientAppFixture.client(dynamo, "+7 999 000-00-00", ClientAppAccessStatus.CONNECTED)
                    val invited = ClientAppFixture.client(dynamo, mom)
                    Triple(ClientAppFixture.accountContext(ClientAppFixture.account(mom)), dynamo, listOf(stranger, invited, ClientId.new()))
                }

            strangers.forEach {
                assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<AppError>>(profile(ctx, dynamo, it)).value.code)
            }
            val forged = ClientProfileContext(ctx.accountId, ctx.sessionId, dynamo, strangers.first(), Currency.RUB, ctx.lang)
            val home = either { TestPostgres.db.transaction { context(forged) { DbClientAppHomeView().home() } } }
            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<AppError>>(home).value.code)
        }

    @Test
    fun `архив клиента и выключенный кабинет закрывают профиль`() =
        runTest {
            val (ctx, dynamo, ivan) =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    val ivan = ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED)
                    Triple(ClientAppFixture.accountContext(ClientAppFixture.account(mom)), dynamo, ivan)
                }
            assertIs<Either.Right<ClientProfileContext>>(profile(ctx, dynamo, ivan))

            orThrow { TestPostgres.db.sql("UPDATE clients SET state = 'ARCHIVED' WHERE id = :id").bind("id", ivan).execute() }
            assertIs<Either.Left<AppError>>(profile(ctx, dynamo, ivan))

            orThrow {
                TestPostgres.db.sql("UPDATE clients SET state = 'ACTIVE' WHERE id = :id").bind("id", ivan).execute()
                TestPostgres.db.sql("UPDATE organizations SET client_app_enabled = false WHERE id = :id").bind("id", dynamo).execute()
            }
            assertIs<Either.Left<AppError>>(profile(ctx, dynamo, ivan))
        }

    @Test
    fun `активность в другой организации не видна`() =
        runTest {
            val (ctx, volna) =
                orThrow {
                    val volna = ClientAppFixture.org("Волна")
                    ClientAppFixture.accountContext(ClientAppFixture.account(mom)) to volna
                }
            val (dynamoIvan, volnaIvan) =
                orThrow {
                    ClientAppFixture.client(ClientAppFixture.org("Динамо"), mom, ClientAppAccessStatus.CONNECTED) to
                        ClientAppFixture.client(volna, mom, ClientAppAccessStatus.CONNECTED)
                }

            val now = Clock.System.now()
            orThrow { TestPostgres.db.transaction { context(ctx) { context(ClientProfileContexts.profile(volna, volnaIvan)) { DbClientAppAccesses().touch(now) } } } }

            assertNotNull(lastSeen(volnaIvan))
            assertNull(lastSeen(dynamoIvan))
        }

    @Test
    fun `активность пишется не чаще раза в 5 минут`() =
        runTest {
            val (ctx, dynamo, ivan) =
                orThrow {
                    val dynamo = ClientAppFixture.org("Динамо")
                    Triple(ClientAppFixture.accountContext(ClientAppFixture.account(mom)), dynamo, ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED))
                }
            val start = Clock.System.now()

            suspend fun touch(at: Instant) = orThrow { TestPostgres.db.transaction { context(ctx) { context(ClientProfileContexts.profile(dynamo, ivan)) { DbClientAppAccesses().touch(at) } } } }

            touch(start)
            touch(start + 4.minutes)
            assertEquals(start.toEpochMilliseconds(), lastSeen(ivan)?.toEpochMilliseconds())
            touch(start + 6.minutes)
            assertEquals((start + 6.minutes).toEpochMilliseconds(), lastSeen(ivan)?.toEpochMilliseconds())
        }

    @Test
    fun `сеансы — только свои и активные, текущий отмечен`() =
        runTest {
            val now = Clock.System.now()
            val (response, ids) =
                orThrow {
                    val sessions = DbClientAppSessions()
                    val mine = ClientAppFixture.account(mom)
                    val other = ClientAppFixture.account("+7 903 000-00-00")
                    val (active, stale, current) =
                        TestPostgres.db.transaction {
                            val active = sessions.new(mine, "Chrome, Android", now - 10.minutes, now - 365.days)
                            val stale = sessions.new(mine, "Firefox, Linux", now - 61.days, now - 365.days)
                            val current = sessions.new(mine, "Safari, iOS", now, now - 365.days)
                            sessions.new(other, "Edge, Windows", now, now - 365.days)
                            Triple(active, stale, current)
                        }
                    val ctx = ClientAccountContext(mine, current.id, Lang.RU)
                    TestPostgres.db.transaction { context(ctx) { DbClientAppSessionsView().sessions(ClientAppSessionsQuery(now, 60.days)) } } to
                        listOf(current.id, active.id, stale.id)
                }

            assertEquals(ids.take(2), response.sessions.map { it.id })
            assertEquals(listOf(true, false), response.sessions.map { it.current })
            assertEquals("Safari, iOS", response.sessions.first().name)
        }
}
