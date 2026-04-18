package org.athletica.crm.domain.audit

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UserId
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class PostgresAuditLogTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val auditLog = PostgresAuditLog()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db
                .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId)
                .bind("name", "Audit Test Org")
                .execute()
            TestPostgres.db
                .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId)
                .bind("login", "${userId.value}@example.com")
                .bind("hash", "hash")
                .execute()
        }
    }

    private suspend fun countRows(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM audit_logs WHERE org_id = :orgId")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asLong(0) }
            ?: 0L

    // ─────────────────────────── tests ─────────────────────────────

    @Test
    fun `log сохраняет одно событие в БД`() =
        runTest {
            val entityId = Uuid.generateV7()
            TestPostgres.db.transaction {
                context(this) {
                    auditLog.log(
                        AuditEvent(
                            orgId = orgId,
                            userId = userId,
                            username = "user@example.com",
                            actionType = AuditActionType.CREATE,
                            entityType = "client",
                            entityId = entityId,
                            data = """{"name":"Иван"}""",
                            ipAddress = "192.168.1.1",
                        ),
                    )
                }
            }
            assertEquals(1L, countRows())
        }

    @Test
    fun `log сохраняет все поля события корректно`() =
        runTest {
            val entityId = Uuid.generateV7()
            TestPostgres.db.transaction {
                context(this) {
                    auditLog.log(
                        AuditEvent(
                            orgId = orgId,
                            userId = userId,
                            username = "admin@example.com",
                            actionType = AuditActionType.UPDATE,
                            entityType = "client",
                            entityId = entityId,
                            data = """{"name":"Пётр"}""",
                            ipAddress = "10.0.0.1",
                        ),
                    )
                }
            }

            val row =
                TestPostgres.db
                    .sql(
                        """
                        SELECT org_id, user_id, username, action_type, entity_type, entity_id, data::text, ip_address
                        FROM audit_logs
                        WHERE org_id = :orgId
                        """.trimIndent(),
                    ).bind("orgId", orgId)
                    .firstOrNull { r ->
                        mapOf(
                            "orgId" to r.asUuid("org_id"),
                            "userId" to r.asUuidOrNull("user_id"),
                            "username" to r.asString("username"),
                            "actionType" to r.asString("action_type"),
                            "entityType" to r.asStringOrNull("entity_type"),
                            "entityId" to r.asUuidOrNull("entity_id"),
                            "data" to r.asStringOrNull("data"),
                            "ipAddress" to r.asStringOrNull("ip_address"),
                        )
                    }

            assertNotNull(row)
            assertEquals(orgId.value, row["orgId"])
            assertEquals(userId.value, row["userId"])
            assertEquals("admin@example.com", row["username"])
            assertEquals("update", row["actionType"])
            assertEquals("client", row["entityType"])
            assertEquals(entityId, row["entityId"])
            assertEquals("10.0.0.1", row["ipAddress"])
            assertNotNull(row["data"])
        }

    @Test
    fun `log сохраняет событие без опциональных полей`() =
        runTest {
            TestPostgres.db.transaction {
                context(this) {
                    auditLog.log(
                        AuditEvent(
                            orgId = orgId,
                            userId = null,
                            username = "system",
                            actionType = AuditActionType.EXPORT,
                            entityType = null,
                            entityId = null,
                            data = null,
                            ipAddress = null,
                        ),
                    )
                }
            }

            val row =
                TestPostgres.db
                    .sql(
                        """
                        SELECT user_id, entity_type, entity_id, data, ip_address
                        FROM audit_logs
                        WHERE org_id = :orgId
                        """.trimIndent(),
                    ).bind("orgId", orgId)
                    .firstOrNull { r ->
                        listOf(
                            r.asUuidOrNull("user_id"),
                            r.asStringOrNull("entity_type"),
                            r.asUuidOrNull("entity_id"),
                            r.asStringOrNull("data"),
                            r.asStringOrNull("ip_address"),
                        )
                    }

            assertNotNull(row)
            row.forEach { assertNull(it) }
        }

    @Test
    fun `log сохраняет несколько событий`() =
        runTest {
            TestPostgres.db.transaction {
                context(this) {
                    repeat(5) { i ->
                        auditLog.log(
                            AuditEvent(
                                orgId = orgId,
                                userId = userId,
                                username = "user@example.com",
                                actionType = AuditActionType.CREATE,
                                entityType = "client",
                                entityId = Uuid.generateV7(),
                                data = """{"index":$i}""",
                                ipAddress = "127.0.0.1",
                            ),
                        )
                    }
                }
            }
            assertEquals(5L, countRows())
        }

    @Test
    fun `log сохраняет все типы действий`() =
        runTest {
            TestPostgres.db.transaction {
                context(this) {
                    for (actionType in AuditActionType.entries) {
                        auditLog.log(
                            AuditEvent(
                                orgId = orgId,
                                userId = userId,
                                username = "user@example.com",
                                actionType = actionType,
                            ),
                        )
                    }
                }
            }

            assertEquals(AuditActionType.entries.size.toLong(), countRows())

            val savedCodes =
                TestPostgres.db
                    .sql("SELECT action_type FROM audit_logs WHERE org_id = :orgId ORDER BY action_type")
                    .bind("orgId", orgId)
                    .list { row -> row.asString(0) }
                    .toSet()

            val expectedCodes = AuditActionType.entries.map { it.code }.toSet()
            assertEquals(expectedCodes, savedCodes)
        }

    @Test
    fun `log balance_adjust сохраняет данные операции`() =
        runTest {
            val clientId = Uuid.generateV7()
            TestPostgres.db.transaction {
                context(this) {
                    auditLog.log(
                        AuditEvent(
                            orgId = orgId,
                            userId = userId,
                            username = "admin@example.com",
                            actionType = AuditActionType.BALANCE_ADJUST,
                            entityType = "client",
                            entityId = clientId,
                            data = """{"amount":500.0,"operationType":"admin_credit","note":"Бонус"}""",
                            ipAddress = "127.0.0.1",
                        ),
                    )
                }
            }

            val savedEntityId =
                TestPostgres.db
                    .sql("SELECT entity_id FROM audit_logs WHERE org_id = :orgId AND action_type = 'balance_adjust'")
                    .bind("orgId", orgId)
                    .firstOrNull { row -> row.asUuid("entity_id") }

            assertEquals(clientId, savedEntityId)
        }

    @Test
    fun `события из разных организаций не смешиваются`() =
        runTest {
            val otherOrgId = OrgId.new()
            TestPostgres.db
                .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId)
                .bind("name", "Other Org")
                .execute()

            TestPostgres.db.transaction {
                context(this) {
                    auditLog.log(
                        AuditEvent(
                            orgId = orgId,
                            userId = userId,
                            username = "user1@example.com",
                            actionType = AuditActionType.CREATE,
                        ),
                    )
                    auditLog.log(
                        AuditEvent(
                            orgId = otherOrgId,
                            userId = userId,
                            username = "user2@example.com",
                            actionType = AuditActionType.DELETE,
                        ),
                    )
                }
            }

            assertEquals(1L, countRows())
        }
}
