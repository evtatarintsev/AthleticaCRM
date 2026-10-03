package org.athletica.crm.leadsources

import arrow.core.Either
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.leadSources.LeadSourceDetailResponse
import org.athletica.crm.api.schemas.leadSources.LeadSourceListResponse
import org.athletica.crm.configureServer
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.usecases.auth.User
import org.athletica.crm.usecases.auth.signUp
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты маршрутов справочника источников: пояснение при создании и обновлении, предел его длины. */
class LeadSourcesRoutesTest {
    private lateinit var di: Di
    private lateinit var token: String
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию; её владелец — автор запросов. */
    private suspend fun signUpOwner() {
        val result =
            TestPostgres.db.transaction {
                context(this, PasswordHasher(), DbUserDisplaySettings()) {
                    signUp(
                        SignUpRequest(
                            companyName = "Acme",
                            userName = "John",
                            login = "owner@example.com",
                            password = "password123",
                            timezone = "UTC",
                            currency = Currency.RUB,
                        ),
                    )
                }
            }
        token = testJwtConfig.makeAccessToken(assertIs<Either.Right<User>>(result).value)
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String): HttpResponse =
        client.post("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.AcceptLanguage, "ru")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Источник [id] из списка источников организации. */
    private suspend fun ApplicationTestBuilder.listed(id: LeadSourceId): LeadSourceDetailResponse {
        val response =
            client.get("/api/lead-sources/list") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString<LeadSourceListResponse>(response.bodyAsText()).leadSources.single { it.id == id }
    }

    @Test
    fun `пояснение задаётся при создании и меняется при обновлении`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val id = LeadSourceId.new()

                val created = post("lead-sources/create", """{"id":"$id","name":"Партнёры","description":" Фитнес-клубы-партнёры "}""")
                assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())
                assertEquals("Фитнес-клубы-партнёры", listed(id).description)

                val updated = post("lead-sources/update", """{"id":"$id","name":"Партнёры","description":"Магазины-партнёры"}""")
                assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())
                assertEquals("Магазины-партнёры", listed(id).description)
            }
        }

    @Test
    fun `запрос без пояснения создаёт источник с пустым пояснением`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val id = LeadSourceId.new()

                val created = post("lead-sources/create", """{"id":"$id","name":"Партнёры"}""")

                assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())
                assertEquals("", listed(id).description)
            }
        }

    @Test
    fun `пояснение из пробелов сохраняется пустым`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val id = LeadSourceId.new()

                post("lead-sources/create", """{"id":"$id","name":"Партнёры","description":"   "}""")

                assertEquals("", listed(id).description)
            }
        }

    @Test
    fun `пояснение длиннее 500 символов отклоняется и не меняет источник`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val id = LeadSourceId.new()
                post("lead-sources/create", """{"id":"$id","name":"Партнёры","description":"Клубы"}""")
                val tooLong = "а".repeat(501)

                val rejectedCreate = post("lead-sources/create", """{"id":"${LeadSourceId.new()}","name":"Новый","description":"$tooLong"}""")
                val rejectedUpdate = post("lead-sources/update", """{"id":"$id","name":"Партнёры","description":"$tooLong"}""")

                assertEquals("LEAD_SOURCE_DESCRIPTION_TOO_LONG", rejectedCreate.errorCode())
                assertEquals("LEAD_SOURCE_DESCRIPTION_TOO_LONG", rejectedUpdate.errorCode())
                assertEquals("Клубы", listed(id).description)
            }
        }

    private suspend fun HttpResponse.errorCode(): String {
        assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        return json.decodeFromString<ErrorResponse>(bodyAsText()).code
    }
}
