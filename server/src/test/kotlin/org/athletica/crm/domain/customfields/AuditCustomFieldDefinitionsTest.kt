package org.athletica.crm.domain.customfields

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.appJson
import org.athletica.crm.core.audit.AuditActionType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldKey
import org.athletica.crm.core.customfields.toFieldKey
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuditCustomFieldDefinitionsTest {
    private val orgId = OrgId.new()
    private val ctx = employeeContext(orgId = orgId)
    private val audit = AuditLogStub()
    private val db = DbCustomFieldDefinitions()
    private val definitions = AuditCustomFieldDefinitions(db, audit)

    @Before
    fun setUp() {
        orThrow {
            TestPostgres.truncate()
            runBlocking {
                TestPostgres.db
                    .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", orgId)
                    .bind("name", "Орг")
                    .execute()
            }
        }
    }

    /** Сохраняет [fields] через аудирующий декоратор и возвращает события этого сохранения. */
    private suspend fun save(fields: List<CustomFieldDefinition>): List<AuditEvent> =
        orThrow {
            audit.events.clear()
            TestPostgres.db.transaction { context(ctx) { definitions.saveAll("CLIENT", fields) } }
            audit.events.toList()
        }

    /** Идентификаторы сохранённых полей по ключу. */
    private suspend fun ids() = orThrow { TestPostgres.db.transaction { context(ctx) { db.stored("CLIENT").associate { it.definition.fieldKey to it.id.value } } } }

    private val school = CustomFieldDefinition.Text(key("school"), "Школа")
    private val size = CustomFieldDefinition.Number(key("size"), "Размер")
    private val uniform = CustomFieldDefinition.Select(key("uniform"), "Размер формы", options = listOf("S", "M"))

    @Test
    fun `добавление поля даёт одно событие CREATE`() =
        runTest {
            save(listOf(school, size))
            val events = save(listOf(school, size, uniform))

            val event = events.single()
            assertEquals(AuditActionType.CREATE, event.actionType)
            assertEquals("custom_field", event.entityType)
            assertEquals(ids()[key("uniform")], event.entityId)
            val data = appJson.parseToJsonElement(event.data!!).jsonObject
            assertEquals("CLIENT", data["entityType"]!!.jsonPrimitive.content)
            assertEquals(2, data["position"]!!.jsonPrimitive.int)
            assertEquals("Размер формы", data["definition"]!!.jsonObject["label"]!!.jsonPrimitive.content)
        }

    @Test
    fun `переименование и удаление дают UPDATE и DELETE`() =
        runTest {
            save(listOf(school, size))
            val before = ids()
            val events = save(listOf(school.copy(label = "Учебное заведение")))

            assertEquals(2, events.size)
            val update = events.single { it.actionType == AuditActionType.UPDATE }
            assertEquals(before[key("school")], update.entityId)
            assertTrue(update.data!!.contains("Учебное заведение"))
            val delete = events.single { it.actionType == AuditActionType.DELETE }
            assertEquals(before[key("size")], delete.entityId)
            assertTrue(delete.data!!.contains("Размер"))
        }

    @Test
    fun `смена порядка даёт UPDATE у сдвинутых полей`() =
        runTest {
            save(listOf(school, size, uniform))
            val events = save(listOf(size, school, uniform))

            val before = ids()
            assertEquals(setOf(before[key("school")], before[key("size")]), events.map { it.entityId }.toSet())
            assertTrue(events.all { it.actionType == AuditActionType.UPDATE })
        }

    @Test
    fun `удаление первого поля даёт только DELETE`() =
        runTest {
            save(listOf(school, size, uniform))
            val events = save(listOf(size, uniform))

            assertEquals(listOf(AuditActionType.DELETE), events.map { it.actionType })
        }

    @Test
    fun `вставка поля в начало даёт только CREATE`() =
        runTest {
            save(listOf(size, uniform))
            val events = save(listOf(school, size, uniform))

            assertEquals(listOf(AuditActionType.CREATE), events.map { it.actionType })
        }

    @Test
    fun `сохранение без изменений не пишет событий`() =
        runTest {
            save(listOf(school, size))
            assertTrue(save(listOf(school, size)).isEmpty())
        }

    @Test
    fun `ошибочное сохранение не пишет событий`() =
        runTest {
            save(listOf(school))
            audit.events.clear()
            val rejected =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { definitions.saveAll("CLIENT", listOf(CustomFieldDefinition.Select(key("empty"), "Пустой"))) }
                    }
                }
            assertIs<Either.Left<AppError>>(rejected)
            assertTrue(audit.events.isEmpty())
        }

    private fun key(value: String): CustomFieldKey = either { value.toFieldKey() }.getOrElse { error("Невалидный тестовый ключ: $value") }
}

/** Журнал аудита в памяти. */
private class AuditLogStub : AuditLog {
    /** Записанные события по порядку. */
    val events: MutableList<AuditEvent> = mutableListOf()

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun log(event: AuditEvent) {
        events.add(event)
    }
}
