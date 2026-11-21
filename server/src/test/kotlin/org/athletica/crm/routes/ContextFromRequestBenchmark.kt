package org.athletica.crm.routes

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.athletica.crm.TestPostgres
import org.athletica.crm.assistant.assistantOwner
import org.athletica.crm.configureServer
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.domain.employees.EmployeeRole
import org.athletica.crm.sessionAccessToken
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.testName
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Замер времени сборки контекста запроса ([contextFromRequest]) на прогретом приложении.
 * Запускается только при `CONTEXT_BENCH=1`; результат печатается строкой `BENCH`.
 */
class ContextFromRequestBenchmark {
    /** Прогревает [WARMUP] запросами и печатает медиану [SAMPLES] последовательных сборок контекста. */
    @Test
    fun contextFromRequestMedian() {
        assumeTrue(System.getenv("CONTEXT_BENCH") == "1")
        TestPostgres.truncate()
        val di = testDi()
        val (owner, employeeId) =
            orThrow {
                runBlocking {
                    val owner = assistantOwner(di)
                    val employeeId = EmployeeId.new()
                    di.database.transaction {
                        val extraBranch = BranchId.new()
                        sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Второй филиал')")
                            .bind("id", extraBranch)
                            .bind("orgId", owner.orgId)
                            .execute()
                        context(owner) {
                            val roleIds =
                                listOf(
                                    setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE, UserPermission.CAN_MANAGE_TASKS),
                                    setOf(UserPermission.CAN_VIEW_ALL_TASKS),
                                    setOf(UserPermission.CAN_USE_ASSISTANT, UserPermission.CAN_MANAGE_ORG_BALANCE),
                                ).mapIndexed { i, permissions ->
                                    EmployeeRole(Uuid.random(), testName("Роль $i"), permissions).also { di.roles.new(it) }.id
                                }
                            di.employees.new(
                                employeeId,
                                testName("Замер"),
                                null,
                                null,
                                null,
                                EmployeePermissionSelection(roleIds, setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE), setOf(UserPermission.CAN_MANAGE_ORG_BALANCE)),
                                EmployeeBranchAccess.Selected(listOf(owner.branchId, extraBranch)),
                            )
                        }
                    }
                    owner to employeeId
                }
            }
        val token =
            testJwtConfig.sessionAccessToken(
                object : AuthenticatedUser {
                    override val id = owner.userId
                    override val orgId = owner.orgId
                    override val branchId = owner.branchId
                    override val employeeId = employeeId
                    override val username = owner.username
                },
            )
        val samples = mutableListOf<Duration>()
        testApplication {
            application {
                context(di) { configureServer() }
                routing {
                    authenticate("auth-jwt") {
                        get("/bench/context") {
                            val started = TimeSource.Monotonic.markNow()
                            call.contextFromRequest(di.database, di.employees, di.organizations, di.employeePresence, di.authSessions)
                            samples += started.elapsedNow()
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }
                }
            }
            repeat(WARMUP + SAMPLES) {
                val response = client.get("/bench/context") { header(HttpHeaders.Authorization, "Bearer $token") }
                assertEquals(HttpStatusCode.NoContent, response.status)
            }
        }
        val measured = samples.drop(WARMUP).sorted()
        println("BENCH contextFromRequest samples=${measured.size} median=${measured[measured.size / 2]} p90=${measured[measured.size * 9 / 10]}")
    }

    private companion object {
        /** Запросы прогрева, не входящие в выборку. */
        const val WARMUP = 200

        /** Размер выборки. */
        const val SAMPLES = 1000
    }
}
