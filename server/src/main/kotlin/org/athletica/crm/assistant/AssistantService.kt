package org.athletica.crm.assistant

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.typeToken
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantDialogResponse
import org.athletica.crm.api.schemas.assistant.AssistantSettingsRequest
import org.athletica.crm.api.schemas.assistant.AssistantSettingsResponse
import org.athletica.crm.api.schemas.assistant.AssistantStatusResponse
import org.athletica.crm.assistant.provider.AssistantConnection
import org.athletica.crm.assistant.provider.AssistantRunner
import org.athletica.crm.assistant.storage.AssistantStorage
import org.athletica.crm.assistant.tools.ClientDrafts
import org.athletica.crm.assistant.tools.PrepareClientArgs
import org.athletica.crm.assistant.tools.SaveClientArgs
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Прикладная оркестрация: DB-транзакции короткие, запросы к AI выполняются между ними. */
class AssistantService(
    private val db: Database,
    private val connection: AssistantConnection,
    private val storage: AssistantStorage,
    private val drafts: ClientDrafts,
    private val employees: Employees,
    private val definitions: CustomFieldDefinitions,
) {
    /** Статус доступен с сессией, даже без AI-ключа и права использования. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun status(): AssistantStatusResponse =
        db.transaction {
            val employee = employees.byId(ctx.employee.id)
            AssistantStatusResponse(connection.available, storage.settings(connection.available).enabled, employee.isActive && employee.hasPermission(UserPermission.CAN_USE_ASSISTANT))
        }

    /** Только владелец получает настройки и расход организации. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun settings(): AssistantSettingsResponse =
        db.transaction {
            requireOwner()
            storage.settings(connection.available)
        }

    /** Включение недоступно без подключения; выключение всегда возможно. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun updateSettings(request: AssistantSettingsRequest): AssistantSettingsResponse =
        db.transaction {
            requireOwner()
            if (request.enabled && !connection.available) {
                raise(assistantError("UNAVAILABLE"))
            }
            storage.updateSettings(request.enabled, request.dailyMessageLimit)
            storage.settings(connection.available)
        }

    /** Восстанавливает последний личный диалог после закрытия панели или рестарта. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun current(): AssistantDialogResponse =
        db.transaction {
            context(access()) { storage.current() }
        }

    /** Начинает новый диалог, сохраняя прежнюю историю на сервере. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun create(): AssistantDialogResponse =
        db.transaction {
            context(access()) { storage.create() }
        }

    /** Прямое подтверждение не вызывает модель и не расходует квоту. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun confirm(client: ClientId): AssistantBlock.ClientCreated =
        db.transaction {
            context(access()) { drafts.save(drafts.dialogOf(client), client) }
        }

    /** Принимает сообщение атомарно, затем выполняет ограниченный ход без открытой транзакции. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun send(dialog: Uuid, text: String): AssistantDialogResponse {
        val (message, history, fields) =
            db.transaction {
                context(access()) {
                    Triple(storage.accept(dialog, text, connection.available), storage.history(dialog), Json.encodeToString(definitions.all("CLIENT")))
                }
            }
        val blocks = mutableListOf<AssistantBlock>()
        val registry =
            ToolRegistry {
                tool(
                    object : Tool<PrepareClientArgs, String>(typeToken<PrepareClientArgs>(), typeToken<String>(), "prepare_client", "Validate all data and display a NEW client confirmation card; never writes a client. Custom field values are strings keyed by field key; ask for missing required fields.") {
                        override suspend fun execute(args: PrepareClientArgs): String =
                            either {
                                db.transaction {
                                    context(access()) {
                                        drafts.prepare(dialog, message, args).also { blocks.add(it) }
                                    }
                                }
                            }.fold({ Json.encodeToString(mapOf("error" to it.code, "field" to it.message)) }, { Json.encodeToString(it) })
                    },
                )
                tool(
                    object : Tool<SaveClientArgs, String>(typeToken<SaveClientArgs>(), typeToken<String>(), "save_client", "Save the last pending client card ONLY after explicit user confirmation in a later exchange. Use clientId returned by prepare_client.") {
                        override suspend fun execute(args: SaveClientArgs): String =
                            either {
                                val id =
                                    try {
                                        Uuid.parse(args.clientId).toClientId()
                                    } catch (_: IllegalArgumentException) {
                                        raise(assistantError("VALIDATION"))
                                    }
                                db.transaction {
                                    context(access()) {
                                        drafts.save(dialog, id, message).also { blocks.add(it) }
                                    }
                                }
                            }.fold({ Json.encodeToString(mapOf("error" to it.code)) }, { Json.encodeToString(it) })
                    },
                )
            }
        val started = TimeSource.Monotonic.markNow()
        var error: String? = null
        var answer = ""
        var exchange = "[]"
        try {
            val result = withTimeout(45_000) { AssistantRunner(connection).run(text, ctx.lang.code, history, registry, fields) }
            answer = result.text
            exchange = result.history
        } catch (_: TimeoutCancellationException) {
            error = "ASSISTANT_TIMEOUT"
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                db.transaction { storage.finish(dialog, message, "", blocks, "[]", "ASSISTANT_INTERRUPTED") }
            }
            throw cancelled
        } catch (_: Exception) {
            error = "ASSISTANT_PROVIDER_FAILURE"
        }
        val response =
            db.transaction {
                storage.finish(dialog, message, answer, blocks, exchange, error)
                storage.dialog(dialog)
            }
        logger.info("assistant turn={} status={} durationMs={}", message, error ?: "completed", started.elapsedNow().inWholeMilliseconds)
        return response
    }

    /** Перечитывает сотрудника перед каждым tool; блокировка настроек сериализует квоту и отключение. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun access(): EmployeeRequestContext {
        val employee = employees.byId(ctx.employee.id)
        if (!employee.isActive || !employee.hasPermission(UserPermission.CAN_USE_ASSISTANT)) {
            raise(assistantError("FORBIDDEN"))
        }
        if (!connection.available) {
            raise(assistantError("UNAVAILABLE"))
        }
        if (!storage.settings(connection.available, lock = true).enabled) {
            raise(assistantError("DISABLED"))
        }
        return ctx.copy(employee = employee)
    }

    /** Проверяет владельца по актуальной записи, включая активность. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun requireOwner() {
        val employee = employees.byId(ctx.employee.id)
        if (!employee.isActive || !employee.isOwner) {
            raise(assistantError("FORBIDDEN"))
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(AssistantService::class.java)
    }
}
