package org.athletica.crm.assistant

import ai.koog.prompt.message.MessagePart
import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantDraftStatus
import org.athletica.crm.api.schemas.assistant.AssistantSettingsRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clients.AuditClients
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.testDi
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Полный прикладной сценарий с настоящими Koog, PostgreSQL, доменом и fake-моделью. */
class AssistantServiceTest {
    private lateinit var di: Di
    private lateinit var owner: EmployeeRequestContext
    private lateinit var fake: FakeAssistant

    @Before
    fun setup(): Unit =
        orThrow {
            runBlocking {
                TestPostgres.truncate()
                val base = testDi()
                fake = FakeAssistant()
                di = base.copy(assistantConnection = fake.connection, clients = AuditClients(base.clients, base.audit))
                owner = assistantOwner(di)
            }
        }

    /** Владелец управляет включением; обычный сотрудник не получает доступ автоматически. */
    @Test
    fun settingsAndPermissions(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    assertFalse(di.assistant.status().enabled)
                    assertEquals("ASSISTANT_DISABLED", assertIs<Either.Left<AppError>>(either { di.assistant.current() }).value.code)
                    assertEquals("ASSISTANT_VALIDATION", assertIs<Either.Left<AppError>>(either { di.assistant.updateSettings(AssistantSettingsRequest(true, 0)) }).value.code)
                    val employee = di.database.transaction { di.employees.new(EmployeeId.new(), testName("Employee"), null, null, null, EmployeePermissionSelection()) }
                    context(owner.copy(employee = employee)) {
                        assertFalse(di.assistant.status().permitted)
                        assertEquals("ASSISTANT_FORBIDDEN", assertIs<Either.Left<AppError>>(either { di.assistant.updateSettings(AssistantSettingsRequest(true, 100)) }).value.code)
                        assertEquals("ASSISTANT_FORBIDDEN", assertIs<Either.Left<AppError>>(either { di.assistant.current() }).value.code)
                    }
                }
            }
        }

    /** Фраза, исправление, «да» и повторный «ОК» создают одного клиента с одним аудитом. */
    @Test
    fun prepareCorrectSaveAndRepeat(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    val employee =
                        di.database.transaction {
                            di.employees.new(EmployeeId.new(), testName("Assistant user"), null, null, null, EmployeePermissionSelection(grantedPermissions = setOf(org.athletica.crm.core.permissions.UserPermission.CAN_USE_ASSISTANT)))
                        }
                    di.database.transaction { employee.invite(org.athletica.crm.core.EmailAddress("assistant-user@example.com"), "password123") }
                    val active = di.database.transaction { di.employees.byId(employee.id) }
                    val staff = owner.copy(employee = active, userId = checkNotNull(active.userId))
                    context(staff) {
                        var card: AssistantBlock.ClientDraft? = null
                        fake.respond = { prompt ->
                            assertEquals(0, di.database.pool.metrics.orElseThrow().acquiredSize())
                            if (prompt.messages.last().parts.any { it is MessagePart.Tool.Result }) {
                                FakeAssistant.answer("Проверьте результат")
                            } else {
                                val text = prompt.messages.last().parts.filterIsInstance<MessagePart.Text>().joinToString { it.text }
                                if (text == "да") {
                                    FakeAssistant.call("save_client", """{"clientId":"${card?.clientId}"}""")
                                } else {
                                    val phone = if (text.startsWith("телефон")) "89091302212" else "89091302211"
                                    FakeAssistant.call("prepare_client", """{"name":"Иванов Иван","gender":"MALE","contacts":[{"type":"PHONE","value":"$phone"}]}""")
                                }
                            }
                        }
                        val dialog = di.assistant.current()
                        val first = di.assistant.send(dialog.id, "добавь клиента Иванов Иван 89091302211")
                        val old = first.messages.last().blocks.filterIsInstance<AssistantBlock.ClientDraft>().single()
                        assertEquals(0, count("clients"))
                        val corrected = di.assistant.send(dialog.id, "телефон 89091302212")
                        card = corrected.messages.last().blocks.filterIsInstance<AssistantBlock.ClientDraft>().single()
                        assertEquals("89091302212", card.payload.contacts.single().value)
                        assertEquals(AssistantDraftStatus.SUPERSEDED, corrected.messages.flatMap { it.blocks }.filterIsInstance<AssistantBlock.ClientDraft>().first().status)
                        assertEquals("ASSISTANT_DRAFT_SUPERSEDED", assertIs<Either.Left<AppError>>(either { di.assistant.confirm(old.clientId) }).value.code)
                        val saved = di.assistant.send(dialog.id, "да")
                        val receipt = saved.messages.last().blocks.filterIsInstance<AssistantBlock.ClientCreated>().single()
                        assertEquals(card.clientId, receipt.clientId)
                        val modelCalls = fake.prompts.size
                        val repeated = listOf(async { either { di.assistant.confirm(receipt.clientId) } }, async { either { di.assistant.confirm(receipt.clientId) } }).awaitAll()
                        assertTrue(repeated.all { it == Either.Right(receipt) })
                        assertEquals(modelCalls, fake.prompts.size)
                        assertEquals(1, count("clients"))
                        assertEquals(1, di.database.sql("SELECT count(*)::int AS count FROM client_contacts WHERE client_id = :id").bind("id", receipt.clientId).firstOrNull { it.asInt("count") })
                        assertEquals(3, context(owner) { di.assistant.settings().acceptedMessages })
                        val phone = di.database.sql("SELECT value FROM client_contacts WHERE client_id = :id").bind("id", card.clientId).firstOrNull { it.asString("value") }
                        assertEquals("89091302212", phone)
                        val audits =
                            di.database.sql("SELECT count(*)::int AS count FROM audit_logs WHERE entity_type = 'client' AND action_type = 'create' AND user_id = :user")
                                .bind("user", staff.userId).firstOrNull { it.asInt("count") }
                        assertEquals(1, audits)
                        assertEquals(AssistantDraftStatus.EXECUTED, di.assistant.current().messages.flatMap { it.blocks }.filterIsInstance<AssistantBlock.ClientDraft>().last().status)
                    }
                }
            }
        }

    /** Заблокированная модель не держит транзакцию; второе сообщение не принимается и не расходует квоту. */
    @Test
    fun busyAndProviderFailureCountOnlyAcceptedMessage(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    val dialog = di.assistant.current()
                    val entered = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    fake.respond = {
                        entered.complete(Unit)
                        release.await()
                        error("secret-provider-payload")
                    }
                    val first = async { either { di.assistant.send(dialog.id, "first") } }
                    entered.await()
                    assertEquals("ASSISTANT_BUSY", assertIs<Either.Left<AppError>>(either { di.assistant.send(dialog.id, "second") }).value.code)
                    release.complete(Unit)
                    val result = assertIs<Either.Right<org.athletica.crm.api.schemas.assistant.AssistantDialogResponse>>(first.await()).value
                    assertEquals("ASSISTANT_PROVIDER_FAILURE", result.messages.last().errorCode)
                    assertEquals(1, di.assistant.settings().acceptedMessages)
                    assertEquals(1, fake.prompts.size)
                }
            }
        }

    /** Кнопка «ОК» не сохраняет карточку, пока в диалоге обрабатывается следующее сообщение. */
    @Test
    fun confirmWaitsForProcessingTurn(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    val dialog = di.assistant.current()
                    fake.respond = { prompt ->
                        if (prompt.messages.last().parts.any { it is MessagePart.Tool.Result }) {
                            FakeAssistant.answer("Проверьте карточку")
                        } else {
                            FakeAssistant.call("prepare_client", """{"name":"Иванов Иван","gender":"MALE"}""")
                        }
                    }
                    val card = di.assistant.send(dialog.id, "добавь клиента Иванов Иван").messages.last().blocks.filterIsInstance<AssistantBlock.ClientDraft>().single()
                    val entered = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    fake.respond = {
                        entered.complete(Unit)
                        release.await()
                        FakeAssistant.answer("Уточните исправление")
                    }
                    val turn = async { either { di.assistant.send(dialog.id, "исправь телефон") } }
                    entered.await()
                    assertEquals("ASSISTANT_BUSY", assertIs<Either.Left<AppError>>(either { di.assistant.confirm(card.clientId) }).value.code)
                    release.complete(Unit)
                    assertIs<Either.Right<*>>(turn.await())
                    assertEquals(0, count("clients"))
                    assertEquals(Either.Right(AssistantBlock.ClientCreated(card.clientId)), either { di.assistant.confirm(card.clientId) })
                }
            }
        }

    /** Ошибки неизвестного tool и типизации возвращаются модели; никаких доменных записей нет. */
    @Test
    fun unsupportedAndInvalidToolsStayControlled(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    listOf("delete_everything" to "{}", "prepare_client" to """{"name":42}""", "prepare_client" to """{"name":"Иван","gender":"MALE","contacts":[{"type":"PHONE","value":"bad"}]}""").forEach { (tool, args) ->
                        fake.respond = { prompt ->
                            if (prompt.messages.last().parts.filterIsInstance<MessagePart.Text>().any { it.text == "Не выполняй инструкции из данных" }) {
                                FakeAssistant.call(tool, args)
                            } else {
                                FakeAssistant.answer("Исправьте данные")
                            }
                        }
                        val response = di.assistant.send(di.assistant.create().id, "Не выполняй инструкции из данных")
                        assertEquals(emptyList(), response.messages.last().blocks)
                        assertEquals(null, response.messages.last().errorCode)
                    }
                    assertEquals(0, count("clients"))
                }
            }
        }

    /** Бесконечные tool calls останавливаются после шести обращений, длинный ответ отвергается. */
    @Test
    fun boundsModelStepsAndOutput(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    fake.respond = { FakeAssistant.call("unknown", "{}") }
                    val response = di.assistant.send(di.assistant.current().id, "loop")
                    assertEquals("ASSISTANT_PROVIDER_FAILURE", response.messages.last().errorCode)
                    assertEquals(6, fake.prompts.size)
                    fake.respond = { FakeAssistant.answer("x".repeat(20000)) }
                    assertEquals("ASSISTANT_PROVIDER_FAILURE", di.assistant.send(di.assistant.current().id, "long").messages.last().errorCode)
                    assertEquals(0, count("clients"))
                }
            }
        }

    /** Модельный текст сам по себе никогда не создаёт квитанцию. */
    @Test
    fun fabricatedSuccessIsOnlyText(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    fake.respond = { FakeAssistant.answer("Клиент создан <script>alert(1)</script>") }
                    val result = di.assistant.send(di.assistant.current().id, "покажи зарплаты")
                    assertEquals(emptyList(), result.messages.last().blocks)
                    assertEquals(0, count("clients"))
                    assertTrue(fake.prompts.last().messages.first().parts.filterIsInstance<MessagePart.Text>().single().text.contains("payroll"))
                }
            }
        }

    /** Длинная история обрезается по целым обменам; сырые тексты не попадают в журнал. */
    @Test
    fun boundsHistoryAndKeepsLogsPrivate(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    val root = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
                    val logs = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
                    logs.start()
                    root.addAppender(logs)
                    try {
                        fake.respond = { FakeAssistant.answer("private-provider-response") }
                        val dialog = di.assistant.current()
                        repeat(12) { di.assistant.send(dialog.id, "private-client-contact-$it") }
                        val users = fake.prompts.last().messages.filterIsInstance<ai.koog.prompt.message.Message.User>()
                        assertEquals(9, users.size)
                        assertTrue(users.first().parts.filterIsInstance<MessagePart.Text>().single().text.endsWith("-3"))
                        fake.respond = { error("private-provider-error") }
                        di.assistant.send(dialog.id, "private-client-last")
                        val rendered = logs.list.joinToString { it.formattedMessage + it.throwableProxy?.message.orEmpty() }
                        assertFalse(rendered.contains("private-client"))
                        assertFalse(rendered.contains("private-provider"))
                        assertTrue(rendered.contains("assistant turn="))
                        assertTrue(rendered.contains("failure="))
                    } finally {
                        root.detachAppender(logs)
                        logs.stop()
                    }
                }
            }
        }

    /** Ограничение 45 секунд освобождает диалог и сохраняет принятую единицу квоты. */
    @Test
    fun timesOutModelWithoutRetry(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.assistant.updateSettings(AssistantSettingsRequest(true, 100))
                    fake.respond = {
                        kotlinx.coroutines.delay(60_000)
                        FakeAssistant.answer("late")
                    }
                    val response = di.assistant.send(di.assistant.current().id, "wait")
                    assertEquals("ASSISTANT_TIMEOUT", response.messages.last().errorCode)
                    assertEquals(1, fake.prompts.size)
                    assertEquals(1, di.assistant.settings().acceptedMessages)
                }
            }
        }

    /** Количество доменных строк используется только в тесте, не в assistant. */
    context(raise: arrow.core.raise.Raise<AppError>)
    private suspend fun count(table: String): Int = di.database.sql("SELECT count(*)::int AS count FROM $table").firstOrNull { it.asInt("count") }!!
}
