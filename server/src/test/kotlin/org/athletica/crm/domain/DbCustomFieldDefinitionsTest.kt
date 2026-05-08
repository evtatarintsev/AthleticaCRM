package org.athletica.crm.domain

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
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class DbCustomFieldDefinitionsTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()

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
    private val otherCtx =
        RequestContext(
            Lang.EN,
            UserId.new(),
            otherOrgId,
            BranchId.new(),
            EmployeeId.new(),
            "other@example.com",
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
                .bind("name", "Орг 1")
                .execute()
            TestPostgres.db
                .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId)
                .bind("name", "Орг 2")
                .execute()
        }
    }

    // ─── all ──────────────────────────────────────────────────────────────────

    @Test
    fun `all возвращает пустой список если нет определений`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `all возвращает определения в порядке display_order`() =
        runTest {
            val fields =
                listOf(
                    CustomFieldDefinition.Text(fieldKey = "first", label = "first"),
                    CustomFieldDefinition.Number(fieldKey = "second", label = "second"),
                    CustomFieldDefinition.Bool(fieldKey = "third", label = "third"),
                )

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { definitions.saveAll("client", fields) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(3, result.size)
            assertEquals("first", result[0].fieldKey)
            assertEquals("second", result[1].fieldKey)
            assertEquals("third", result[2].fieldKey)
        }

    @Test
    fun `all не возвращает поля другой организации`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = "org1_field", label = "org1_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `all не возвращает поля другого entityType`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = "client_field", label = "client_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("employee") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    // ─── saveAll ──────────────────────────────────────────────────────────────

    @Test
    fun `saveAll сохраняет поля всех типов`() =
        runTest {
            val fields =
                listOf(
                    CustomFieldDefinition.Text(fieldKey = "text_f", label = "text_f"),
                    CustomFieldDefinition.Number(fieldKey = "num_f", label = "num_f"),
                    CustomFieldDefinition.Date(fieldKey = "date_f", label = "date_f"),
                    CustomFieldDefinition.Bool(fieldKey = "bool_f", label = "bool_f"),
                    CustomFieldDefinition.Phone(fieldKey = "phone_f", label = "phone_f"),
                    CustomFieldDefinition.Email(fieldKey = "email_f", label = "email_f"),
                    CustomFieldDefinition.Url(fieldKey = "url_f", label = "url_f"),
                    CustomFieldDefinition.Select(fieldKey = "sel_f", label = "sel_f", options = listOf("A", "B")),
                )

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { definitions.saveAll("client", fields) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(8, result.size)
        }

    @Test
    fun `saveAll корректно сохраняет Select с options`() =
        runTest {
            val options = listOf("beginner", "intermediate", "advanced")
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Select(fieldKey = "level", label = "level", options = options)),
                        )
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val select = assertIs<CustomFieldDefinition.Select>(result.single())
            assertEquals(options, select.options)
        }

    @Test
    fun `saveAll сохраняет границы Number и Text`() =
        runTest {
            val fields =
                listOf(
                    CustomFieldDefinition.Number(fieldKey = "age", label = "age", minValue = 0, maxValue = 120),
                    CustomFieldDefinition.Text(fieldKey = "note", label = "note", minLength = 1, maxLength = 200),
                )

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { definitions.saveAll("client", fields) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val number = assertIs<CustomFieldDefinition.Number>(result.first { it.fieldKey == "age" })
            assertEquals(0L, number.minValue)
            assertEquals(120L, number.maxValue)
            val text = assertIs<CustomFieldDefinition.Text>(result.first { it.fieldKey == "note" })
            assertEquals(1, text.minLength)
            assertEquals(200, text.maxLength)
        }

    @Test
    fun `saveAll заменяет существующие поля новым набором`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(
                                CustomFieldDefinition.Text(fieldKey = "old1", label = "old1"),
                                CustomFieldDefinition.Number(fieldKey = "old2", label = "old2"),
                            ),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Bool(fieldKey = "new1", label = "new1")),
                        )
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.size)
            assertEquals("new1", result.single().fieldKey)
        }

    @Test
    fun `saveAll с пустым списком удаляет все поля`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = "to_delete", label = "to_delete")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { definitions.saveAll("client", emptyList()) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `saveAll не затрагивает поля другого entityType`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "employee",
                            listOf(CustomFieldDefinition.Text(fieldKey = "emp_field", label = "emp_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Number(fieldKey = "client_field", label = "client_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val employeeFields =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("employee") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, employeeFields.size)
            assertEquals("emp_field", employeeFields.single().fieldKey)
        }

    @Test
    fun `saveAll сохраняет флаги isRequired isSearchable isSortable`() =
        runTest {
            val def =
                CustomFieldDefinition.Text(
                    fieldKey = "f",
                    label = "f",
                    isRequired = true,
                    isSearchable = true,
                    isSortable = true,
                )
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { definitions.saveAll("client", listOf(def)) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }
                    .single()

            assertTrue(result.isRequired)
            assertTrue(result.isSearchable)
            assertTrue(result.isSortable)
        }
}
