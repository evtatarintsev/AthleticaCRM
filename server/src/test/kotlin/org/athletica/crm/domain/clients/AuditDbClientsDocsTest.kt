package org.athletica.crm.domain.clients

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.uuid.Uuid

/**
 * Регрессионные тесты прикрепления и удаления документов клиента через [AuditClients]
 * поверх реальных [DbClients] и [PostgresAuditLog] — так, как клиенты собраны в `Di.kt`.
 */
class AuditDbClientsDocsTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val branchId = Uuid.generateV7()

    private val ctx =
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = orgId,
            currency = Currency.RUB,
            userId = userId,
            branchId = branchId.toBranchId(),
            employeeId = EmployeeId.new(),
            username = "test@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )

    private val clients = AuditClients(DbClients(), PostgresAuditLog())

    /** Очищает базу и создаёт организацию, пользователя и филиал. */
    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "test@example.com").bind("hash", "hash").execute()
            TestPostgres.db.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", branchId).bind("orgId", orgId).bind("name", "Основной").execute()
        }
    }

    /** Создаёт запись о загруженном файле и возвращает её идентификатор. */
    private suspend fun insertUpload(): UploadId {
        val id = Uuid.generateV7()
        TestPostgres.db.sql(
            """
            INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
            VALUES (:id, :orgId, :userId, :key, 'scan.jpg', 'image/jpeg', 1024)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("userId", userId)
            .bind("key", "uploads/$id")
            .execute()
        return id.toUploadId()
    }

    /** Создаёт клиента так же, как форма веб-клиента: только имя, пол по умолчанию. */
    private suspend fun newClient(): ClientId {
        val id = ClientId.new()
        either {
            TestPostgres.db.transaction {
                context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE).save() }
            }
        }.getOrElse { fail("Setup failed: $it") }
        return id
    }

    /** Количество документов клиента [clientId] в базе. */
    private suspend fun countDocs(clientId: ClientId): Long =
        TestPostgres.db.sql("SELECT COUNT(*) FROM client_docs WHERE client_id = :id")
            .bind("id", clientId)
            .firstOrNull { it.asLong(0) } ?: 0L

    @Test
    fun `attachDoc и save сохраняют документ и пишут событие аудита`() =
        runTest {
            val clientId = newClient()
            val uploadId = insertUpload()
            val doc = clientDoc(uploadId, "scan.jpg")

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        (clients.byId(clientId) as ActiveClient).attachDoc(doc).save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countDocs(clientId))
            val auditTypes =
                TestPostgres.db.sql("SELECT action_type FROM audit_logs WHERE entity_type = 'client_doc' AND entity_id = :id")
                    .bind("id", doc.id)
                    .list { it.asString("action_type") }
            assertEquals(listOf("create"), auditTypes)
        }

    @Test
    fun `deleteDoc и save удаляют документ и пишут событие аудита`() =
        runTest {
            val clientId = newClient()
            val doc = clientDoc(insertUpload(), "scan.jpg")
            either {
                TestPostgres.db.transaction {
                    context(ctx) { (clients.byId(clientId) as ActiveClient).attachDoc(doc).save() }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) { (clients.byId(clientId) as ActiveClient).deleteDoc(doc.id).save() }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(0L, countDocs(clientId))
            val auditTypes =
                TestPostgres.db.sql("SELECT action_type FROM audit_logs WHERE entity_type = 'client_doc' AND entity_id = :id ORDER BY created_at")
                    .bind("id", doc.id)
                    .list { it.asString("action_type") }
            assertEquals(listOf("create", "delete"), auditTypes)
        }
}
