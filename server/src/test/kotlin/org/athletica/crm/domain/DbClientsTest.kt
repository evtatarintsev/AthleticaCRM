package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.ArchivedClient
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.clients.clientDoc
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbClientsTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()
    private val branchId = Uuid.generateV7()

    private val ctx = EmployeeRequestContext(lang = Lang.EN, orgId = orgId, currency = Currency.RUB, userId = userId, branchId = branchId.toBranchId(), employeeId = employeeId, username = "test@example.com", clientIp = null, permission = EmployeePermission())
    private val otherCtx = EmployeeRequestContext(lang = Lang.EN, orgId = otherOrgId, currency = Currency.RUB, userId = UserId.new(), branchId = BranchId.new(), employeeId = otherEmployeeId, username = "test@example.com", clientIp = null, permission = EmployeePermission())

    private val clients = DbClients()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Org 2").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "test@example.com").bind("hash", "hash").execute()
            TestPostgres.db.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", branchId).bind("orgId", orgId).bind("name", "Основной").execute()
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private suspend fun insertUpload(orgId: OrgId = this.orgId): UploadId {
        val id = Uuid.generateV7()
        TestPostgres.db.sql(
            """
            INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
            VALUES (:id, :orgId, :userId, :key, :name, 'application/pdf', 1024)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("userId", userId)
            .bind("key", "uploads/$id")
            .bind("name", "doc.pdf")
            .execute()
        return id.toUploadId()
    }

    private suspend fun countClientsInDb(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM clients")
            .firstOrNull { row -> row.asLong(0) } ?: 0L

    private suspend fun countDocsInDb(clientId: ClientId): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM client_docs WHERE client_id = :id")
            .bind("id", clientId)
            .firstOrNull { row -> row.asLong(0) } ?: 0L

    // ─── new() ────────────────────────────────────────────────────────────────

    @Test
    fun `new создаёт клиента в базе данных`() =
        runTest {
            val id = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Иван Петров", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countClientsInDb())
        }

    @Test
    fun `new возвращает клиента с переданными полями`() =
        runTest {
            val id = ClientId.new()
            val birthday = LocalDate(1990, 5, 15)
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            clients.new(id, "Мария Иванова", null, birthday, Gender.FEMALE)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(id, result.id)
            assertEquals("Мария Иванова", result.name)
            assertEquals(birthday, result.birthday)
            assertEquals(Gender.FEMALE, result.gender)
            assertNull(result.avatarId)
            assertTrue(result.docs.isEmpty())
            assertTrue(result.groups.isEmpty())
        }

    @Test
    fun `new сохраняет avatarId`() =
        runTest {
            val uploadId = insertUpload()
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            clients.new(ClientId.new(), "Клиент", uploadId, null, Gender.MALE)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(uploadId, result.avatarId)
        }

    @Test
    fun `new возвращает ошибку CLIENT_ALREADY_EXISTS при дублировании id`() =
        runTest {
            val id = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Первый", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.new(id, "Второй", null, null, Gender.MALE) }
                    }
                }
            assertEquals("CLIENT_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(1L, countClientsInDb()) // в базе по-прежнему один клиент
        }

    @Test
    fun `new изолирует клиентов по организациям`() =
        runTest {
            // один и тот же id нельзя использовать дважды — PK глобальный
            val id = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Клиент орг 1", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(otherCtx) { clients.new(id, "Клиент орг 2", null, null, Gender.MALE) }
                    }
                }
            assertIs<Either.Left<DomainError>>(result)
        }

    // ─── byId() ───────────────────────────────────────────────────────────────

    @Test
    fun `byId возвращает клиента с корректными полями`() =
        runTest {
            val id = ClientId.new()
            val birthday = LocalDate(1985, 3, 22)
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Пётр Сидоров", null, birthday, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val client =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.byId(id) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(id, client.id)
            assertEquals("Пётр Сидоров", client.name)
            assertEquals(birthday, client.birthday)
            assertEquals(Gender.MALE, client.gender)
        }

    @Test
    fun `byId возвращает CLIENT_NOT_FOUND для несуществующего id`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.byId(ClientId.new()) }
                    }
                }
            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId не возвращает клиента другой организации`() =
        runTest {
            val id = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Чужой клиент", null, null, Gender.FEMALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(otherCtx) { clients.byId(id) }
                    }
                }
            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId загружает документы клиента`() =
        runTest {
            val id = ClientId.new()
            val uploadId = insertUpload()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        val client = clients.new(id, "Клиент", null, null, Gender.MALE)
                        context(ctx) { client.attachDoc(clientDoc(uploadId, "Договор")) }.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val client =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.byId(id) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, client.docs.size)
            assertEquals("Договор", client.docs.first().name)
            assertEquals(uploadId, client.docs.first().uploadId)
        }

    @Test
    fun `byId загружает группы клиента`() =
        runTest {
            val id = ClientId.new()
            val groupId = Uuid.generateV7()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }
            runBlocking {
                TestPostgres.db.sql("INSERT INTO groups (id, org_id, name, branch_id) VALUES (:id, :orgId, :name, :branchId)")
                    .bind("id", groupId).bind("orgId", orgId).bind("name", "Группа А").bind("branchId", branchId).execute()
                TestPostgres.db.sql("INSERT INTO enrollments (client_id, group_id) VALUES (:clientId, :groupId)")
                    .bind("clientId", id).bind("groupId", groupId).execute()
            }

            val client =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.byId(id) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, client.groups.size)
            assertEquals("Группа А", client.groups.first().name)
        }

    // ─── save() ───────────────────────────────────────────────────────────────

    @Test
    fun `save обновляет скалярные поля клиента`() =
        runTest {
            val id = ClientId.new()
            val newBirthday = LocalDate(2000, 1, 1)
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Старое имя", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        (clients.byId(id) as ActiveClient)
                            .withNew("Новое имя", null, newBirthday, Gender.FEMALE)
                            .save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val updated =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.byId(id) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("Новое имя", updated.name)
            assertEquals(newBirthday, updated.birthday)
            assertEquals(Gender.FEMALE, updated.gender)
        }

    @Test
    fun `save добавляет новый документ`() =
        runTest {
            val id = ClientId.new()
            val uploadId = insertUpload()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        context(ctx) { (clients.byId(id) as ActiveClient).attachDoc(clientDoc(uploadId, "Паспорт")) }.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countDocsInDb(id))
        }

    @Test
    fun `save удаляет документ`() =
        runTest {
            val id = ClientId.new()
            val uploadId = insertUpload()
            val doc = clientDoc(uploadId, "Документ на удаление")

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE).attachDoc(doc) }.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }
            assertEquals(1L, countDocsInDb(id))

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        context(ctx) { (clients.byId(id) as ActiveClient).deleteDoc(doc.id) }.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(0L, countDocsInDb(id))
        }

    @Test
    fun `save синхронизирует несколько документов`() =
        runTest {
            val id = ClientId.new()
            val upload1 = insertUpload()
            val upload2 = insertUpload()
            val upload3 = insertUpload()

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        context(ctx) {
                            clients.new(id, "Клиент", null, null, Gender.MALE)
                                .attachDoc(clientDoc(upload1, "Первый"))
                                .attachDoc(clientDoc(upload2, "Второй"))
                        }.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }
            assertEquals(2L, countDocsInDb(id))

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        val doc3 = clientDoc(upload3, "Третий")
                        context(ctx) {
                            // удаляем все старые, добавляем один новый
                            (clients.byId(id) as ActiveClient)
                                .let { c -> c.docs.fold(c) { acc, d -> acc.deleteDoc(d.id) } }
                                .attachDoc(doc3)
                        }.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countDocsInDb(id))
        }

    @Test
    fun `save не затрагивает данные другого клиента`() =
        runTest {
            val id1 = ClientId.new()
            val id2 = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        clients.new(id1, "Клиент 1", null, null, Gender.MALE)
                        clients.new(id2, "Клиент 2", null, null, Gender.FEMALE)
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        (clients.byId(id1) as ActiveClient).withNew("Изменённый", null, null, Gender.MALE).save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val client2 =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { clients.byId(id2) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("Клиент 2", client2.name)
            assertEquals(Gender.FEMALE, client2.gender)
        }

    // ─── archive() / restore() ─────────────────────────────────────────────────

    @Test
    fun `byId возвращает ActiveClient для нового клиента`() =
        runTest {
            val id = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val client =
                either {
                    TestPostgres.db.transaction { context(ctx) { clients.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertIs<ActiveClient>(client)
        }

    @Test
    fun `archive переводит клиента в ArchivedClient, restore возвращает обратно`() =
        runTest {
            val id = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) { (clients.byId(id) as ActiveClient).archive() }
                }
            }.getOrElse { fail("Archive failed: $it") }

            val archived =
                either {
                    TestPostgres.db.transaction { context(ctx) { clients.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }
            assertIs<ArchivedClient>(archived)

            either {
                TestPostgres.db.transaction {
                    context(ctx) { (clients.byId(id) as ArchivedClient).restore() }
                }
            }.getOrElse { fail("Restore failed: $it") }

            val restored =
                either {
                    TestPostgres.db.transaction { context(ctx) { clients.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }
            assertIs<ActiveClient>(restored)
        }

    @Test
    fun `list по умолчанию возвращает активных, с archived=true — архивных`() =
        runTest {
            val activeId = ClientId.new()
            val archivedId = ClientId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        clients.new(activeId, "Активный", null, null, Gender.MALE)
                        clients.new(archivedId, "Архивный", null, null, Gender.FEMALE)
                        (clients.byId(archivedId) as ActiveClient).archive()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val active =
                either {
                    TestPostgres.db.transaction { context(ctx) { clients.list() } }
                }.getOrElse { fail("Unexpected error: $it") }
            assertEquals(listOf(activeId), active.map { it.id })

            val archived =
                either {
                    TestPostgres.db.transaction { context(ctx) { clients.list(archived = true) } }
                }.getOrElse { fail("Unexpected error: $it") }
            assertEquals(listOf(archivedId), archived.map { it.id })
        }
}
