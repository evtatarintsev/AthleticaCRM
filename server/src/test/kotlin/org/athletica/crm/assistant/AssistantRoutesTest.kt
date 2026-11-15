package org.athletica.crm.assistant

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.configureServer
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clients.AuditClients
import org.athletica.crm.storage.asInt
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Проверяет HTTP-границу, optional AI и CREATE-аудит обычной формы. */
class AssistantRoutesTest {
    private lateinit var di: Di
    private lateinit var owner: EmployeeRequestContext

    @Before
    fun setup(): Unit =
        orThrow {
            runBlocking {
                TestPostgres.truncate()
                val base = testDi()
                di = base.copy(clients = AuditClients(base.clients, base.audit))
                owner = assistantOwner(di)
            }
        }

    /** Все маршруты требуют сессию; status без AI-ключа остаётся рабочим. */
    @Test
    fun sessionAndMissingConfiguration(): Unit =
        testApplication {
            application { context(di) { configureServer() } }
            listOf("status", "settings", "dialogs/current").forEach {
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/assistant/$it").status)
            }
            listOf("settings/update", "dialogs/create", "messages/send", "drafts/confirm").forEach {
                assertEquals(HttpStatusCode.Unauthorized, client.post("/api/assistant/$it").status)
            }
            val status = client.get("/api/assistant/status") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
            assertEquals(HttpStatusCode.OK, status.status)
            assertTrue(status.bodyAsText().contains("\"available\":false"))
            assertFalse(status.bodyAsText().contains("apiKey"))
            val response = post("assistant/dialogs/create", "")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("ASSISTANT_UNAVAILABLE"))
        }

    /** Выключение блокирует диалог, отправку и подтверждение, прежде чем искать переданные ID. */
    @Test
    fun disabledAssistantRejectsAllOperations(): Unit =
        testApplication {
            val fake = FakeAssistant()
            di = di.copy(assistantConnection = fake.connection)
            application { context(di) { configureServer() } }
            val id = ClientId.new()
            listOf("dialogs/create" to "", "messages/send" to """{"dialogId":"$id","text":"test"}""", "drafts/confirm" to """{"clientId":"$id"}""").forEach { (path, body) ->
                val response = post("assistant/$path", body)
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("ASSISTANT_DISABLED"))
            }
            val current = client.get("/api/assistant/dialogs/current") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
            assertEquals(HttpStatusCode.BadRequest, current.status)
            assertEquals(0, fake.prompts.size)
        }

    /** Создание из обычной формы получает тот же единичный CREATE-аудит. */
    @Test
    fun normalClientCreationWritesCreateAudit(): Unit =
        testApplication {
            application { context(di) { configureServer() } }
            val id = ClientId.new()
            val response = post("clients/create", """{"id":"$id","name":"Иван","gender":"MALE"}""")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            orThrow {
                val count =
                    di.database.sql("SELECT count(*)::int AS count FROM audit_logs WHERE entity_type = 'client' AND entity_id = :client AND action_type = 'create' AND user_id = :user")
                        .bind("client", id).bind("user", owner.userId).firstOrNull { it.asInt("count") }
                assertEquals(1, count)
            }
        }

    /** HTTP-подтверждение возвращает квитанцию без модели и дополнительной квоты. */
    @Test
    fun confirmUsesNoModelOrQuota(): Unit =
        testApplication {
            val fake = FakeAssistant()
            di = di.copy(assistantConnection = fake.connection)
            val card =
                orThrow {
                    context(owner) {
                        di.assistant.updateSettings(org.athletica.crm.api.schemas.assistant.AssistantSettingsRequest(true, 100))
                        fake.respond = { prompt ->
                            if (prompt.messages.last().parts.any { it is ai.koog.prompt.message.MessagePart.Tool.Result }) {
                                FakeAssistant.answer("Проверьте")
                            } else {
                                FakeAssistant.call("prepare_client", """{"name":"Иван","gender":"MALE"}""")
                            }
                        }
                        di.assistant.send(di.assistant.current().id, "Иван").messages.last().blocks.filterIsInstance<org.athletica.crm.api.schemas.assistant.AssistantBlock.ClientDraft>().single()
                    }
                }
            val calls = fake.prompts.size
            application { context(di) { configureServer() } }
            val response = post("assistant/drafts/confirm", """{"clientId":"${card.clientId}"}""")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertTrue(response.bodyAsText().contains(card.clientId.toString()))
            assertEquals(calls, fake.prompts.size)
            orThrow { context(owner) { assertEquals(1, di.assistant.settings().acceptedMessages) } }
        }

    /** Токен соответствует настоящему владельцу из фикстуры. */
    private fun token(): String =
        testJwtConfig.makeAccessToken(
            object : AuthenticatedUser {
                override val id = owner.userId
                override val orgId = owner.orgId
                override val branchId = owner.branchId
                override val employeeId = owner.employee.id
                override val username = owner.username
            },
        )

    /** Отправляет JSON с действующей сессией. */
    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}
