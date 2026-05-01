package org.athletica.crm.domain

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldType
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
                    field("first", CustomFieldType.Text),
                    field("second", CustomFieldType.Number),
                    field("third", CustomFieldType.Boolean),
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
                        definitions.saveAll("client", listOf(field("org1_field", CustomFieldType.Text)))
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
                        definitions.saveAll("client", listOf(field("client_field", CustomFieldType.Text)))
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
                    field("text_f", CustomFieldType.Text),
                    field("num_f", CustomFieldType.Number),
                    field("date_f", CustomFieldType.Date),
                    field("bool_f", CustomFieldType.Boolean),
                    field("phone_f", CustomFieldType.Phone),
                    field("email_f", CustomFieldType.Email),
                    field("url_f", CustomFieldType.Url),
                    field("sel_f", CustomFieldType.Select(listOf("A", "B"))),
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
                        definitions.saveAll("client", listOf(field("level", CustomFieldType.Select(options))))
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val select = assertIs<CustomFieldType.Select>(result.single().fieldType)
            assertEquals(options, select.options)
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
                                field("old1", CustomFieldType.Text),
                                field("old2", CustomFieldType.Number),
                            ),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll("client", listOf(field("new1", CustomFieldType.Boolean)))
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
                        definitions.saveAll("client", listOf(field("to_delete", CustomFieldType.Text)))
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
                        definitions.saveAll("employee", listOf(field("emp_field", CustomFieldType.Text)))
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        definitions.saveAll("client", listOf(field("client_field", CustomFieldType.Number)))
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

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
                field("f", CustomFieldType.Text).copy(
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

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun field(
        key: String,
        type: CustomFieldType,
    ) = org.athletica.crm.core.customfields.CustomFieldDefinition(
        orgId = orgId,
        entityType = "client",
        fieldKey = key,
        label = key,
        fieldType = type,
        isRequired = false,
        isSearchable = false,
        isSortable = false,
    )
}
