package org.athletica.crm.sportcatalog

import arrow.core.Either
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.sportcatalog.SportCatalogResponse
import org.athletica.crm.configureServer
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

/** Тесты маршрута каталога видов спорта: одинаковый ответ для всех, синонимы, аутентификация. */
class SportCatalogRoutesTest {
    private lateinit var di: Di
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию [company] с владельцем [login] и возвращает его токен. */
    private suspend fun ownerToken(company: String, login: String): String {
        val result =
            TestPostgres.db.transaction {
                context(this, PasswordHasher(), DbUserDisplaySettings()) {
                    signUp(
                        SignUpRequest(
                            companyName = company,
                            userName = "Owner",
                            login = login,
                            password = "password123",
                            timezone = "UTC",
                            currency = Currency.RUB,
                        ),
                    )
                }
            }
        return testJwtConfig.makeAccessToken(assertIs<Either.Right<User>>(result).value)
    }

    /** Каталог, запрошенный с токеном [token] и языком [lang]. */
    private suspend fun ApplicationTestBuilder.catalog(token: String, lang: String): SportCatalogResponse {
        val response =
            client.get("/api/sport-catalog/list") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.AcceptLanguage, lang)
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun `каталог одинаков для разных организаций и языков`() =
        runTest {
            val first = ownerToken("Acme", "acme@example.com")
            val second = ownerToken("Globex", "globex@example.com")
            testApplication {
                application { context(di) { configureServer() } }

                val ru = catalog(first, "ru")
                val en = catalog(second, "en")

                assertEquals(ru, en)
                val swimming = ru.sports.first()
                assertEquals("Плавание", swimming.names.ru)
                assertEquals("Swimming", swimming.names.en)
            }
        }

    @Test
    fun `дисциплина содержит синонимы`() =
        runTest {
            val token = ownerToken("Acme", "acme@example.com")
            testApplication {
                application { context(di) { configureServer() } }

                val breaking =
                    catalog(token, "ru").sports
                        .flatMap { it.disciplines }
                        .single { it.names.ru == "Брейкинг" }

                assertEquals("Breaking", breaking.names.en)
                assertEquals(listOf("брейк-данс", "break dance", "b-boying"), breaking.aliases)
            }
        }

    @Test
    fun `запрос без токена отклоняется`() =
        testApplication {
            application { context(di) { configureServer() } }

            val response = client.get("/api/sport-catalog/list")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
