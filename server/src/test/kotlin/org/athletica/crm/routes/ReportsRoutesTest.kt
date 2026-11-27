package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.raise.context.Raise
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.reports.CountReportValue
import org.athletica.crm.api.schemas.reports.ReportBetter
import org.athletica.crm.api.schemas.reports.ReportFilter
import org.athletica.crm.api.schemas.reports.ReportsTableResponse
import org.athletica.crm.api.schemas.reports.ReportsTableRow
import org.athletica.crm.configureServer
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.reports.ReportPeriods
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.employeeContext
import org.athletica.crm.read.reports.ReportTableSource
import org.athletica.crm.sessionAccessToken
import org.athletica.crm.storage.Transaction
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Таблица показателей отчётов: право на раздел, филиал и отбор источников по правам. */
class ReportsRoutesTest {
    private val fixture = MembershipFixture()
    private val today = LocalDate(2026, 11, 27)

    @Before
    fun setUp() =
        runBlocking {
            TestPostgres.truncate()
            fixture.setUp()
        }

    private fun token() =
        testJwtConfig.sessionAccessToken(
            object : AuthenticatedUser {
                override val id = fixture.ctx.userId
                override val orgId = fixture.orgId
                override val branchId = fixture.branchId
                override val employeeId = fixture.employeeId
                override val username = "reports@test"
            },
        )

    /** Источник одной строки [reportId], требующий прав [requires]. */
    private fun source(
        reportId: String,
        requires: Set<UserPermission>,
    ) = object : ReportTableSource {
        override val requires = requires

        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        override suspend fun rows(
            filter: ReportFilter,
            periods: ReportPeriods,
        ) = listOf(ReportsTableRow(reportId, reportId, CountReportValue(1), null, ReportBetter.UP))
    }

    private val sources = listOf(source("groups", emptySet()), source("revenue", setOf(UserPermission.CAN_VIEW_FINANCE)))

    private fun table(
        permissions: Set<UserPermission>,
        filter: ReportFilter = ReportFilter(),
        branches: EmployeeBranchAccess = EmployeeBranchAccess.All,
    ): Either<AppError, ReportsTableResponse> =
        runBlocking {
            val ctx = employeeContext(orgId = fixture.orgId, permissions = EmployeePermission(emptyList(), permissions, emptySet()), availableBranches = branches)
            either { TestPostgres.db.transaction { context(ctx) { reportsTable(filter, today, sources) } } }
        }

    @Test
    fun `без права на отчёты — отказ`() {
        assertEquals("PERMISSION_DENIED", assertIs<Either.Left<AppError>>(table(emptySet())).value.code)
    }

    @Test
    fun `денежный источник без права на финансы не попадает в таблицу`() {
        val reportsOnly = assertIs<Either.Right<ReportsTableResponse>>(table(setOf(UserPermission.CAN_VIEW_REPORTS))).value
        assertEquals(listOf("groups"), reportsOnly.rows.map { it.reportId })
        val withFinance = assertIs<Either.Right<ReportsTableResponse>>(table(setOf(UserPermission.CAN_VIEW_REPORTS, UserPermission.CAN_VIEW_FINANCE))).value
        assertEquals(listOf("groups", "revenue"), withFinance.rows.map { it.reportId })
        assertEquals(LocalDate(2026, 11, 1), withFinance.period.from)
        assertEquals(LocalDate(2026, 10, 27), withFinance.basePeriod.to)
    }

    @Test
    fun `чужой филиал — отказ`() {
        val result = table(setOf(UserPermission.CAN_VIEW_REPORTS), ReportFilter(branchId = BranchId.new()), EmployeeBranchAccess.Selected(listOf(fixture.branchId)))
        assertEquals("PERMISSION_DENIED", assertIs<Either.Left<AppError>>(result).value.code)
    }

    @Test
    fun `маршруты отвечают пустой таблицей владельцу и отказом сотруднику без права`() =
        runTest {
            val di = testDi()
            testApplication {
                application { context(di) { configureServer() } }
                val table =
                    client.post("/api/reports/table") {
                        header(HttpHeaders.Authorization, "Bearer ${token()}")
                        contentType(ContentType.Application.Json)
                        setBody("""{"period":"30d"}""")
                    }
                assertEquals(HttpStatusCode.OK, table.status, table.bodyAsText())
                assertEquals(emptyList(), Json.decodeFromString<ReportsTableResponse>(table.bodyAsText()).rows)
                val file =
                    client.post("/api/reports/table/export") {
                        header(HttpHeaders.Authorization, "Bearer ${token()}")
                        contentType(ContentType.Application.Json)
                        setBody("""{"filter":{"period":"month"},"format":"csv"}""")
                    }
                assertEquals(HttpStatusCode.OK, file.status)
                assertTrue(file.headers[HttpHeaders.ContentDisposition].orEmpty().contains(".csv"))
                assertEquals("Показатель,Период,Прошлый период,Изменение", file.bodyAsText().removePrefix("﻿").trim())

                orThrow { TestPostgres.db.sql("UPDATE employees SET is_owner = false WHERE id = :id").bind("id", fixture.employeeId).execute() }
                listOf("/api/reports/table" to "{}", "/api/reports/table/export" to "{}").forEach { (path, body) ->
                    val denied =
                        client.post(path) {
                            header(HttpHeaders.Authorization, "Bearer ${token()}")
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }
                    assertEquals(HttpStatusCode.BadRequest, denied.status)
                    assertTrue(denied.bodyAsText().contains("PERMISSION_DENIED"))
                }
            }
        }
}
