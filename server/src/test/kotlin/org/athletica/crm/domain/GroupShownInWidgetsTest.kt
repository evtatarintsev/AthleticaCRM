package org.athletica.crm.domain

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Тесты признака группы «показывать в виджетах». */
class GroupShownInWidgetsTest {
    private val groups = DbGroups()
    private val fixture = ScheduleFixture()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `новая группа без явного признака показывается в виджетах`() =
        runTest {
            fixture.setUp()
            val groupId = GroupId.new()

            fixture.inContext { groups.new(groupId, testName("Йога"), emptyList(), emptyList()) }

            assertTrue(fixture.inContext { groups.byId(groupId).shownInWidgets })
        }

    @Test
    fun `признак задаётся при создании и меняется при сохранении`() =
        runTest {
            fixture.setUp()
            val groupId = GroupId.new()

            fixture.inContext { groups.new(groupId, testName("Тестовая"), emptyList(), emptyList(), shownInWidgets = false) }
            assertFalse(fixture.inContext { groups.byId(groupId).shownInWidgets })

            fixture.inContext { groups.byId(groupId).withShownInWidgets(true).save() }
            assertTrue(fixture.inContext { groups.byId(groupId).shownInWidgets })
        }
}
