package org.athletica.crm.read.tasks

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

/** Тесты read-проекции карточки задачи: подстановка имён и сборка вложений. */
class DbTaskDetailViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()

    private val tasks = DbTasks()
    private val view = DbTaskDetailView()

    private val ctx =
        EmployeeRequestContext(
            lang = Lang.RU,
            userId = userId,
            orgId = orgId,
            branchId = BranchId.new(),
            employeeId = employeeId,
            username = "test@example.com",
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
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Other Org").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "test@example.com").bind("hash", "hash").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", employeeId).bind("orgId", orgId).bind("name", "Иван").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", otherEmployeeId).bind("orgId", orgId).bind("name", "Пётр").execute()
        }
    }

    private suspend fun insertClient(name: String): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId).bind("orgId", orgId).bind("name", name)
            .execute()
        return clientId
    }

    private suspend fun insertUpload(originalName: String, sizeBytes: Long): UploadId {
        val id = Uuid.generateV7()
        TestPostgres.db
            .sql(
                """
                INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
                VALUES (:id, :orgId, :userId, :key, :name, 'application/pdf', :size)
                """.trimIndent(),
            )
            .bind("id", id).bind("orgId", orgId).bind("userId", userId)
            .bind("key", "uploads/$id").bind("name", originalName).bind("size", sizeBytes)
            .execute()
        return id.toUploadId()
    }

    private suspend fun <T> inTransaction(block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T): Either<DomainError, T> =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(ctx, this, raise) { block() }
            }
        }

    private suspend fun <T> succeeding(block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T): T = inTransaction(block).getOrElse { fail("Unexpected error: $it") }

    @Test
    fun `возвращает карточку с именем создателя`() =
        runTest {
            val taskId = TaskId.new()
            succeeding { tasks.new(taskId, "Позвонить", "Описание", null, null, null) }

            val row = succeeding { view.byId(taskId) }

            assertEquals(taskId, row.id)
            assertEquals("Позвонить", row.title)
            assertEquals("Описание", row.description)
            assertEquals(TaskStatus.PENDING, row.status)
            assertEquals(employeeId, row.createdBy)
            assertEquals("Иван", row.createdByName)
            assertNull(row.assigneeId)
            assertNull(row.assigneeName)
            assertNull(row.clientId)
            assertNull(row.clientName)
            assertTrue(row.attachments.isEmpty())
        }

    @Test
    fun `подставляет имена исполнителя и клиента`() =
        runTest {
            val clientId = insertClient("Анна Клиентова")
            val taskId = TaskId.new()
            succeeding { tasks.new(taskId, "С клиентом", "", clientId, null, null) }
            TestPostgres.db
                .sql("UPDATE tasks SET assignee_id = :assigneeId WHERE id = :id")
                .bind("assigneeId", otherEmployeeId).bind("id", taskId)
                .execute()

            val row = succeeding { view.byId(taskId) }

            assertEquals(otherEmployeeId, row.assigneeId)
            assertEquals("Пётр", row.assigneeName)
            assertEquals(clientId, row.clientId)
            assertEquals("Анна Клиентова", row.clientName)
        }

    @Test
    fun `собирает метаданные вложений`() =
        runTest {
            val taskId = TaskId.new()
            val upload = insertUpload("договор.pdf", sizeBytes = 2048)
            succeeding {
                tasks.new(taskId, "С вложением", "", null, null, null)
                tasks.byId(taskId).attach(upload).save()
            }

            val attachment = succeeding { view.byId(taskId) }.attachments.single()

            assertEquals(upload, attachment.id)
            assertEquals("договор.pdf", attachment.originalName)
            assertEquals("application/pdf", attachment.contentType)
            assertEquals(2048L, attachment.sizeBytes)
            assertEquals("uploads/${upload.value}", attachment.objectKey)
        }

    @Test
    fun `задача чужой организации не находится`() =
        runTest {
            val taskId = TaskId.new()
            succeeding { tasks.new(taskId, "Своя", "", null, null, null) }
            TestPostgres.db
                .sql("UPDATE tasks SET org_id = :orgId WHERE id = :id")
                .bind("orgId", otherOrgId).bind("id", taskId)
                .execute()

            assertIs<Either.Left<DomainError>>(inTransaction { view.byId(taskId) })
        }
}
