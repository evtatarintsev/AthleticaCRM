package org.athletica.crm.usecases

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.usecases.customfields.getCustomFields
import org.athletica.crm.usecases.customfields.saveCustomFields
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class CustomFieldsUseCaseTest {
    private val orgId = OrgId.new()

    private val ctx =
        RequestContext(
            Lang.EN,
            UserId.new(),
            orgId,
            BranchId.new(),
            EmployeeId.new(),
            "test@example.com",
            null,
            EmployeePermission(),
        )

    private val definitions = DbCustomFieldDefinitions()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db
                .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId)
                .bind("name", "Test Org")
                .execute()
        }
    }

    // ─── getCustomFields ──────────────────────────────────────────────────────

    @Test
    fun `getCustomFields возвращает пустой список для новой организации`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { getCustomFields(definitions, "client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getCustomFields возвращает ранее сохранённые поля`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        saveCustomFields(
                            definitions,
                            "client",
                            listOf(
                                CustomFieldDefinition.Select(
                                    fieldKey = "age_group",
                                    label = "age_group",
                                    options = listOf("adult", "junior"),
                                ),
                            ),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { getCustomFields(definitions, "client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.size)
            val select = assertIs<CustomFieldDefinition.Select>(result.single())
            assertEquals("age_group", select.fieldKey)
            assertEquals(listOf("adult", "junior"), select.options)
        }

    // ─── saveCustomFields ─────────────────────────────────────────────────────

    @Test
    fun `saveCustomFields возвращает сохранённый список`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            saveCustomFields(
                                definitions,
                                "client",
                                listOf(
                                    CustomFieldDefinition.Text(fieldKey = "name", label = "name"),
                                    CustomFieldDefinition.Number(fieldKey = "age", label = "age"),
                                ),
                            )
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(2, result.size)
            assertEquals("name", result[0].fieldKey)
            assertEquals("age", result[1].fieldKey)
        }

    @Test
    fun `saveCustomFields с пустым списком очищает набор полей`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        saveCustomFields(
                            definitions,
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = "to_remove", label = "to_remove")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { saveCustomFields(definitions, "client", emptyList()) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `saveCustomFields перезаписывает предыдущий набор полей`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        saveCustomFields(
                            definitions,
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = "old", label = "old")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            saveCustomFields(
                                definitions,
                                "client",
                                listOf(CustomFieldDefinition.Number(fieldKey = "new", label = "new")),
                            )
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.size)
            assertEquals("new", result.single().fieldKey)
        }

    // ─── валидация field_key ───────────────────────────────────────────────────

    @Test
    fun `saveCustomFields возвращает INVALID_FIELD_KEY для ключа с пробелом`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            saveCustomFields(
                                definitions,
                                "client",
                                listOf(CustomFieldDefinition.Text(fieldKey = "INVALID KEY", label = "x")),
                            )
                        }
                    }
                }

            assertEquals(
                "INVALID_FIELD_KEY",
                assertIs<Either.Left<DomainError>>(result).value.code,
            )
        }

    @Test
    fun `saveCustomFields возвращает INVALID_FIELD_KEY для ключа с заглавными буквами`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            saveCustomFields(
                                definitions,
                                "client",
                                listOf(CustomFieldDefinition.Text(fieldKey = "AgeGroup", label = "x")),
                            )
                        }
                    }
                }

            assertEquals(
                "INVALID_FIELD_KEY",
                assertIs<Either.Left<DomainError>>(result).value.code,
            )
        }

    @Test
    fun `saveCustomFields возвращает INVALID_FIELD_KEY для ключа с дефисом`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            saveCustomFields(
                                definitions,
                                "client",
                                listOf(CustomFieldDefinition.Text(fieldKey = "age-group", label = "x")),
                            )
                        }
                    }
                }

            assertEquals(
                "INVALID_FIELD_KEY",
                assertIs<Either.Left<DomainError>>(result).value.code,
            )
        }

    @Test
    fun `saveCustomFields принимает ключ из строчных букв и подчёркивания`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            saveCustomFields(
                                definitions,
                                "client",
                                listOf(CustomFieldDefinition.Text(fieldKey = "age_group", label = "age_group")),
                            )
                        }
                    }
                }

            assertIs<Either.Right<List<CustomFieldDefinition>>>(result)
        }
}
