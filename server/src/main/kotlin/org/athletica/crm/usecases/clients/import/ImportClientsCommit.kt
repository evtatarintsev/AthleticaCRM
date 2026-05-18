package org.athletica.crm.usecases.clients.import

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse.CreatedLeadSource
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse.RowResult
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse.Status
import org.athletica.crm.api.schemas.clients.import.ColumnMapping
import org.athletica.crm.api.schemas.clients.import.ImportTarget
import org.athletica.crm.api.schemas.clients.import.LeadSourceAction
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.CustomFieldValues
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.toMoney
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.leadSource.LeadSource
import org.athletica.crm.domain.leadSource.LeadSources
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.storage.Transaction

private const val MAX_CSV_SIZE_BYTES = 10L * 1024 * 1024
private const val CLIENT_ENTITY_TYPE = "CLIENT"

/**
 * Валидирует и (при `dryRun = false`) импортирует клиентов из ранее загруженного CSV
 * согласно [ClientImportCommitRequest.columnMapping] и сопутствующим маппингам.
 *
 * Импорт идёт в одной транзакции. Строки с ошибками не блокируют импорт остальных —
 * каждая строка обрабатывается изолированно, ошибочные попадают в [RowResult.errors].
 */
context(db: Database, minio: MinioService, ctx: RequestContext)
suspend fun importClientsCommit(
    request: ClientImportCommitRequest,
    clients: Clients,
    balances: ClientBalances,
    leadSources: LeadSources,
    definitions: CustomFieldDefinitions,
): Either<DomainError, ClientImportCommitResponse> =
    either {
        val upload = db.transaction { uploadRecordById(request.uploadId) }

        if (upload.sizeBytes > MAX_CSV_SIZE_BYTES) {
            raise(CommonDomainError("FILE_TOO_BIG", "Файл превышает 10 МБ"))
        }

        validateMapping(request.columnMapping).bind()

        val bytes = minio.downloadObject(upload.objectKey)
        val parsed =
            try {
                CsvReader.parse(bytes)
            } catch (e: Exception) {
                raise(CommonDomainError("CSV_PARSE_FAILED", "Не удалось разобрать CSV: ${e.message ?: ""}"))
            }

        val columnIndex = parsed.headers.withIndex().associate { (i, h) -> h to i }
        val plan = buildPlan(request.columnMapping, columnIndex).bind()

        db.transaction {
            val existingLeadSources = leadSources.list().associateBy { it.name }
            val customDefs =
                definitions
                    .all(CLIENT_ENTITY_TYPE)
                    .associateBy { it.fieldKey.value }

            val createdLeadSources =
                if (request.dryRun) {
                    emptyList()
                } else {
                    createMissingLeadSources(
                        leadSourceMapping = request.leadSourceMapping,
                        existingByName = existingLeadSources,
                        leadSources = leadSources,
                    )
                }

            val leadSourceIdByCsvValue: Map<String, LeadSourceId> =
                resolveLeadSourceIds(
                    leadSourceMapping = request.leadSourceMapping,
                    existingByName = existingLeadSources,
                    created = createdLeadSources,
                )

            val balanceNote = "Импорт CSV ${upload.originalName} от ${today()}"
            val rows =
                parsed.rows.mapIndexed { idx, row ->
                    processRow(
                        rowNumber = idx + 1,
                        row = row,
                        plan = plan,
                        request = request,
                        customDefs = customDefs,
                        leadSourceIdByCsvValue = leadSourceIdByCsvValue,
                        clients = clients,
                        balances = balances,
                        balanceNote = balanceNote,
                    )
                }

            ClientImportCommitResponse(
                totalRows = parsed.rows.size,
                imported = rows.count { it.status == Status.OK },
                skipped = rows.count { it.status == Status.ERROR },
                rows = rows,
                createdLeadSources = createdLeadSources.map { CreatedLeadSource(it.id, it.name) },
            )
        }
    }

/**
 * Готовый к исполнению план импорта: индексы CSV-колонок для каждого назначения.
 * Custom-поля собраны в карту `csvIndex → fieldKey`.
 */
private data class ImportPlan(
    val nameIndex: Int,
    val birthdayIndex: Int?,
    val genderIndex: Int?,
    val leadSourceIndex: Int?,
    val balanceIndex: Int?,
    val customFieldIndices: Map<Int, String>,
)

