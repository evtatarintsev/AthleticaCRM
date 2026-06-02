package org.athletica.crm.domain

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldKey
import org.athletica.crm.core.customfields.toFieldKey
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
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
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "test@example.com",
            clientIp = null,
            permission = EmployeePermission(),
        )
    private val otherCtx =
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = otherOrgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "other@example.com",
            clientIp = null,
            permission = EmployeePermission(),
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
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `all возвращает определения в порядке display_order`() =
        runTest {
            val fields =
                listOf(
                    CustomFieldDefinition.Text(fieldKey = key("first"), label = "first"),
                    CustomFieldDefinition.Number(fieldKey = key("second"), label = "second"),
                    CustomFieldDefinition.Bool(fieldKey = key("third"), label = "third"),
                )

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll("client", fields)
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            definitions.all("client")
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(3, result.size)
            assertEquals(key("first"), result[0].fieldKey)
            assertEquals(key("second"), result[1].fieldKey)
            assertEquals(key("third"), result[2].fieldKey)
        }

    @Test
    fun `all не возвращает поля другой организации`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = key("org_field"), label = "org_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(otherCtx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `all не возвращает поля другого entityType`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = key("client_field"), label = "client_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("employee") }
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
                    CustomFieldDefinition.Text(fieldKey = key("text_f"), label = "text_f"),
                    CustomFieldDefinition.Number(fieldKey = key("num_f"), label = "num_f"),
                    CustomFieldDefinition.Date(fieldKey = key("date_f"), label = "date_f"),
                    CustomFieldDefinition.Bool(fieldKey = key("bool_f"), label = "bool_f"),
                    CustomFieldDefinition.Phone(fieldKey = key("phone_f"), label = "phone_f"),
                    CustomFieldDefinition.Email(fieldKey = key("email_f"), label = "email_f"),
                    CustomFieldDefinition.Url(fieldKey = key("url_f"), label = "url_f"),
                    CustomFieldDefinition.Select(fieldKey = key("sel_f"), label = "sel_f", options = listOf("A", "B")),
                )

            either {
                TestPostgres.db.transaction {
                    context(ctx) { definitions.saveAll("client", fields) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(8, result.size)
        }

    @Test
    fun `saveAll корректно сохраняет Select с options`() =
        runTest {
            val options = listOf("beginner", "intermediate", "advanced")
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Select(fieldKey = key("level"), label = "level", options = options)),
                        )
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
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
                    CustomFieldDefinition.Number(fieldKey = key("age"), label = "age", minValue = 0, maxValue = 120),
                    CustomFieldDefinition.Text(fieldKey = key("note"), label = "note", minLength = 1, maxLength = 200),
                )

            either {
                TestPostgres.db.transaction {
                    context(ctx) { definitions.saveAll("client", fields) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val number = assertIs<CustomFieldDefinition.Number>(result.first { it.fieldKey == key("age") })
            assertEquals(0L, number.minValue)
            assertEquals(120L, number.maxValue)
            val text = assertIs<CustomFieldDefinition.Text>(result.first { it.fieldKey == key("note") })
            assertEquals(1, text.minLength)
            assertEquals(200, text.maxLength)
        }

    @Test
    fun `saveAll заменяет существующие поля новым набором`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(
                                CustomFieldDefinition.Text(fieldKey = key("old_one"), label = "old_one"),
                                CustomFieldDefinition.Number(fieldKey = key("old_two"), label = "old_two"),
                            ),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Bool(fieldKey = key("new_one"), label = "new_one")),
                        )
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.size)
            assertEquals(key("new_one"), result.single().fieldKey)
        }

    @Test
    fun `saveAll с пустым списком удаляет все поля`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Text(fieldKey = key("to_delete"), label = "to_delete")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) { definitions.saveAll("client", emptyList()) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(result.isEmpty())
        }

    @Test
    fun `saveAll не затрагивает поля другого entityType`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "employee",
                            listOf(CustomFieldDefinition.Text(fieldKey = key("emp_field"), label = "emp_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        definitions.saveAll(
                            "client",
                            listOf(CustomFieldDefinition.Number(fieldKey = key("client_field"), label = "client_field")),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val employeeFields =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("employee") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, employeeFields.size)
            assertEquals(key("emp_field"), employeeFields.single().fieldKey)
        }

    @Test
    fun `saveAll сохраняет флаги isRequired isSearchable isSortable`() =
        runTest {
            val def =
                CustomFieldDefinition.Text(
                    fieldKey = key("flagged"),
                    label = "flagged",
                    isRequired = true,
                    isSearchable = true,
                    isSortable = true,
                )
            either {
                TestPostgres.db.transaction {
                    context(ctx) { definitions.saveAll("client", listOf(def)) }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.all("client") }
                    }
                }.getOrElse { fail("Unexpected error: $it") }
                    .single()

            assertTrue(result.isRequired)
            assertTrue(result.isSearchable)
            assertTrue(result.isSortable)
        }

    private fun key(value: String): CustomFieldKey = value.toFieldKey().getOrElse { error("Невалидный тестовый CustomFieldKey: $value") }
}
