package org.athletica.crm.demo

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.configureServer
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.asLong
import org.athletica.crm.testDi
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Регистрация и `/auth/me` в демо-режиме и без него. */
class DemoModeRoutesTest {
    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Регистрирует владельца через `POST /auth/sign-up` клиентом [client]. */
    private suspend fun signUp(client: HttpClient): HttpResponse =
        client.post("/api/auth/sign-up") {
            contentType(ContentType.Application.Json)
            setBody("""{"companyName":"Демо-клуб","userName":"Владелец","login":"owner@example.com","password":"password123","timezone":"Europe/Moscow","currency":"RUB"}""")
        }

    /** Признак `demo` из `/auth/me` для владельца с токеном из ответа регистрации [signup]. */
    private suspend fun demoFlag(client: HttpClient, signup: HttpResponse): Boolean {
        val access = Json.parseToJsonElement(signup.bodyAsText()).jsonObject.getValue("accessToken").jsonPrimitive.content
        val me = client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer $access") }
        assertEquals(HttpStatusCode.OK, me.status)
        return Json.parseToJsonElement(me.bodyAsText()).jsonObject.getValue("demo").jsonPrimitive.boolean
    }

    /** Значение `n` запроса [sql]. */
    private suspend fun num(sql: String): Long = orThrow { TestPostgres.db.sql(sql).firstOrNull { it.asLong("n") } ?: 0 }

    /** Поднимает приложение с зависимостями [di] и выполняет [block] его клиентом. */
    private fun withApp(di: Di, block: suspend (HttpClient) -> Unit) =
        testApplication {
            application { context(di) { configureServer() } }
            block(client)
        }

    @Test
    fun `в демо-режиме регистрация наполняет организацию и me сообщает признак демо`() =
        withApp(testDi().copy(demoData = DemoData())) { client ->
            val signup = signUp(client)
            assertEquals(HttpStatusCode.OK, signup.status, signup.bodyAsText())
            assertTrue(demoFlag(client, signup))
            assertTrue(num("SELECT count(*) AS n FROM clients") > 0)
            assertTrue(num("SELECT count(*) AS n FROM sessions WHERE status = 'completed'") > 0)
            assertTrue(num("SELECT count(*) AS n FROM sales") > 0)
            assertEquals("Демо-клуб", orThrow { TestPostgres.db.sql("SELECT name AS n FROM organizations").firstOrNull { it.get("n", String::class.java) } })
        }

    @Test
    fun `без демо-режима регистрация создаёт только филиал и зал, признак демо выключен`() =
        withApp(testDi()) { client ->
            val signup = signUp(client)
            assertEquals(HttpStatusCode.OK, signup.status)
            assertEquals(false, demoFlag(client, signup))
            assertEquals(0, num("SELECT count(*) AS n FROM clients"))
            assertEquals(0, num("SELECT count(*) AS n FROM groups"))
            assertEquals(1, num("SELECT count(*) AS n FROM branches"))
            assertEquals(1, num("SELECT count(*) AS n FROM halls"))
        }

    @Test
    fun `ошибка наполнения отклоняет регистрацию целиком`() =
        withApp(testDi().copy(demoData = DemoData(tasks = DemoFixture.failingTasks))) { client ->
            val signup = signUp(client)
            assertNotEquals(HttpStatusCode.OK, signup.status)
            assertEquals(0, num("SELECT count(*) AS n FROM organizations"))
            assertEquals(0, num("SELECT count(*) AS n FROM users"))
            assertEquals(0, num("SELECT count(*) AS n FROM org_emails"))
        }
}