private fun validateMapping(mapping: List<ColumnMapping>): Either<DomainError, Unit> =
    either {
        val builtInCounts =
            mapping
                .groupingBy { it.target::class.qualifiedName ?: "?" }
                .eachCount()
        if (mapping.none { it.target is ImportTarget.Name }) {
            raise(CommonDomainError("MAPPING_NAME_MISSING", "Не выбрана колонка с ФИО"))
        }
        listOf(
            ImportTarget.Name::class.qualifiedName!! to "MAPPING_NAME_DUPLICATED",
            ImportTarget.Birthday::class.qualifiedName!! to "MAPPING_BIRTHDAY_DUPLICATED",
            ImportTarget.Gender::class.qualifiedName!! to "MAPPING_GENDER_DUPLICATED",
            ImportTarget.LeadSource::class.qualifiedName!! to "MAPPING_LEAD_SOURCE_DUPLICATED",
            ImportTarget.Balance::class.qualifiedName!! to "MAPPING_BALANCE_DUPLICATED",
        ).forEach { (className, code) ->
            if ((builtInCounts[className] ?: 0) > 1) {
                raise(CommonDomainError(code, "В маппинге несколько колонок ссылаются на одно поле"))
            }
        }
    }

private fun buildPlan(
    mapping: List<ColumnMapping>,
    columnIndex: Map<String, Int>,
): Either<DomainError, ImportPlan> =
    either {
        fun indexOf(column: String): Int =
            columnIndex[column]
                ?: raise(CommonDomainError("MAPPING_UNKNOWN_COLUMN", "Колонка не найдена в CSV: $column"))

        var nameIdx: Int? = null
        var birthdayIdx: Int? = null
        var genderIdx: Int? = null
        var leadSourceIdx: Int? = null
        var balanceIdx: Int? = null
        val customs = mutableMapOf<Int, String>()
        mapping.forEach { cm ->
            when (val target = cm.target) {
                ImportTarget.Skip -> {}
                ImportTarget.Name -> nameIdx = indexOf(cm.sourceColumn)
                ImportTarget.Birthday -> birthdayIdx = indexOf(cm.sourceColumn)
                ImportTarget.Gender -> genderIdx = indexOf(cm.sourceColumn)
                ImportTarget.LeadSource -> leadSourceIdx = indexOf(cm.sourceColumn)
                ImportTarget.Balance -> balanceIdx = indexOf(cm.sourceColumn)
                is ImportTarget.CustomField -> customs[indexOf(cm.sourceColumn)] = target.key.value
            }
        }

        ImportPlan(
            nameIndex = nameIdx ?: raise(CommonDomainError("MAPPING_NAME_MISSING", "Не выбрана колонка с ФИО")),
            birthdayIndex = birthdayIdx,
            genderIndex = genderIdx,
            leadSourceIndex = leadSourceIdx,
            balanceIndex = balanceIdx,
            customFieldIndices = customs,
        )
    }

context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun createMissingLeadSources(
    leadSourceMapping: Map<String, LeadSourceAction>,
    existingByName: Map<String, LeadSource>,
    leadSources: LeadSources,
): List<LeadSource> {
    val toCreate =
        leadSourceMapping.values
            .filterIsInstance<LeadSourceAction.CreateNew>()
            .map { it.name.trim() }
            .filter { it.isNotEmpty() && !existingByName.containsKey(it) }
            .distinct()
    return toCreate.map { name ->
        val created = LeadSource(id = LeadSourceId.new(), name = name)
        leadSources.create(created)
        created
    }
}

private fun resolveLeadSourceIds(
    leadSourceMapping: Map<String, LeadSourceAction>,
    existingByName: Map<String, LeadSource>,
    created: List<LeadSource>,
): Map<String, LeadSourceId> {
    val byName = existingByName + created.associateBy { it.name }
    return leadSourceMapping.mapNotNull { (csvValue, action) ->
        when (action) {
            is LeadSourceAction.UseExisting -> csvValue to action.id
            is LeadSourceAction.CreateNew -> byName[action.name.trim()]?.let { csvValue to it.id }
            LeadSourceAction.Skip -> null
        }
    }.toMap()
}

