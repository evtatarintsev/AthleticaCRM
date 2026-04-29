package org.athletica.crm

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.clientbalance.AuditClientBalances
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.employees.EmployeePermissions
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.mail.EmailDispatcher
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FakeEmailDispatcher : EmailDispatcher {
    override suspend fun dispatchPending() {}
}

/** Интеграционные тесты сервера с изолированной БД в Docker-контейнере. */
class ApplicationTest {
    private lateinit var testDi: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        testApplication {
            application {
                val audit = PostgresAuditLog()
                testDi =
                    Di(
                        databaseConfig = TestPostgres.dbConfig,
                        database = TestPostgres.db,
                        mailbox = TestMailbox(),
                        jwtConfig = testJwtConfig,
                        minio = TestMinio.minioService,
                        passwordHasher = PasswordHasher(),
                        audit = audit,
                        orgEmails = DbOrgEmails(),
                        emailDispatcher = FakeEmailDispatcher(),
                        orgBalances = DbOrgBalances(),
                        organizations = DbOrganizations(),
                        employeePermissions = EmployeePermissions(),
                        clientBalances = AuditClientBalances(DbClientBalances(), audit),
                        clients = DbClients(),
                        branches = DbBranches(),
                    )
            }
        }
    }

    private val testJwtConfig =
        JwtConfig(
            secret = "test-secret-key-for-unit-tests",
            accessTokenTtlMinutes = 15L,
            refreshTokenTtlDays = 30L,
        )

    @Test
    fun testLoginWithInvalidCredentials() =
        testApplication {
            application {
                context(testDi) {
                    configureServer()
                }
            }
            val response =
                client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"wrong","password":"wrong","branchId":"00000000-0000-0000-0000-000000000000"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun testMeWithoutToken() =
        testApplication {
            application {
                context(testDi) {
                    configureServer()
                }
            }
            val response = client.get("/api/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Token expired or invalid")
        }
}
