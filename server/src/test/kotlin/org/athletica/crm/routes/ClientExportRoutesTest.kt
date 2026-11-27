package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.raise.context.either
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.configureServer
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.auth.SignUp
import org.athletica.crm.sessionAccessToken
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.testName
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Выгрузка клиентов: CSV экранирует текст, xlsx — настоящая книга с числовым балансом. */
class ClientExportRoutesTest {
    @Test
    fun `клиенты выгружаются в CSV и xlsx`() =
        runTest {
            TestPostgres.truncate()
            val di = testDi()
            val owner =
                assertIs<Either.Right<AuthenticatedUser>>(
                    either {
                        di.database.transaction {
                            di.auth.signup(
                                SignUp(
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
                val created =
                    client.post("/api/clients/create") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"id":"${ClientId.new()}","name":"=Иванов, Пётр","gender":"MALE"}""")
                    }
                assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())

                val body = """{"fields":["gender","balance"]}"""
                val csv =
                    client.post("/api/clients/export") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                assertEquals(HttpStatusCode.OK, csv.status)
                assertTrue(csv.headers[HttpHeaders.ContentDisposition].orEmpty().contains("clients.csv"))
                val lines = csv.bodyAsText().removePrefix("﻿").trim().lines()
                assertEquals(listOf("Имя,Пол,Баланс", "\"'=Иванов, Пётр\",М,0.00"), lines)

                val xlsx =
                    client.post("/api/clients/export?format=xlsx") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                assertEquals(HttpStatusCode.OK, xlsx.status)
                assertTrue(xlsx.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/vnd.openxmlformats"))
                ReadableWorkbook(xlsx.bodyAsBytes().inputStream()).use { workbook ->
                    val rows = workbook.firstSheet.read()
                    assertEquals("=Иванов, Пётр", rows[1].getCell(0).asString())
                    assertEquals(CellType.NUMBER, rows[1].getCell(2).type)
                    assertEquals(0, BigDecimal.ZERO.compareTo(rows[1].getCell(2).asNumber()))
                }
            }
        }
}
