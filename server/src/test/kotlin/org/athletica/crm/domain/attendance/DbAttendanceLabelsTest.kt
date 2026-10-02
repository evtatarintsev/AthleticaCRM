package org.athletica.crm.domain.attendance

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditFilter
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DB-тесты справочника меток: жизненный цикл метки, уникальность названия и аудит. */
class DbAttendanceLabelsTest {
    private val fixture = AttendanceFixture()
    private val audit = AuditLogStub()
    private val labels = AuditAttendanceLabels(DbAttendanceLabels(), audit)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun create(name: String, scope: AttendanceLabelScope = AttendanceLabelScope.ABSENT): AttendanceLabelId {
        val id = AttendanceLabelId.new()
        fixture.inContext { labels.new(id, name, scope).save() }
        return id
    }

    @Test
    fun `созданная метка попадает в конец справочника`() =
        runTest {
            fixture.setUp()
            create("Болеет")
            create("Опоздал", AttendanceLabelScope.PRESENT)

            val list = fixture.inContext { labels.list(includeArchived = false) }

            assertEquals(listOf("Болеет" to 1, "Опоздал" to 2), list.map { it.name to it.position })
            assertEquals(AttendanceLabelScope.PRESENT, list.last().scope)
        }

    @Test
    fun `метку можно переименовать и переместить`() =
        runTest {
            fixture.setUp()
            val first = create("Болеет")
            val second = create("Прогул")

            fixture.inContext { labels.byId(second).renamed("Не предупредил").movedTo(0).save() }

            val list = fixture.inContext { labels.list(includeArchived = false) }
            assertEquals(listOf(second, first), list.map { it.id })
            assertEquals("Не предупредил", list.first().name)
        }

    @Test
    fun `архивная метка скрыта из справочника и возвращается после восстановления`() =
        runTest {
            fixture.setUp()
            val id = create("Болеет")

            fixture.inContext { labels.byId(id).archived().save() }

            assertEquals(emptyList(), fixture.inContext { labels.list(includeArchived = false) }.map { it.id })
            assertEquals(listOf(true), fixture.inContext { labels.list(includeArchived = true) }.map { it.isArchived })

            fixture.inContext { labels.byId(id).restored().save() }

            assertEquals(listOf(false), fixture.inContext { labels.list(includeArchived = false) }.map { it.isArchived })
        }

    @Test
    fun `название, занятое в организации, отклоняется без учёта регистра`() =
        runTest {
            fixture.setUp()
            create("Болеет")

            val result = fixture.runInContext { labels.new(AttendanceLabelId.new(), "болеет", AttendanceLabelScope.ABSENT).save() }

            assertEquals("ATTENDANCE_LABEL_ALREADY_EXISTS", fixture.errorCode(result))
            assertEquals(1, fixture.inContext { labels.list(includeArchived = true) }.size)
        }

    @Test
    fun `пустое название отклоняется`() =
        runTest {
            fixture.setUp()

            val result = fixture.runInContext { labels.new(AttendanceLabelId.new(), "  ", AttendanceLabelScope.ANY).save() }

            assertEquals("ATTENDANCE_LABEL_NAME_BLANK", fixture.errorCode(result))
        }

    @Test
    fun `одинаковое название допустимо в разных организациях`() =
        runTest {
            fixture.setUp()
            create("Болеет")
            val other = AttendanceFixture()
            other.setUp()

            other.inContext { labels.new(AttendanceLabelId.new(), "Болеет", AttendanceLabelScope.ABSENT).save() }

            assertEquals(1, other.inContext { labels.list(includeArchived = false) }.size)
        }

    @Test
    fun `архивация метки попадает в аудит`() =
        runTest {
            fixture.setUp()
            val id = create("Болеет")

            fixture.inContext { labels.byId(id).archived().save() }

            val records = audit.events.filter { it.entityType == "attendance_label" && it.entityId == id.value }
            assertEquals(2, records.size)
            assertEquals(AuditActionType.UPDATE, records.last().actionType)
            assertTrue(records.last().data.orEmpty().contains("\"isArchived\":true"), records.last().data)
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