context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun processRow(
    rowNumber: Int,
    row: List<String?>,
    plan: ImportPlan,
    request: ClientImportCommitRequest,
    customDefs: Map<String, CustomFieldDefinition>,
    leadSourceIdByCsvValue: Map<String, LeadSourceId>,
    clients: Clients,
    balances: ClientBalances,
    balanceNote: String,
): RowResult {
    val errors = mutableListOf<String>()

    val name =
        ValueParsers.parseName(row.getOrNull(plan.nameIndex))
            .fold(
                ifLeft = {
                    errors += "Пустое имя"
                    null
                },
                ifRight = { it },
            )

    val birthday: LocalDate? =
        plan.birthdayIndex?.let { idx ->
            ValueParsers.parseDate(row.getOrNull(idx), request.dateFormat)
                .fold(
                    ifLeft = {
                        errors += "Не удалось распознать дату: ${row.getOrNull(idx)}"
                        null
                    },
                    ifRight = { it },
                )
        }

    val gender: Gender =
        plan.genderIndex
            ?.let { idx ->
                ValueParsers.parseGender(row.getOrNull(idx), request.genderMapping)
                    .fold(
                        ifLeft = {
                            errors += "Неизвестное значение пола: ${row.getOrNull(idx)}"
                            null
                        },
                        ifRight = { it },
                    )
            } ?: request.defaultGender

    val leadSourceId: LeadSourceId? =
        plan.leadSourceIndex?.let { idx ->
            val raw = row.getOrNull(idx)?.trim().orEmpty()
            if (raw.isEmpty()) null else leadSourceIdByCsvValue[raw]
        }

    val balance: Double? =
        plan.balanceIndex?.let { idx ->
            ValueParsers.parseDecimal(row.getOrNull(idx))
                .fold(
                    ifLeft = {
                        errors += "Не удалось распознать баланс: ${row.getOrNull(idx)}"
                        null
                    },
                    ifRight = { it },
                )
        }

    val customFields = collectCustomFields(row, plan, customDefs, errors)

    if (errors.isNotEmpty() || name == null) {
        return RowResult(rowNumber = rowNumber, status = Status.ERROR, errors = errors)
    }

    if (request.dryRun) {
        return RowResult(rowNumber = rowNumber, status = Status.OK)
    }

    return either {
        val client =
            clients.new(
                id = ClientId.new(),
                name = name,
                avatarId = null,
                birthday = birthday,
                gender = gender,
                leadSourceId = leadSourceId,
                customFields = customFields,
            )
        if (balance != null && balance != 0.0) {
            balances.currentOf(client).adjust(balance.toMoney(ctx.currency), balanceNote)
        }
        RowResult(rowNumber = rowNumber, status = Status.OK)
    }.fold(
        ifLeft = { err ->
            RowResult(
                rowNumber = rowNumber,
                status = Status.ERROR,
                errors = listOf(err.message),
            )
        },
        ifRight = { it },
    )
}

private fun collectCustomFields(
    row: List<String?>,
    plan: ImportPlan,
    defs: Map<String, CustomFieldDefinition>,
    errors: MutableList<String>,
): List<CustomFieldValue> {
    if (plan.customFieldIndices.isEmpty()) {
        return emptyList()
    }
    val values =
        plan.customFieldIndices.mapNotNull { (idx, key) ->
            val raw = row.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val def =
                defs[key] ?: run {
                    errors += "Неизвестное кастомное поле: $key"
                    return@mapNotNull null
                }
            buildCustomFieldValue(def, raw, errors)
        }
    val container = CustomFieldValues(defs.values.toList()).with(values)
    return container.fold(
        ifLeft = { err ->
            errors += err.message
            emptyList()
        },
        ifRight = { it.toList() },
    )
}

private fun buildCustomFieldValue(
    def: CustomFieldDefinition,
    raw: String,
    errors: MutableList<String>,
): CustomFieldValue? =
    when (def) {
        is CustomFieldDefinition.Text,
        is CustomFieldDefinition.Phone,
        is CustomFieldDefinition.Email,
        is CustomFieldDefinition.Url,
        -> CustomFieldValue.Text(def.fieldKey, raw)

        is CustomFieldDefinition.Number ->
            ValueParsers.parseDecimal(raw).fold(
                ifLeft = {
                    errors += "Не удалось распознать число для поля ${def.fieldKey.value}"
                    null
                },
                ifRight = { num -> num?.let { CustomFieldValue.Number(def.fieldKey, it) } },
            )

        is CustomFieldDefinition.Date ->
            ValueParsers.parseDate(raw, customPattern = null).fold(
                ifLeft = {
                    errors += "Не удалось распознать дату для поля ${def.fieldKey.value}"
                    null
                },
                ifRight = { date -> date?.let { CustomFieldValue.Date(def.fieldKey, it) } },
            )

        is CustomFieldDefinition.Bool -> CustomFieldValue.Bool(def.fieldKey, parseBool(raw))

        is CustomFieldDefinition.Select ->
            if (def.options.any { it.equals(raw, ignoreCase = true) }) {
                val canonical = def.options.first { it.equals(raw, ignoreCase = true) }
                CustomFieldValue.Select(def.fieldKey, canonical)
            } else {
                errors += "Значение «$raw» не входит в варианты поля ${def.fieldKey.value}"
                null
            }
    }

private fun parseBool(raw: String): Boolean =
    when (raw.trim().lowercase()) {
        "1", "true", "да", "yes", "y", "+" -> true
        else -> false
    }

private fun today(): LocalDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toKotlinLocalDate()
