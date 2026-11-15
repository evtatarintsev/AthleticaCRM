package org.athletica.crm.assistant

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.assistant.storage.AssistantStorage
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.testDi
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** DB-проверки изоляции, конкурентной квоты и восстановления после прерванного хода. */
class AssistantStorageTest {
    private lateinit var di: Di
    private lateinit var owner: EmployeeRequestContext
    private lateinit var other: EmployeeRequestContext
    private lateinit var storage: AssistantStorage
    private var instant = Instant.parse("2026-11-14T20:59:59Z")
    private val clock =
        object : Clock {
            override fun now() = instant
        }

    @Before
    fun setup(): Unit =
        orThrow {
            runBlocking {
                TestPostgres.truncate()
                di = testDi()
                owner = assistantOwner(di, "Europe/Moscow")
                other = assistantOwner(di)
                storage = AssistantStorage(DbOrgCalendar(clock), clock)
            }
        }

    /** Новый экземпляр хранилища восстанавливает диалог и сообщения, чужой контекст получает отказ. */
    @Test
    fun persistsAndIsolatesDialogs(): Unit =
        orThrow {
            runBlocking {
                val dialog = context(owner) { di.database.transaction { storage.current() } }
                context(owner) {
                    di.database.transaction {
                        storage.updateSettings(true, 100)
                        val message = storage.accept(dialog.id, "Иван", true)
                        storage.finish(dialog.id, message, "Уточните данные", emptyList(), "[]")
                    }
                }
                val restored = context(owner) { di.database.transaction { AssistantStorage(DbOrgCalendar()).current() } }
                assertEquals(dialog.id, restored.id)
                assertEquals(listOf("Иван", "Уточните данные"), restored.messages.map { it.text })
                assertIs<Either.Left<*>>(either { context(other) { di.database.transaction { storage.dialog(dialog.id) } } })
                val colleague =
                    context(owner) {
                        di.database.transaction { di.employees.new(EmployeeId.new(), testName("Colleague"), null, null, null, EmployeePermissionSelection()) }
                    }
                assertIs<Either.Left<*>>(either { context(owner.copy(employee = colleague)) { di.database.transaction { storage.dialog(dialog.id) } } })
                context(owner) {
                    val newer = di.database.transaction { storage.create() }
                    assertNotEquals(dialog.id, newer.id)
                    assertEquals(newer.id, di.database.transaction { storage.current() }.id)
                }
            }
        }

    /** Последнюю единицу занимают атомарно, а busy и новый локальный день расходуют квоту по правилам. */
    @Test
    fun reservesQuotaAndResetsAtOrganizationMidnight(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    assertFalse(di.database.transaction { storage.settings(true) }.enabled)
                    val dialogs =
                        di.database.transaction {
                            storage.updateSettings(true, 1)
                            listOf(storage.create(), storage.create())
                        }
                    val outcomes =
                        dialogs.map { dialog ->
                            async {
                                either { di.database.transaction { storage.accept(dialog.id, "message", true) } }
                            }
                        }.awaitAll()
                    assertEquals(1, outcomes.count { it is Either.Right })
                    assertEquals("ASSISTANT_LIMIT", assertIs<Either.Left<org.athletica.crm.core.errors.AppError>>(outcomes.first { it is Either.Left }).value.code)
                    val active = dialogs[outcomes.indexOfFirst { it is Either.Right }]
                    val busy = either { di.database.transaction { storage.accept(active.id, "again", true) } }
                    assertEquals("ASSISTANT_BUSY", assertIs<Either.Left<org.athletica.crm.core.errors.AppError>>(busy).value.code)
                    assertEquals(1, di.database.transaction { storage.settings(true) }.acceptedMessages)
                    instant += 2.seconds
                    assertEquals(0, di.database.transaction { storage.settings(true) }.acceptedMessages)
                    di.database.transaction { storage.accept(dialogs.first { it != active }.id, "new day", true) }
                    assertEquals(1, di.database.transaction { storage.settings(true) }.acceptedMessages)
                }
            }
        }

    /** Чтение освобождает просроченный ход; позднее завершение его не восстанавливает. */
    @Test
    fun expiresProcessingWithoutRevivingLateResponse(): Unit =
        orThrow {
            runBlocking {
                instant = Instant.parse("2026-11-14T12:00:00Z")
                context(owner) {
                    val dialog =
                        di.database.transaction {
                            storage.updateSettings(true, 100)
                            storage.create()
                        }
                    val message = di.database.transaction { storage.accept(dialog.id, "first", true) }
                    instant += 46.seconds
                    val expired = di.database.transaction { storage.dialog(dialog.id) }
                    assertEquals("ASSISTANT_TIMEOUT", expired.messages.last().errorCode)
                    di.database.transaction { storage.finish(dialog.id, message, "late", emptyList(), "[]") }
                    assertEquals("", di.database.transaction { storage.dialog(dialog.id) }.messages.last().text)
                    di.database.transaction { storage.accept(dialog.id, "next", true) }
                    assertEquals(2, di.database.transaction { storage.settings(true) }.acceptedMessages)
                }
            }
        }
}
