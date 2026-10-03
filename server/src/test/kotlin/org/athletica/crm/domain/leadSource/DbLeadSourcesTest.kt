package org.athletica.crm.domain.leadSource

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditFilter
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DB-тесты справочника источников: пояснение при создании, обновлении и в аудите. */
class DbLeadSourcesTest {
    private val fixture = ScheduleFixture()
    private val audit = AuditLogStub()
    private val leadSources = AuditLeadSources(DbLeadSources(), audit)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Пояснение из [raw]; ошибка роняет тест. */
    private fun description(raw: String): LeadSourceDescription = LeadSourceDescription.from(raw, Lang.RU).getOrElse { error(it.toString()) }

    private suspend fun create(name: String, description: String): LeadSourceId {
        val id = LeadSourceId.new()
        fixture.inContext { leadSources.new(id, name, description(description)).save() }
        return id
    }

    /** Название и пояснение источников организации в порядке справочника. */
    private suspend fun listed(): List<Pair<String, String>> = fixture.inContext { leadSources.list().map { it.name to it.description.value } }

    @Test
    fun `источник создаётся с пояснением и с пустым пояснением`() =
        runTest {
            fixture.setUp()
            fixture.insertActor()
            create("Партнёры", "Фитнес-клубы и магазины-партнёры")
            create("Другое", "")

            assertEquals(listOf("Другое" to "", "Партнёры" to "Фитнес-клубы и магазины-партнёры"), listed())
        }

    @Test
    fun `обновление меняет название и пояснение`() =
        runTest {
            fixture.setUp()
            fixture.insertActor()
            val id = create("Сайт", "")

            fixture.inContext { leadSources.byId(id).withNew("Сайт и лендинги", description("Сайт организации и посадочные страницы")).save() }

            assertEquals(listOf("Сайт и лендинги" to "Сайт организации и посадочные страницы"), listed())
        }

    @Test
    fun `пояснение очищается до пустого`() =
        runTest {
            fixture.setUp()
            fixture.insertActor()
            val id = create("Сайт", "Сайт организации")

            fixture.inContext { leadSources.byId(id).withNew("Сайт", LeadSourceDescription.EMPTY).save() }

            assertEquals(listOf("Сайт" to ""), listed())
        }

    @Test
    fun `источник, созданный в обход приложения, получает пустое пояснение`() =
        runTest {
            fixture.setUp()
            fixture.insertActor()
            TestPostgres.db
                .sql("INSERT INTO lead_sources (org_id, name) VALUES (:orgId, 'Старый источник')")
                .bind("orgId", fixture.orgId)
                .execute()

            assertEquals(listOf("Старый источник" to ""), listed())
        }

    @Test
    fun `пояснение попадает в аудит`() =
        runTest {
            fixture.setUp()
            fixture.insertActor()
            val id = create("Мероприятие", "Соревнования и открытые тренировки")

            val record = audit.events.single { it.entityType == "lead_source" && it.entityId == id.value }
            assertTrue(record.data.orEmpty().contains("\"description\":\"Соревнования и открытые тренировки\""), record.data)
        }
}

/** Журнал аудита в памяти: запись в `audit_logs` требует настоящего пользователя. */
private class AuditLogStub : AuditLog {
    /** Записанные события по порядку. */
    val events: MutableList<AuditEvent> = mutableListOf()

    context(tr: Transaction)
    override suspend fun log(event: AuditEvent) {
        events.add(event)
    }

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun list(filter: AuditFilter): List<AuditEvent> = events

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun count(filter: AuditFilter): Long = events.size.toLong()
}
