package org.athletica.crm.assistant

import ai.koog.prompt.message.MessagePart
import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import kotlinx.coroutines.runBlocking
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantSettingsRequest
import org.athletica.crm.assistant.tools.ClientDrafts
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldKey
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.AuditClients
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.testDi
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Проверки границы подтверждения и отката доменной записи. */
class AssistantGuardTest {
    private lateinit var di: Di
    private lateinit var owner: EmployeeRequestContext
    private lateinit var fake: FakeAssistant
    private val arguments = """{"name":"Иванов Иван","gender":"MALE","contacts":[{"type":"PHONE","value":"89091302211"}]}"""

    @Before
    fun setup(): Unit =
        orThrow {
            runBlocking {
                TestPostgres.truncate()
                val base = testDi()
                fake = FakeAssistant()
                di = base.copy(assistantConnection = fake.connection, clients = AuditClients(base.clients, base.audit))
                owner = assistantOwner(di)
                context(owner) { di.assistant.updateSettings(AssistantSettingsRequest(true, 100)) }
            }
        }

    /** Даже просьба без подтверждения не разрешает подготовку и сохранение за один ход. */
    @Test
    fun cannotPrepareAndSaveInSameTurn(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    var calls = 0
                    fake.respond = {
                        when (++calls) {
                            1 -> FakeAssistant.call("prepare_client", arguments)
                            2 -> {
                                val id = di.database.sql("SELECT client_id::text FROM assistant_client_drafts").firstOrNull { it.asString("client_id") }
                                FakeAssistant.call("save_client", """{"clientId":"$id"}""")
                            }
                            else -> FakeAssistant.answer("Подтвердите карточку")
                        }
                    }
                    val result = di.assistant.send(di.assistant.current().id, "создай без подтверждения")
                    assertTrue(result.messages.last().blocks.none { it is AssistantBlock.ClientCreated })
                    assertEquals(0, clientCount())
                    assertTrue(fake.prompts.last().messages.last().parts.filterIsInstance<MessagePart.Tool.Result>().single().output.contains("CONFIRMATION_REQUIRED"))
                }
            }
        }

    /** Недостающее обязательное поле возвращается модели с подписью; карточки и клиента ещё нет. */
    @Test
    fun requiresOrganizationCustomFields(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    di.database.transaction { di.customFieldDefinitions.saveAll("CLIENT", listOf(CustomFieldDefinition.Text(CustomFieldKey.from("guardian"), "Родитель", isRequired = true))) }
                    fake.respond = { prompt ->
                        if (prompt.messages.last().parts.any { it is MessagePart.Tool.Result }) {
                            FakeAssistant.answer("Укажите родителя")
                        } else {
                            FakeAssistant.call("prepare_client", arguments)
                        }
                    }
                    val result = di.assistant.send(di.assistant.current().id, "добавь клиента Иванов Иван")
                    assertEquals(emptyList(), result.messages.last().blocks)
                    assertEquals(0, clientCount())
                    assertTrue(fake.prompts.last().messages.last().parts.filterIsInstance<MessagePart.Tool.Result>().single().output.contains("Родитель"))
                }
            }
        }

    /** Ошибка контакта откатывает клиента, CREATE-аудит и executed; после исправления можно повторить. */
    @Test
    fun rollsBackAllWritesIfContactFails(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    val card = prepareCard()
                    val failingContacts =
                        object : ClientContacts by di.clientContacts {
                            context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
                            override suspend fun replace(client: ActiveClient, contacts: List<ClientContact>) {
                                raise.raise(CommonDomainError("CONTACT_INVALID", "Invalid contact"))
                            }
                        }
                    val failing = AssistantService(di.database, fake.connection, di.assistantStorage, ClientDrafts(di.assistantStorage, di.clients, failingContacts, di.customFieldDefinitions), di.employees, di.customFieldDefinitions)
                    assertEquals("CONTACT_INVALID", assertIs<Either.Left<AppError>>(either { failing.confirm(card.clientId) }).value.code)
                    assertEquals(0, clientCount())
                    assertEquals("pending", di.database.sql("SELECT status FROM assistant_client_drafts WHERE client_id = :id").bind("id", card.clientId).firstOrNull { it.asString("status") })
                    assertEquals(0, di.database.sql("SELECT count(*)::int AS count FROM audit_logs WHERE entity_type = 'client'").firstOrNull { it.asInt("count") })
                    di.assistant.confirm(card.clientId)
                    assertEquals(1, clientCount())
                }
            }
        }

    /** Долгий запрос не использует старый снимок права перед save_client. */
    @Test
    fun reloadsPermissionBeforeSavingTool(): Unit =
        orThrow {
            runBlocking {
                context(owner) {
                    val permission = UserPermission.CAN_USE_ASSISTANT
                    val employee = di.database.transaction { di.employees.new(EmployeeId.new(), testName("Employee"), null, null, null, EmployeePermissionSelection(grantedPermissions = setOf(permission))) }
                    di.database.transaction { employee.invite(org.athletica.crm.core.EmailAddress("staff@example.com"), "password123") }
                    val staff = owner.copy(employee = di.database.transaction { di.employees.byId(employee.id) })
                    val card = context(staff) { prepareCard() }
                    fake.respond = { prompt ->
                        if (prompt.messages.last().parts.any { it is MessagePart.Tool.Result }) {
                            FakeAssistant.answer("Доступ отозван")
                        } else {
                            di.database.transaction { employee.withNew(testName("Employee"), EmployeePermissionSelection(revokedPermissions = setOf(permission)), null, null, null, employee.availableBranches).save() }
                            FakeAssistant.call("save_client", """{"clientId":"${card.clientId}"}""")
                        }
                    }
                    context(staff) {
                        val response = di.assistant.send(di.assistant.current().id, "да")
                        assertTrue(response.messages.last().blocks.none { it is AssistantBlock.ClientCreated })
                        assertEquals("ASSISTANT_FORBIDDEN", assertIs<Either.Left<AppError>>(either { di.assistant.confirm(card.clientId) }).value.code)
                    }
                    assertEquals(0, clientCount())
                }
            }
        }

    /** Деактивированный владелец не управляет настройками, даже если контекст ещё активен. */
    @Test
    fun deactivatedOwnerCannotManageSettings(): Unit =
        orThrow {
            runBlocking {
                di.database.sql("UPDATE employees SET is_active = false WHERE id = :id").bind("id", owner.employee.id).execute()
                context(owner) {
                    assertEquals("ASSISTANT_FORBIDDEN", assertIs<Either.Left<AppError>>(either { di.assistant.settings() }).value.code)
                    assertEquals("ASSISTANT_FORBIDDEN", assertIs<Either.Left<AppError>>(either { di.assistant.updateSettings(AssistantSettingsRequest(false, 100)) }).value.code)
                }
            }
        }

    /** Готовит карточку через настоящий цикл Koog. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    private suspend fun prepareCard(): AssistantBlock.ClientDraft {
        fake.respond = { prompt -> if (prompt.messages.last().parts.any { it is MessagePart.Tool.Result }) FakeAssistant.answer("Проверьте") else FakeAssistant.call("prepare_client", arguments) }
        return di.assistant.send(di.assistant.current().id, "добавь клиента").messages.last().blocks.filterIsInstance<AssistantBlock.ClientDraft>().single()
    }

    context(raise: Raise<AppError>)
    private suspend fun clientCount(): Int = di.database.sql("SELECT count(*)::int AS count FROM clients").firstOrNull { it.asInt("count") }!!
}
