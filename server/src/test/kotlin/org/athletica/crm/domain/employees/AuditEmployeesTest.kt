package org.athletica.crm.domain.employees

import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.domain.EmployeesStub
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditFilter
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import kotlin.collections.emptyList
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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

class AuditEmployeesTest {
    private val orgId = OrgId.new()
    private val ctx = RequestContext(Lang.EN, UserId.new(), orgId, "owner@example.com", "127.0.0.1")
    private val tr = FakeTransaction
    private val clock = Clock.System
    private val emptyPermission = EmployeePermission(emptyList(), emptySet(), emptySet())

    private fun makeSubject(
        stub: org.athletica.crm.domain.EmployeesStub,
        audit: AuditLogStub,
    ) = AuditEmployees(stub, audit)

    // ─── new ──────────────────────────────────────────────────────────────────

    @Test
    fun `new делегирует создание сотрудника и возвращает его`() =
        runTest {
            val stub = EmployeesStub(emptyList(), clock)
            val audit = AuditLogStub()
            val subject = makeSubject(stub, audit)
            val id = EmployeeId.new()

            val employee =
                either<DomainError, _> {
                    context(ctx, tr, this) {
                        subject.new(id, "Иван Иванов", "+71234567890", "ivan@example.com".toEmailAddress(), null, emptyPermission)
                    }
                }.getOrNull()

            assertNotNull(employee)
            assertEquals(id, employee.id)
            assertEquals("Иван Иванов", employee.name)
            assertEquals(1, stub.employees.size)
        }

    @Test
    fun `new записывает событие аудита CREATE для employee`() =
        runTest {
            val stub = EmployeesStub(emptyList(), clock)
            val audit = AuditLogStub()
            val subject = makeSubject(stub, audit)
            val id = EmployeeId.new()

            either<DomainError, _> {
                context(ctx, tr, this) {
                    subject.new(id, "Мария", null, null, null, emptyPermission)
                }
            }

            assertEquals(1, audit.events.size)
            val event = audit.events.first()
            assertEquals(AuditActionType.CREATE, event.actionType)
            assertEquals("employee", event.entityType)
            assertEquals(id.value, event.entityId)
            assertEquals(orgId, event.orgId)
            assertEquals(ctx.userId, event.userId)
        }

    @Test
    fun `new включает в данные аудита id, name, phoneNo и email`() =
        runTest {
            val stub = EmployeesStub(emptyList(), clock)
            val audit = AuditLogStub()
            val subject = makeSubject(stub, audit)
            val id = EmployeeId.new()

            either<DomainError, _> {
                context(ctx, tr, this) {
                    subject.new(id, "Сергей", "+79001234567", "sergey@example.com".toEmailAddress(), null, emptyPermission)
                }
            }

            val data = audit.events.first().data
            assertNotNull(data)
            assertContains(data, id.value.toString())
            assertContains(data, "Сергей")
            assertContains(data, "+79001234567")
            assertContains(data, "sergey@example.com")
        }

    @Test
    fun `new записывает null-поля без ошибок`() =
        runTest {
            val stub = EmployeesStub(emptyList(), clock)
            val audit = AuditLogStub()
            val subject = makeSubject(stub, audit)

            either<DomainError, _> {
                context(ctx, tr, this) {
                    subject.new(EmployeeId.new(), "Без контактов", null, null, null, emptyPermission)
                }
            }

            assertEquals(1, audit.events.size)
            val data = audit.events.first().data
            assertNotNull(data)
            assertContains(data, "Без контактов")
        }

    @Test
    fun `new записывает ровно одно событие аудита на каждый вызов`() =
        runTest {
            val stub = EmployeesStub(emptyList(), clock)
            val audit = AuditLogStub()
            val subject = makeSubject(stub, audit)

            repeat(3) {
                either<DomainError, _> {
                    context(ctx, tr, this) {
                        subject.new(EmployeeId.new(), "Сотрудник $it", null, null, null, emptyPermission)
                    }
                }
            }

            assertEquals(3, audit.events.size)
            assertTrue(audit.events.all { it.actionType == AuditActionType.CREATE })
        }
}
