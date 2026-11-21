package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.raise.context.either
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.configureServer
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.employeeContext
import org.athletica.crm.read.clients.ClientListQuery
import org.athletica.crm.read.clients.ClientSortColumn
import org.athletica.crm.read.clients.ClientSortCriterion
import org.athletica.crm.sessionAccessToken
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.testName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Совместимость форматов сортировки и проверки границы API клиентов. */
class ClientListRoutesTest {
    private fun query(body: String): ClientListQuery =
        assertIs<Either.Right<ClientListQuery>>(
            context(employeeContext(orgId = OrgId.new())) {
                either<AppError, ClientListQuery> { Json.decodeFromString<ClientListRequest>(body).toQuery() }
            },
        ).value

    @Test
    fun `новая сортировка имеет приоритет, старые поля и пустой массив совместимы`() {
        assertEquals(listOf(ClientSortCriterion(ClientSortColumn.NAME)), query("{}").sort)
        listOf("", ",\"sort\":null", ",\"sort\":[]").forEach { sort ->
            assertEquals(
                listOf(ClientSortCriterion(ClientSortColumn.BIRTHDAY, ascending = false)),
                query("""{"sortField":"BIRTHDAY","sortDirection":"Desc"$sort}""").sort,
            )
        }
        assertEquals(
            listOf(ClientSortCriterion(ClientSortColumn.BALANCE), ClientSortCriterion(ClientSortColumn.NAME, ascending = false)),
            query("""{"sortField":"BIRTHDAY","sortDirection":"Desc","sort":[{"field":"BALANCE"},{"field":"NAME","direction":"Desc"}]}""").sort,
        )
        assertEquals(
            listOf(ClientSortCriterion(ClientSortColumn.NEXT_BIRTHDAY)),
            query("""{"birthday":{"from":"2026-12-30","to":"2027-01-06"},"sort":[{"field":"NEXT_BIRTHDAY"}]}""").sort,
        )
    }

    @Test
    fun `недопустимая сортировка отклоняется через HTTP`() =
        runTest {
            TestPostgres.truncate()
            val di = testDi()
            val owner =
                assertIs<Either.Right<AuthenticatedUser>>(
                    either {
                        di.database.transaction {
                            di.auth.signup(
                                SignUpRequest(
                                    companyName = testName("Acme"),
                                    userName = testName("John"),
                                    login = "owner@example.com",
                                    password = "password123",
                                    timezone = "UTC",
                                    currency = Currency.RUB,
                                ),
                                lang = Lang.RU,
                                clientIp = null,
                            )
                        }
                    },
                ).value
            val token = testJwtConfig.sessionAccessToken(owner)
            testApplication {
                application { context(di) { configureServer() } }
                listOf(
                    """{"sort":[{"field":"NAME"},{"field":"NAME","direction":"Desc"}]}""",
                    """{"sort":[{"field":"NEXT_BIRTHDAY"}]}""",
                    """{"sortField":"NEXT_BIRTHDAY"}""",
                    """{"sort":[{"field":"c.name; DROP TABLE clients"}]}""",
                    """{"sort":[{"field":"NAME","direction":"invalid"}]}""",
                ).forEach { body ->
                    val response =
                        client.post("/api/clients/list") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }
                    assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                }
            }
        }
}
