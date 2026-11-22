package org.athletica.crm.domain.widgets

import arrow.core.Either
import arrow.core.raise.Raise
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.widgets.CreateScheduleWidgetRequest
import org.athletica.crm.api.schemas.widgets.EditScheduleWidgetRequest
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.widgets.WidgetHorizon
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/** Тесты агрегата виджета расписания: настройки, ключ, удаление, права и аудит. */
class DbScheduleWidgetsTest {
    private val widgets = DbScheduleWidgets()
    private val manager = ScheduleFixture(EmployeePermission(emptyList(), setOf(UserPermission.CAN_MANAGE_WIDGETS), emptySet()))

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private fun codeOf(result: Either<AppError, *>): String = assertIs<CommonDomainError>(assertIs<Either.Left<AppError>>(result).value).code

    private suspend fun widgetCount(): Long =
        orThrow {
            TestPostgres.db.sql("SELECT COUNT(*) AS cnt FROM schedule_widgets").firstOrNull { it.asLong("cnt") } ?: 0L
        }

    /** Создаёт группу [name] в новом филиале той же организации. */
    private suspend fun insertGroupInOtherBranch(name: String): GroupId =
        orThrow {
            val branchId = Uuid.generateV7()
            TestPostgres.db
                .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Север')")
                .bind("id", branchId)
                .bind("orgId", manager.orgId)
                .execute()
            val id = GroupId.new()
            TestPostgres.db
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", id)
                .bind("orgId", manager.orgId)
                .bind("branchId", branchId)
                .bind("name", name)
                .execute()
            return id
        }

    @Test
    fun `виджет с одним названием получает настройки по умолчанию и случайный ключ`() =
        runTest {
            manager.setUp()
            val id = ScheduleWidgetId.new()

            manager.inContext { widgets.new(id, testName("Сайт")).save() }
            val widget = manager.inContext { widgets.byId(id) }

            assertEquals("Сайт", widget.name)
            assertEquals(manager.branchId, widget.branchId)
            assertEquals(WidgetSettings(true, WidgetHorizon.DEFAULT, false, true, true, emptyList()), widget.settings)
            assertEquals(43, widget.publicKey.length)
            assertNotEquals(widget.publicKey, manager.inContext { widgets.new(ScheduleWidgetId.new(), testName("ВК")).publicKey })
        }

    @Test
    fun `пустое название отклоняется при разборе запроса`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<CreateScheduleWidgetRequest>("""{"id":"${Uuid.generateV7()}","name":"   "}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<EditScheduleWidgetRequest>(
                """
                {"id":"${Uuid.generateV7()}","name":"","enabled":true,"horizon":30,"showCoaches":false,
                 "showHall":true,"showDisciplines":true,"excludedGroupIds":[]}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `настройки и исключённые группы филиала сохраняются`() =
        runTest {
            manager.setUp()
            val group = manager.insertGroup("Взрослые")
            val id = ScheduleWidgetId.new()
            manager.inContext { widgets.new(id, testName("Сайт")).save() }

            val settings = WidgetSettings(false, manager.inContext { WidgetHorizon.from(14) }, true, false, false, listOf(group))
            manager.inContext { widgets.byId(id).withNew(testName("ВК — дети"), settings).save() }
            val widget = manager.inContext { widgets.byId(id) }

            assertEquals("ВК — дети", widget.name)
            assertEquals(settings, widget.settings)
        }

    @Test
    fun `группа другого филиала не исключается и виджет не меняется`() =
        runTest {
            manager.setUp()
            val foreign = insertGroupInOtherBranch("Северные")
            val id = ScheduleWidgetId.new()

            assertEquals("GROUP_NOT_FOUND", codeOf(manager.runInContext { widgets.new(id, testName("Сайт"), WidgetSettings(excludedGroups = listOf(foreign))).save() }))
            assertEquals(0L, widgetCount())

            manager.inContext { widgets.new(id, testName("Сайт")).save() }
            assertEquals(
                "GROUP_NOT_FOUND",
                codeOf(manager.runInContext { widgets.byId(id).withNew(testName("Другое"), WidgetSettings(excludedGroups = listOf(foreign))).save() }),
            )
            val widget = manager.inContext { widgets.byId(id) }
            assertEquals("Сайт", widget.name)
            assertEquals(emptyList(), widget.settings.excludedGroups)
        }

    @Test
    fun `перевыпуск ключа заменяет ключ`() =
        runTest {
            manager.setUp()
            val id = ScheduleWidgetId.new()
            manager.inContext { widgets.new(id, testName("Сайт")).save() }
            val before = manager.inContext { widgets.byId(id).publicKey }

            manager.inContext { widgets.byId(id).regenerateKey() }

            val after = manager.inContext { widgets.byId(id).publicKey }
            assertNotEquals(before, after)
            assertEquals(43, after.length)
        }

    @Test
    fun `удалённый виджет не находится`() =
        runTest {
            manager.setUp()
            val group = manager.insertGroup()
            val id = ScheduleWidgetId.new()
            manager.inContext { widgets.new(id, testName("Сайт"), WidgetSettings(excludedGroups = listOf(group))).save() }

            manager.inContext { widgets.byId(id).delete() }

            assertEquals("WIDGET_NOT_FOUND", codeOf(manager.runInContext { widgets.byId(id) }))
            assertEquals(0L, widgetCount())
        }

    @Test
    fun `без права CAN_MANAGE_WIDGETS команды отклоняются`() =
        runTest {
            manager.setUp()
            val id = ScheduleWidgetId.new()
            manager.inContext { widgets.new(id, testName("Сайт")).save() }
            val noRights = manager.withPermissions(EmployeePermission())
            assertEquals("PERMISSION_DENIED", codeOf(noRights.runInContext { widgets.new(ScheduleWidgetId.new(), testName("ВК")).save() }))
            assertEquals("PERMISSION_DENIED", codeOf(noRights.runInContext { widgets.byId(id).regenerateKey() }))
            assertEquals("PERMISSION_DENIED", codeOf(noRights.runInContext { widgets.byId(id).delete() }))
            assertEquals(1L, widgetCount())
        }

    @Test
    fun `создание и удаление пишут записи аудита`() =
        runTest {
            manager.setUp()
            val audit = AuditLogStub()
            val audited = AuditScheduleWidgets(widgets, audit)
            val id = ScheduleWidgetId.new()

            manager.inContext { audited.new(id, testName("Сайт")).save() }
            manager.inContext { audited.byId(id).delete() }

            assertEquals(listOf(AuditActionType.CREATE, AuditActionType.DELETE), audit.events.map { it.actionType })
            assertEquals(listOf("schedule_widget", "schedule_widget"), audit.events.map { it.entityType })
        }
}

/** Журнал аудита в памяти: запоминает события вместо записи в БД. */
private class AuditLogStub : AuditLog {
    val events: MutableList<AuditEvent> = mutableListOf()

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun log(event: AuditEvent) {
        events.add(event)
    }
}
