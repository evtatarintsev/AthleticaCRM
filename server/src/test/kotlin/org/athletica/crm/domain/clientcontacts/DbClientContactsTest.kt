package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.ConstraintViolation
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты репозитория контактов клиента ([DbClientContacts]). */
class DbClientContactsTest {
    private val orgId = OrgId.new()
    private val clientId = ClientId.new()
    private val contacts = DbClientContacts()
    private val clients = DbClients()

    private val ctx =
        employeeContext(
            lang = Lang.RU,
            userId = UserId.new(),
            orgId = orgId,
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "user@example.com",
            clientIp = null,
            currency = Currency.RUB,
            permissions = EmployeePermission(),
        )

    @Before
    fun setUp() {
        orThrow {
            TestPostgres.truncate()
            runBlocking {
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", orgId).bind("name", "Org").execute()
                TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                    .bind("id", clientId).bind("orgId", orgId).bind("name", "Клиент").execute()
            }
        }
    }

    @Test
    fun `replace полностью заменяет набор контактов клиента`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            contacts.replace(
                                clients.activeById(clientId),
                                listOf(
                                    ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, "+79990001122"),
                                    ClientContact(ClientContactId.new(), clientId, ContactType.EMAIL, "a@b.c"),
                                ),
                            )
                            contacts.replace(
                                clients.activeById(clientId),
                                listOf(ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, "+70001112233")),
                            )
                            contacts.byClient(clientId)
                        }
                    }
                }

            val stored = result.getOrNull().orEmpty()
            assertEquals(1, stored.size)
            assertEquals(ContactType.PHONE, stored.first().type)
            assertEquals("+70001112233", stored.first().value)
        }

    @Test
    fun `byClients группирует контакты по клиенту, сохраняет порядок и изолирует организацию`() =
        orThrow {
            runTest {
                val clientId2 = ClientId.new()
                val otherOrgId = OrgId.new()
                val otherClientId = ClientId.new()

                TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                    .bind("id", clientId2).bind("orgId", orgId).bind("name", "Клиент 2").execute()
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", otherOrgId).bind("name", "Другая Org").execute()
                TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                    .bind("id", otherClientId).bind("orgId", otherOrgId).bind("name", "Чужой клиент").execute()

                val result =
                    either {
                        TestPostgres.db.transaction {
                            context(ctx) {
                                contacts.replace(
                                    clients.activeById(clientId),
                                    listOf(
                                        ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, "+79990001122"),
                                        ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, "+79993334455"),
                                    ),
                                )
                                contacts.replace(
                                    clients.activeById(clientId2),
                                    listOf(ClientContact(ClientContactId.new(), clientId2, ContactType.EMAIL, "a@b.c")),
                                )
                            }
                            context(ctx.copy(orgId = otherOrgId)) {
                                contacts.replace(
                                    clients.activeById(otherClientId),
                                    listOf(ClientContact(ClientContactId.new(), otherClientId, ContactType.PHONE, "+70000000000")),
                                )
                            }
                            context(ctx) {
                                contacts.byClients(listOf(clientId, clientId2, otherClientId))
                            }
                        }
                    }

                val byClient = result.getOrNull().orEmpty()
                assertEquals(setOf("+79990001122", "+79993334455"), byClient.getValue(clientId).map { it.value }.toSet())
                assertEquals(listOf("a@b.c"), byClient.getValue(clientId2).map { it.value })
                assertFalse(byClient.containsKey(otherClientId))
            }
        }

    @Test
    fun `byClients возвращает пустую карту для пустого списка идентификаторов`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            contacts.byClients(emptyList())
                        }
                    }
                }

            assertTrue(result.getOrNull().orEmpty().isEmpty())
        }

    private suspend fun replaceContacts(vararg contactList: ClientContact) =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    contacts.replace(clients.activeById(clientId), contactList.toList())
                }
            }
        }

    private suspend fun storedContacts(): List<ClientContact> =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    contacts.byClient(clientId)
                }
            }
        }.getOrNull().orEmpty()

    private fun phone(
        value: String,
        label: String,
        isPrimary: Boolean = false,
    ) = ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, value, label, isPrimary)

    @Test
    fun `replace сохраняет набор в нормализованном виде`() =
        runTest {
            replaceContacts(
                phone("1", " мама "),
                ClientContact(ClientContactId.new(), clientId, ContactType.EMAIL, "a@b.c"),
                phone("2", "папа", isPrimary = true),
            )

            assertEquals(
                listOf(
                    Triple("2", "папа", true),
                    Triple("1", "мама", false),
                    Triple("a@b.c", null, true),
                ),
                storedContacts().map { Triple(it.value, it.label, it.isPrimary) },
            )
        }

    @Test
    fun `два основных телефона отклоняются и не меняют прежние контакты`() =
        runTest {
            replaceContacts(phone("1", "мама"))

            val result = replaceContacts(phone("2", "папа", isPrimary = true), phone("3", "бабушка", isPrimary = true))

            assertTrue(result.isLeft())
            assertEquals(listOf("1"), storedContacts().map { it.value })
        }

    @Test
    fun `после удаления основного телефона основным становится следующий`() =
        runTest {
            replaceContacts(phone("1", "мама", isPrimary = true), phone("2", "папа"))

            replaceContacts(phone("2", "папа"))

            assertEquals(listOf("папа" to true), storedContacts().map { it.label to it.isPrimary })
        }

    @Test
    fun `база не даёт оставить тип контактов без основного или с двумя основными`() =
        runTest {
            replaceContacts(phone("1", "мама"), phone("2", "папа"))

            val withoutPrimary =
                either {
                    TestPostgres.db.sql("UPDATE client_contacts SET is_primary = false WHERE client_id = :clientId")
                        .bind("clientId", clientId).execute()
                }
            val twoPrimaries =
                either {
                    TestPostgres.db.sql("UPDATE client_contacts SET is_primary = true WHERE client_id = :clientId")
                        .bind("clientId", clientId).execute()
                }

            assertEquals("ck_client_contacts_one_primary", assertIs<ConstraintViolation>(withoutPrimary.leftOrNull()).constraint)
            assertIs<ConstraintViolation>(twoPrimaries.leftOrNull())
            assertEquals(listOf("мама" to true, "папа" to false), storedContacts().map { it.label to it.isPrimary })
        }
}
