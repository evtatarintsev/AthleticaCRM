package org.athletica.crm.read.widgets

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.widgets.DbScheduleWidgets
import org.athletica.crm.domain.widgets.WidgetSettings
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** Тесты проекции виджетов раздела настроек: скоуп по организации и филиалу, право доступа. */
class DbWidgetListViewTest {
    private val view = DbWidgetListView()
    private val widgets = DbScheduleWidgets()
    private val managePermission = EmployeePermission(emptyList(), setOf(UserPermission.CAN_MANAGE_WIDGETS), emptySet())

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private fun codeOf(result: Either<AppError, *>): String = assertIs<CommonDomainError>(assertIs<Either.Left<AppError>>(result).value).code

    /** Вставляет виджет [name] прямо в БД в филиал [branchId] организации [orgId]. */
    private suspend fun insertWidget(orgId: Uuid, branchId: Uuid, name: String): ScheduleWidgetId =
        orThrow {
            val id = ScheduleWidgetId.new()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO schedule_widgets (id, org_id, branch_id, name, public_key, enabled, horizon_days,
                                                  show_coaches, show_hall, show_disciplines)
                    VALUES (:id, :orgId, :branchId, :name, :key, true, 30, false, true, true)
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", orgId)
                .bind("branchId", branchId)
                .bind("name", name)
                .bind("key", Uuid.generateV7().toString())
                .execute()
            return id
        }

    @Test
    fun `виджеты другого филиала и другой организации не видны`() =
        runTest {
            val manager = ScheduleFixture(managePermission)
            manager.setUp()
            val otherOrg = ScheduleFixture()
            otherOrg.setUp("Other Org")
            val otherBranch = Uuid.generateV7()
            orThrow {
                TestPostgres.db
                    .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Север')")
                    .bind("id", otherBranch)
                    .bind("orgId", manager.orgId)
                    .execute()
            }
            val site = insertWidget(manager.orgId, manager.branchId.value, "Сайт")
            val vk = insertWidget(manager.orgId, manager.branchId.value, "ВК")
            val northern = insertWidget(manager.orgId, otherBranch, "Северный")
            val foreign = insertWidget(otherOrg.orgId, otherOrg.branchId.value, "Чужой")

            val list = manager.inContext { view.list() }.widgets

            assertEquals(listOf(vk, site), list.map { it.id })
            assertEquals("WIDGET_NOT_FOUND", codeOf(manager.runInContext { view.byId(northern) }))
            assertEquals("WIDGET_NOT_FOUND", codeOf(manager.runInContext { view.byId(foreign) }))
        }

    @Test
    fun `карточка отдаёт настройки и исключённые группы`() =
        runTest {
            val manager = ScheduleFixture(managePermission)
            manager.setUp()
            val group = manager.insertGroup()
            val id = ScheduleWidgetId.new()
            manager.inContext { widgets.new(id, testName("Сайт"), WidgetSettings(showCoaches = true, excludedGroups = listOf(group))).save() }

            val detail = manager.inContext { view.byId(id) }

            assertEquals("Сайт", detail.name)
            assertEquals(30, detail.horizon.days)
            assertEquals(true, detail.showCoaches)
            assertEquals(listOf(group), detail.excludedGroupIds)
            assertEquals(manager.inContext { widgets.byId(id).publicKey }, detail.publicKey)
        }

    @Test
    fun `без права раздел недоступен`() =
        runTest {
            val fixture = ScheduleFixture()
            fixture.setUp()

            assertEquals("PERMISSION_DENIED", codeOf(fixture.runInContext { view.list() }))
        }
}
