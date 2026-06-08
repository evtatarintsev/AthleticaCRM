package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты репозитория контактов клиента ([DbClientContacts]). */
class DbClientContactsTest {
    private val orgId = OrgId.new()
    private val clientId = ClientId.new()
    private val contacts = DbClientContacts()

    private val ctx =
        EmployeeRequestContext(
            lang = Lang.RU,
            userId = UserId.new(),
            orgId = orgId,
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "user@example.com",
            clientIp = null,
            currency = Currency.RUB,
            permission = EmployeePermission(),
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                .bind("id", clientId).bind("orgId", orgId).bind("name", "Клиент").execute()
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
                                clientId,
                                listOf(
                                    ClientContact(ClientContactId.new(), clientId, ContactType.PHONE, "+79990001122"),
                                    ClientContact(ClientContactId.new(), clientId, ContactType.EMAIL, "a@b.c"),
                                ),
                            )
                            contacts.replace(
                                clientId,
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
}
