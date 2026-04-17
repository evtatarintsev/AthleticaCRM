package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Gender
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toUploadId
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.clients.clientDoc
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

    private val ctx = RequestContext(Lang.EN, userId, orgId, "test@example.com", null)
    private val otherCtx = RequestContext(Lang.EN, UserId.new(), otherOrgId, "test@example.com", null)

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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Иван Петров", null, null, Gender.MALE) }
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
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
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
            assertEquals(0.0, result.balance)
        }

    @Test
    fun `new сохраняет avatarId`() =
        runTest {
            val uploadId = insertUpload()
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Первый", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.new(id, "Второй", null, null, Gender.MALE) }
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Клиент орг 1", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this) { clients.new(id, "Клиент орг 2", null, null, Gender.MALE) }
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Пётр Сидоров", null, birthday, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val client =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.byId(id) }
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
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.byId(ClientId.new()) }
                    }
                }
            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId не возвращает клиента другой организации`() =
        runTest {
            val id = ClientId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Чужой клиент", null, null, Gender.FEMALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this) { clients.byId(id) }
                    }
                }
            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId загружает документы клиента`() =
        runTest {
            val id = ClientId.new()
            val uploadId = insertUpload()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val client = clients.new(id, "Клиент", null, null, Gender.MALE)
                        context(ctx) { client.attachDoc(clientDoc(uploadId, "Договор")) }.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val client =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.byId(id) }
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Клиент", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }
            runBlocking {
                TestPostgres.db.sql("INSERT INTO groups (id, org_id, name) VALUES (:id, :orgId, :name)")
                    .bind("id", groupId).bind("orgId", orgId).bind("name", "Группа А").execute()
                TestPostgres.db.sql("INSERT INTO client_groups (client_id, group_id) VALUES (:clientId, :groupId)")
                    .bind("clientId", id).bind("groupId", groupId).execute()
            }

            val client =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.byId(id) }
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Старое имя", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        clients.byId(id)
                            .withNew("Новое имя", null, newBirthday, Gender.FEMALE)
                            .save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val updated =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.byId(id) }
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { clients.new(id, "Клиент", null, null, Gender.MALE) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        context(ctx) { clients.byId(id).attachDoc(clientDoc(uploadId, "Паспорт")) }.save()
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

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        context(ctx) { clients.new(id, "Клиент", null, null, Gender.MALE).attachDoc(doc) }.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }
            assertEquals(1L, countDocsInDb(id))

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        context(ctx, this) { clients.byId(id).deleteDoc(doc.id) }.save()
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

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        context(ctx) {
                            clients.new(id, "Клиент", null, null, Gender.MALE)
                                .attachDoc(clientDoc(upload1, "Первый"))
                                .attachDoc(clientDoc(upload2, "Второй"))
                        }.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }
            assertEquals(2L, countDocsInDb(id))

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val doc3 = clientDoc(upload3, "Третий")
                        context(ctx) {
                            // удаляем все старые, добавляем один новый
                            clients.byId(id)
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
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        clients.new(id1, "Клиент 1", null, null, Gender.MALE)
                        clients.new(id2, "Клиент 2", null, null, Gender.FEMALE)
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        clients.byId(id1).withNew("Изменённый", null, null, Gender.MALE).save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val client2 =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { clients.byId(id2) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("Клиент 2", client2.name)
            assertEquals(Gender.FEMALE, client2.gender)
        }
}
