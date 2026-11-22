package org.athletica.crm.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.athletica.crm.TestPostgres
import org.athletica.crm.configureServer
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.widgets.DbScheduleWidgets
import org.athletica.crm.domain.widgets.WidgetSettings
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.testDi
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Тесты публичного маршрута расписания виджета: без сессии и без различий между недоступными ключами. */
class PublicScheduleRoutesTest {
    private val widgets = DbScheduleWidgets()
    private val fixture = ScheduleFixture(EmployeePermission(emptyList(), setOf(UserPermission.CAN_MANAGE_WIDGETS), emptySet()))

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Создаёт виджет с настройками [settings] и возвращает его публичный ключ. */
    private fun widgetKey(settings: WidgetSettings = WidgetSettings()): String =
        runBlocking {
            val id = ScheduleWidgetId.new()
            fixture.inContext { widgets.new(id, testName("Сайт"), settings).save() }
            fixture.inContext { widgets.byId(id).publicKey }
        }

    @Test
    fun `расписание отдаётся без сессии и кэшируется`() =
        testApplication {
            runBlocking { fixture.setUp() }
            val key = widgetKey()
            application { context(testDi()) { configureServer() } }

            val response = client.get("/api/public/schedule-widget?key=$key")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("public, max-age=60", response.headers[HttpHeaders.CacheControl])
            assertTrue(response.bodyAsText().contains("\"periodStart\""))
        }

    @Test
    fun `недоступные ключи дают одинаковый ответ`() =
        testApplication {
            runBlocking { fixture.setUp() }
            val disabled = widgetKey(WidgetSettings(enabled = false))
            application { context(testDi()) { configureServer() } }

            val responses = listOf("unknown", disabled).map { client.get("/api/public/schedule-widget?key=$it") }

            responses.forEach { assertEquals(HttpStatusCode.BadRequest, it.status) }
            assertEquals(1, responses.map { it.bodyAsText() }.toSet().size)
            assertTrue(responses.first().bodyAsText().contains("WIDGET_UNAVAILABLE"))
        }
}
