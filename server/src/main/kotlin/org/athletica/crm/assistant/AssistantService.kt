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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantDialogResponse
import org.athletica.crm.api.schemas.assistant.AssistantSettingsRequest
import org.athletica.crm.api.schemas.assistant.AssistantSettingsResponse
import org.athletica.crm.api.schemas.assistant.AssistantStatusResponse
import org.athletica.crm.assistant.imports.AttachedUpload
import org.athletica.crm.assistant.imports.Attachment
import org.athletica.crm.assistant.imports.AttachmentKind
import org.athletica.crm.assistant.imports.ImportDrafts
import org.athletica.crm.assistant.imports.ImportFiles
import org.athletica.crm.assistant.imports.ImportSource
import org.athletica.crm.assistant.imports.PhotoRecognizer
import org.athletica.crm.assistant.imports.PrepareImportArgs
import org.athletica.crm.assistant.imports.source
import org.athletica.crm.assistant.provider.AssistantConnection
import org.athletica.crm.assistant.provider.AssistantRunner
import org.athletica.crm.assistant.storage.AssistantStorage
import org.athletica.crm.assistant.tools.ClientDrafts
import org.athletica.crm.assistant.tools.PrepareClientArgs
import org.athletica.crm.assistant.tools.SaveClientArgs
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.storage.Transaction
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
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
    private val imports: ImportDrafts,
    private val recognizer: PhotoRecognizer,
    private val minio: MinioService,
) {
    /**
     * Статус доступен с сессией, даже без AI-ключа и права использования.
     * `permitted` — снимок сотрудника из контекста запроса; команды перепроверяют доступ в [access].
     */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun status(): AssistantStatusResponse =
        db.transaction {
            AssistantStatusResponse(connection.available, storage.settings(connection.available).enabled, ctx.employee.isActive && ctx.employee.hasPermission(UserPermission.CAN_USE_ASSISTANT))
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

    /** Пакетное подтверждение кнопкой: без модели и расхода квоты. */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun confirmImport(draft: Uuid): AssistantBlock.ImportCompleted =
        db.transaction {
            context(access()) { imports.save(imports.dialogOf(draft), draft) }
        }

    /**
     * Принимает сообщение атомарно, затем выполняет ограниченный ход без открытой транзакции.
     * Вложения [attachments] проверяются до приёма: недопустимый набор не расходует квоту.
     * Ход модели заканчивается раньше срока хода в БД, чтобы успеть сохранить ответ.
     * В журнал попадает только тип сбоя провайдера: его сообщение может содержать данные клиента.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    suspend fun send(dialog: Uuid, text: String, attachments: List<UploadId> = emptyList()): AssistantDialogResponse {
        val (uploads, files) = attachments(attachments)
        val table = files.singleOrNull()?.takeIf { it.kind != AttachmentKind.IMAGE }?.let { ImportFiles.table(it) }
        val deadline = if (files.isEmpty()) AssistantStorage.TURN_DEADLINE else AssistantStorage.ATTACHMENTS_TURN_DEADLINE
        val (message, history, fields) =
            db.transaction {
                context(access()) {
                    Triple(storage.accept(dialog, text, connection.available, uploads.map { it.name }, deadline), storage.history(dialog), Json.encodeToString(definitions.all("CLIENT")))
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
                tool(
                    object : Tool<PrepareImportArgs, String>(
                        typeToken<PrepareImportArgs>(),
                        typeToken<String>(),
                        "prepare_import",
                        "Map columns of an attached import source to client fields and display a NEW import card; never writes clients. Returns only a summary: counts, error codes with row numbers, possible duplicate rows and first names without gender.",
                    ) {
                        override suspend fun execute(args: PrepareImportArgs): String =
                            either {
                                db.transaction {
                                    context(access()) {
                                        imports.prepare(dialog, message, args).also { blocks.add(it.first) }.second
                                    }
                                }
                            }.fold({ Json.encodeToString(mapOf("error" to it.code)) }, { it })
                    },
                )
                tool(
                    object : Tool<SaveImportArgs, String>(typeToken<SaveImportArgs>(), typeToken<String>(), "save_import", "Create all ready clients of the last pending import card ONLY after explicit user confirmation in a later exchange. Use draftId returned by prepare_import.") {
                        override suspend fun execute(args: SaveImportArgs): String =
                            either {
                                val id =
                                    try {
                                        Uuid.parse(args.draftId)
                                    } catch (_: IllegalArgumentException) {
                                        raise(assistantError("VALIDATION"))
                                    }
                                db.transaction {
                                    context(access()) {
                                        imports.save(dialog, id, message).also { blocks.add(it) }
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
            withTimeout(deadline - 5.seconds) {
                either {
                    val sample = if (files.isEmpty()) null else source(dialog, message, files, uploads, table)
                    val input = listOfNotNull(text.trim().ifEmpty { null }, sample).joinToString("\n\n")
                    val tokens = if (files.isEmpty()) AssistantRunner.DEFAULT_MAX_TOKENS else AssistantRunner.ATTACHMENTS_MAX_TOKENS
                    AssistantRunner(connection).run(input, ctx.lang.code, history, registry, fields, tokens)
                }
            }.fold({ error = it.code }, {
                answer = it.text
                exchange = it.history
            })
        } catch (_: TimeoutCancellationException) {
            error = "ASSISTANT_TIMEOUT"
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                db.transaction { storage.finish(dialog, message, "", blocks, "[]", "ASSISTANT_INTERRUPTED") }
            }
            throw cancelled
        } catch (failure: Exception) {
            error = "ASSISTANT_PROVIDER_FAILURE"
            logger.warn("assistant turn={} failure={}", message, failure.message.takeIf { it in LIMIT_CODES } ?: failure::class.qualifiedName)
        }
        val response =
            db.transaction {
                storage.finish(dialog, message, answer, blocks, exchange, error)
                storage.dialog(dialog)
            }
        logger.info("assistant turn={} status={} durationMs={}", message, error ?: "completed", started.elapsedNow().inWholeMilliseconds)
        return response
    }

    /**
     * Проверяет вложения до приёма сообщения: загрузки своей организации, размер, тип по содержимому,
     * допустимый набор и наличие распознавания для фото. Файлы читаются вне транзакции.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    private suspend fun attachments(ids: List<UploadId>): Pair<List<AttachedUpload>, List<Attachment>> {
        if (ids.isEmpty()) {
            return emptyList<AttachedUpload>() to emptyList()
        }
        if (ids.size > ImportFiles.MAX_IMAGES) {
            raise(assistantError("IMPORT_TOO_MANY_PHOTOS"))
        }
        val uploads = db.transaction { context(access()) { imports.uploads(ids) } }
        if (uploads.any { it.sizeBytes > ImportFiles.MAX_BYTES }) {
            raise(assistantError("IMPORT_FILE_TOO_BIG"))
        }
        val files =
            uploads.map { upload ->
                val bytes = minio.downloadObject(upload.objectKey)
                val (kind, mime) = ImportFiles.detect(bytes) ?: raise(assistantError("IMPORT_UNSUPPORTED_FORMAT"))
                Attachment(upload.name, bytes, kind, mime)
            }
        ImportFiles.checkSet(files.map { it.kind })
        if (files.first().kind == AttachmentKind.IMAGE && !recognizer.available) {
            raise(assistantError("IMPORT_RECOGNITION_UNAVAILABLE"))
        }
        return uploads to files
    }

    /**
     * Получает строки (таблица уже прочитана, фото распознаются здесь), сохраняет источник диалога,
     * удаляет загрузки и возвращает недоверенный блок структуры источника для модели.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    private suspend fun source(
        dialog: Uuid,
        message: Uuid,
        files: List<Attachment>,
        uploads: List<AttachedUpload>,
        table: ImportSource?,
    ): String {
        val source = table ?: recognizer.source(files).also { ImportFiles.checkRows(it) }
        val kind = if (table == null) "photo" else "table"
        val id = db.transaction { context(access()) { imports.saveSource(dialog, message, kind, source, uploads) } }
        uploads.forEach { upload ->
            try {
                minio.deleteObject(upload.objectKey)
            } catch (failure: Exception) {
                logger.warn("assistant turn={} upload cleanup failure={}", message, failure::class.qualifiedName)
            }
        }
        return source.sample(id, kind)
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

        /** Собственные коды ограничений [AssistantRunner]; безопасны для журнала. */
        val LIMIT_CODES = setOf("ASSISTANT_STEPS", "ASSISTANT_OUTPUT")
    }
}

/** Только ID ранее показанной пакетной карточки. */
@Serializable
internal data class SaveImportArgs(val draftId: String)
