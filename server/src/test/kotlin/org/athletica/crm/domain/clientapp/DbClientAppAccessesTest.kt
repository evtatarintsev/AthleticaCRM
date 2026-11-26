package org.athletica.crm.domain.clientapp

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.clients.archive
import org.athletica.crm.domain.clients.restore
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты доступов к кабинету клиента ([DbClientAppAccesses]) и их синхронизации с контактами. */
class DbClientAppAccessesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val ivan = ClientId.new()
    private val maria = ClientId.new()
    private val otherIvan = ClientId.new()
    private val accesses = DbClientAppAccesses()
    private val contacts = DbClientContacts()
    private val clients = DbClients()
    private val ctx = employeeContext(orgId = orgId)
    private val otherCtx = employeeContext(orgId = otherOrgId)
    private val mom = PhoneDigits.from("+79031234567")!!
    private val dad = PhoneDigits.from("+79037654321")!!

    @Before
    fun setUp() {
        orThrow {
            TestPostgres.truncate()
            runBlocking {
                listOf(orgId to "Динамо", otherOrgId to "Волна").forEach { (id, name) ->
                    TestPostgres.db.sql("INSERT INTO organizations (id, name, client_app_enabled) VALUES (:id, :name, true)")
                        .bind("id", id).bind("name", name).execute()
                }
                listOf(ivan to orgId, maria to orgId, otherIvan to otherOrgId).forEach { (id, org) ->
                    TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, 'Клиент', 'MALE'::gender)")
                        .bind("id", id).bind("orgId", org).execute()
                }
            }
        }
    }

    @Test
    fun `byClient возвращает по доступу на номер без учёта форматирования, неприглашённый по умолчанию`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "8 (903) 123-45-67", "+79031234567", "+7 903 765-43-21")
                    accesses.byClient(ivan)
                }

            val list = assertIs<Either.Right<List<ClientAppAccess>>>(result).value
            assertEquals(listOf(mom, dad), list.map { it.phone })
            assertEquals(listOf(ClientAppAccessStatus.NOT_INVITED, ClientAppAccessStatus.NOT_INVITED), list.map { it.status })
        }

    @Test
    fun `приглашение, подключение и отключение`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    val invited = notInvited(ivan).invite()
                    val connected = invited.connect()
                    connected.disable()
                    accesses.byClient(ivan).single().status
                }

            assertEquals(ClientAppAccessStatus.DISABLED, assertIs<Either.Right<ClientAppAccessStatus>>(result).value)
        }

    @Test
    fun `приглашённый номер можно отключить`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    notInvited(ivan).invite().disable()
                    accesses.byClient(ivan).single().status
                }

            assertEquals(ClientAppAccessStatus.DISABLED, assertIs<Either.Right<ClientAppAccessStatus>>(result).value)
        }

    @Test
    fun `после «Это не я» и после отключения номер приглашают снова`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    val notMe = notInvited(ivan).invite().rejectAsNotMe()
                    assertEquals(ClientAppAccessStatus.NOT_ME, accesses.byClient(ivan).single().status)
                    val reinvited = notMe.invite()
                    val disabled = reinvited.disable()
                    disabled.invite()
                    accesses.byClient(ivan).single() as StoredClientAppAccess
                }

            val access = assertIs<Either.Right<StoredClientAppAccess>>(result).value
            assertIs<InvitedClientAppAccess>(access)
            assertEquals(access.statusChangedAt, access.invitedAt)
        }

    @Test
    fun `переход из устаревшего статуса отклоняется`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    val invited = notInvited(ivan).invite()
                    invited.connect()
                    invited.rejectAsNotMe()
                }

            assertEquals("CLIENT_APP_ACCESS_CHANGED", assertIs<CommonDomainError>(result.leftOrNull()).code)
        }

    @Test
    fun `организации изолированы`() =
        runTest {
            inTx(ctx) {
                setPhones(ivan, "+79031234567")
                setPhones(maria, "+79031234567")
                notInvited(ivan).invite().connect()
                notInvited(maria).invite()
            }
            val result =
                inTx(otherCtx) {
                    setPhones(otherIvan, "+79031234567")
                    notInvited(otherIvan).invite()
                    accesses.byClient(otherIvan).map { it.status } to accesses.byPhone(mom).map { it.orgId to it.status }.toSet()
                }

            val (otherOrgView, phoneView) = assertIs<Either.Right<Pair<List<ClientAppAccessStatus>, Set<Pair<OrgId, ClientAppAccessStatus>>>>>(result).value
            assertEquals(listOf(ClientAppAccessStatus.INVITED), otherOrgView)
            assertEquals(
                setOf(orgId to ClientAppAccessStatus.CONNECTED, orgId to ClientAppAccessStatus.INVITED, otherOrgId to ClientAppAccessStatus.INVITED),
                phoneView,
            )
        }

    @Test
    fun `номер заменён — старый доступ отключён, новый не приглашён`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+7 903 123-45-67")
                    notInvited(ivan).invite().connect()
                    setPhones(ivan, "+7 903 765-43-21")
                    storedStatuses(ivan) to accesses.byClient(ivan).map { it.phone to it.status }
                }

            val (stored, current) = assertIs<Either.Right<Pair<List<Pair<String, String>>, List<Pair<PhoneDigits, ClientAppAccessStatus>>>>>(result).value
            assertEquals(listOf(mom.value to "DISABLED"), stored)
            assertEquals(listOf(dad to ClientAppAccessStatus.NOT_INVITED), current)
        }

    @Test
    fun `номер удалён и записан снова — доступ остаётся отключённым`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    notInvited(ivan).invite().connect()
                    setPhones(ivan)
                    setPhones(ivan, "8 903 123 45 67")
                    accesses.byClient(ivan).single().status
                }

            assertEquals(ClientAppAccessStatus.DISABLED, assertIs<Either.Right<ClientAppAccessStatus>>(result).value)
        }

    @Test
    fun `номер удалён после загрузки карточки — пригласить его нельзя`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    val stale = notInvited(ivan)
                    setPhones(ivan)
                    stale.invite()
                }

            assertEquals("CLIENT_APP_ACCESS_CHANGED", assertIs<CommonDomainError>(result.leftOrNull()).code)
        }

    @Test
    fun `отключённый номер удалён после загрузки карточки — пригласить снова нельзя`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    val disabled = notInvited(ivan).invite().disable()
                    setPhones(ivan)
                    disabled.invite()
                }

            assertEquals("CLIENT_APP_ACCESS_CHANGED", assertIs<CommonDomainError>(result.leftOrNull()).code)
        }

    @Test
    fun `byPhone не возвращает доступ номера, которого нет в контактах`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    notInvited(ivan).invite().connect()
                    deleteContactsBypassingSync(ivan)
                    accesses.byPhone(mom)
                }

            assertEquals(emptyList(), assertIs<Either.Right<List<StoredClientAppAccess>>>(result).value)
        }

    @Test
    fun `архив клиента и выключенный кабинет скрывают доступ, восстановление возвращает прежний статус`() =
        runTest {
            val result =
                inTx(ctx) {
                    setPhones(ivan, "+79031234567")
                    notInvited(ivan).invite().connect()
                    clients.archive(ivan)
                    val archived = accesses.byPhone(mom).map { it.status }
                    clients.restore(ivan)
                    val restored = accesses.byPhone(mom).map { it.status }
                    setCabinet(enabled = false)
                    val disabled = accesses.byPhone(mom).map { it.status }
                    setCabinet(enabled = true)
                    listOf(archived, restored, disabled, accesses.byPhone(mom).map { it.status })
                }

            assertEquals(
                listOf(emptyList(), listOf(ClientAppAccessStatus.CONNECTED), emptyList(), listOf(ClientAppAccessStatus.CONNECTED)),
                assertIs<Either.Right<List<List<ClientAppAccessStatus>>>>(result).value,
            )
        }

    /** Выполняет [block] в транзакции от имени сотрудника [ctx]. */
    private suspend fun <T> inTx(
        ctx: EmployeeRequestContext,
        block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T,
    ): Either<AppError, T> =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    block()
                }
            }
        }

    /** Заменяет телефоны клиента [clientId] на [phones]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun setPhones(
        clientId: ClientId,
        vararg phones: String,
    ) {
        contacts.replace(clients.activeById(clientId), phones.map { ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, it) })
    }

    /** Номера и статусы строк доступа клиента [clientId] в БД. */
    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun storedStatuses(clientId: ClientId): List<Pair<String, String>> =
        tr.sql("SELECT phone_digits, status FROM client_app_access WHERE client_id = :id")
            .bind("id", clientId)
            .list { it.asString("phone_digits") to it.asString("status") }

    /** Удаляет контакты клиента [clientId] в обход `replace`, без синхронизации доступов. */
    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun deleteContactsBypassingSync(clientId: ClientId) {
        tr.sql("DELETE FROM client_contacts WHERE client_id = :id").bind("id", clientId).execute()
    }

    /** Включает или выключает кабинет организации [orgId]. */
    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun setCabinet(enabled: Boolean) {
        tr.sql("UPDATE organizations SET client_app_enabled = :enabled WHERE id = :id").bind("enabled", enabled).bind("id", orgId).execute()
    }

    /** Единственный неприглашённый доступ клиента [clientId]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun notInvited(clientId: ClientId): NotInvitedClientAppAccess = assertIs<NotInvitedClientAppAccess>(accesses.byClient(clientId).single())
}
