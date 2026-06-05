package org.athletica.crm.domain.clientnotes

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class DbClientNotesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val userId = UserId.new()
    private val otherUserId = UserId.new()
    private val authorId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()
    private val otherOrgEmployeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val otherOrgClientId = ClientId.new()

    private val notes = DbClientNotes()

    private fun ctx(
        id: EmployeeId = authorId,
        org: OrgId = orgId,
    ) = EmployeeRequestContext(
        lang = Lang.RU,
        userId = userId,
        orgId = org,
        branchId = branchId,
        employeeId = id,
        username = "test@example.com",
        clientIp = null,
        currency = Currency.RUB,
        permission = EmployeePermission(),
    )

    private val authorCtx = ctx()
    private val otherCtx = ctx(id = otherEmployeeId)
    private val otherOrgCtx = ctx(id = otherOrgEmployeeId, org = otherOrgId)

    private fun text(value: String): ClientNoteText = ClientNoteText.from(value).getOrElse { fail("Invalid test text: $value") }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Other Org").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "a@example.com").bind("hash", "h").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", otherUserId).bind("login", "b@example.com").bind("hash", "h").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", authorId).bind("orgId", orgId).bind("name", "Автор").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", otherEmployeeId).bind("orgId", orgId).bind("name", "Коллега").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", otherOrgEmployeeId).bind("orgId", otherOrgId).bind("name", "Чужой").execute()
            TestPostgres.db.sql(
                "INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)",
            )
                .bind("id", clientId).bind("orgId", orgId).bind("name", "Клиент").execute()
            TestPostgres.db.sql(
                "INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)",
            )
                .bind("id", otherOrgClientId).bind("orgId", otherOrgId).bind("name", "Чужой клиент").execute()
        }
    }

    private suspend fun rawDeletedAt(noteId: org.athletica.crm.core.entityids.ClientNoteId): String? =
        TestPostgres.db
            .sql("SELECT COALESCE(deleted_at::text, '') AS d FROM client_notes WHERE id = :id")
            .bind("id", noteId)
            .firstOrNull { row -> row.asString("d") }
            ?.takeIf { it.isNotEmpty() }

    private suspend fun countRows(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM client_notes")
            .firstOrNull { row -> row.asLong(0) } ?: 0L

    // ─── add() ────────────────────────────────────────────────────────────────

    @Test
    fun `add создаёт заметку с author=ctx employeeId и createdAt`() =
        runTest {
            val created =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.add(clientId, text("Забрать сумку"))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(authorId, created.authorId)
            assertEquals(clientId, created.clientId)
            assertEquals("Забрать сумку", created.text.value)
            assertEquals(1L, countRows())
        }

    // ─── list() ───────────────────────────────────────────────────────────────

    @Test
    fun `list возвращает заметки в порядке created_at DESC и без удалённых`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(authorCtx) {
                        notes.add(clientId, text("Первая"))
                        notes.add(clientId, text("Вторая"))
                        val toDelete = notes.add(clientId, text("Третья (удалю)"))
                        toDelete.delete()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val list =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.list(clientId)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(2, list.size)
            assertEquals("Вторая", list[0].text.value)
            assertEquals("Первая", list[1].text.value)
        }

    @Test
    fun `list изолирован по организации`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(authorCtx) {
                        notes.add(clientId, text("Видна автору org"))
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val foreignList =
                either {
                    TestPostgres.db.transaction {
                        context(otherOrgCtx) {
                            notes.list(clientId)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(0, foreignList.size)
        }

    // ─── byId() ───────────────────────────────────────────────────────────────

    @Test
    fun `byId возвращает ошибку CLIENT_NOTE_NOT_FOUND для чужой организации`() =
        runTest {
            val created =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.add(clientId, text("Только моя"))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(otherOrgCtx) {
                            notes.byId(created.id)
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("CLIENT_NOTE_NOT_FOUND", result.value.code)
        }

    // ─── withText() / save() ─────────────────────────────────────────────────

    @Test
    fun `withText автор проставляет updatedAt и сохраняет новый текст`() =
        runTest {
            val created =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.add(clientId, text("Старый"))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertNull(created.updatedAt)

            either {
                TestPostgres.db.transaction {
                    context(authorCtx) {
                        val updated = notes.byId(created.id).withText(text("Новый"))
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val reloaded =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.byId(created.id)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("Новый", reloaded.text.value)
            assertNotNull(reloaded.updatedAt)
        }

    @Test
    fun `withText чужого сотрудника возвращает PERMISSION_DENIED`() =
        runTest {
            val created =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.add(clientId, text("Моё"))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(otherCtx) {
                            val updated = notes.byId(created.id).withText(text("Подмена"))
                            updated.save()
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("PERMISSION_DENIED", result.value.code)

            val reloaded =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.byId(created.id)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }
            assertEquals("Моё", reloaded.text.value)
        }

    // ─── delete() ─────────────────────────────────────────────────────────────

    @Test
    fun `delete автором проставляет deleted_at и убирает заметку из list`() =
        runTest {
            val created =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.add(clientId, text("Удалю"))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertNull(rawDeletedAt(created.id))

            either {
                TestPostgres.db.transaction {
                    context(authorCtx) {
                        notes.byId(created.id).delete()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertNotNull(rawDeletedAt(created.id))

            val list =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.list(clientId)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }
            assertEquals(0, list.size)
        }

    @Test
    fun `delete чужой заметки возвращает PERMISSION_DENIED`() =
        runTest {
            val created =
                either {
                    TestPostgres.db.transaction {
                        context(authorCtx) {
                            notes.add(clientId, text("Моё"))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(otherCtx) {
                            notes.byId(created.id).delete()
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("PERMISSION_DENIED", result.value.code)
            assertNull(rawDeletedAt(created.id))
        }
}
