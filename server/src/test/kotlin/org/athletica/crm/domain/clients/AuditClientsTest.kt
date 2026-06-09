package org.athletica.crm.domain.clients

import arrow.core.raise.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditFilter
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object FakeTransaction : Transaction {
    override fun sql(sql: String): QueryBuilder = error("not supported in stub")
}

private class AuditLogStub : AuditLog {
    val events: MutableList<AuditEvent> = mutableListOf()

    context(tr: Transaction)
    override suspend fun log(event: AuditEvent) {
        events.add(event)
    }

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun list(filter: AuditFilter): List<AuditEvent> = events

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun count(filter: AuditFilter): Long = events.size.toLong()
}

private data class ClientStub(
    override val id: ClientId,
    override val name: String,
    override val avatarId: UploadId? = null,
    override val birthday: LocalDate? = null,
    override val gender: Gender = Gender.MALE,
    override val groups: List<ClientGroup> = emptyList(),
    override val docs: List<ClientDoc> = emptyList(),
    override val leadSourceId: LeadSourceId? = null,
    override val customFields: List<CustomFieldValue> = emptyList(),
) : Client {
    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
    }

    context(ctx: EmployeeRequestContext)
    override fun attachDoc(doc: ClientDoc) = copy(docs = docs + doc)

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun deleteDoc(docId: ClientDocId) = copy(docs = docs.filterNot { it.id == docId })

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
        newLeadSourceId: LeadSourceId?,
        newCustomFields: List<CustomFieldValue>,
    ) = copy(
        name = newName,
        avatarId = newAvatarId,
        birthday = newBirthday,
        gender = newGender,
        leadSourceId = newLeadSourceId,
        customFields = newCustomFields,
    )
}

private class ClientsStub(clients: List<ClientStub>) : Clients {
    val clients: MutableList<ClientStub> = clients.toMutableList()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ClientId): Client = clients.first { it.id == id }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
        leadSourceId: LeadSourceId?,
        customFields: List<CustomFieldValue>,
    ): Client {
        val client = ClientStub(id, name, avatarId, birthday, gender, leadSourceId = leadSourceId, customFields = customFields)
        clients.add(client)
        return client
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Client> = clients.toList()
}

class AuditClientsTest {
    private val orgId = OrgId.new()
    private val ctx =
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "owner@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )
    private val tr = FakeTransaction

    @Test
    fun `byId оборачивает клиента в AuditClient`() =
        runTest {
            val id = ClientId.new()
            val subject = AuditClients(ClientsStub(listOf(ClientStub(id, "Иван"))), AuditLogStub())

            val client =
                either {
                    context(ctx, tr) {
                        subject.byId(id)
                    }
                }.getOrNull()

            assertIs<AuditClient>(client)
        }

    @Test
    fun `new оборачивает созданного клиента в AuditClient`() =
        runTest {
            val subject = AuditClients(ClientsStub(emptyList()), AuditLogStub())

            val client =
                either {
                    context(ctx, tr) {
                        subject.new(ClientId.new(), "Мария", null, null, Gender.FEMALE, null, emptyList())
                    }
                }.getOrNull()

            assertIs<AuditClient>(client)
        }

    @Test
    fun `list оборачивает каждого клиента в AuditClient`() =
        runTest {
            val subject =
                AuditClients(
                    ClientsStub(listOf(ClientStub(ClientId.new(), "Иван"), ClientStub(ClientId.new(), "Пётр"))),
                    AuditLogStub(),
                )

            val clients =
                either {
                    context(ctx, tr) {
                        subject.list()
                    }
                }.getOrNull()

            assertEquals(2, clients?.size)
            assertTrue(clients!!.all { it is AuditClient })
        }

    @Test
    fun `save после withNew записывает событие аудита UPDATE для client`() =
        runTest {
            val id = ClientId.new()
            val audit = AuditLogStub()
            val subject = AuditClients(ClientsStub(listOf(ClientStub(id, "Иван"))), audit)

            either {
                context(ctx, tr) {
                    val updated = subject.byId(id).withNew("Иван Петров", null, null, Gender.MALE, null, emptyList())
                    updated.save()
                }
            }

            assertEquals(1, audit.events.size)
            val event = audit.events.first()
            assertEquals(AuditActionType.UPDATE, event.actionType)
            assertEquals("client", event.entityType)
            assertEquals(id.value, event.entityId)
            assertContains(event.data!!, "Иван Петров")
        }

    @Test
    fun `byId без последующих изменений не пишет событий аудита`() =
        runTest {
            val id = ClientId.new()
            val audit = AuditLogStub()
            val subject = AuditClients(ClientsStub(listOf(ClientStub(id, "Иван"))), audit)

            either {
                context(ctx, tr) {
                    subject.byId(id).save()
                }
            }

            assertTrue(audit.events.isEmpty())
        }
}
