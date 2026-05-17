package org.athletica.crm.components.settings.clientimport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse
import org.athletica.crm.api.schemas.clients.import.ClientImportParseRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportParseResponse
import org.athletica.crm.api.schemas.clients.import.ColumnMapping
import org.athletica.crm.api.schemas.clients.import.ImportTarget
import org.athletica.crm.api.schemas.clients.import.LeadSourceAction
import org.athletica.crm.api.schemas.leadSources.LeadSourceDetailResponse
import org.athletica.crm.components.clients.toClientsApiError
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.pickAnyFile

/**
 * Управляет всем потоком импорта клиентов из CSV: загрузка файла, разбор, маппинг,
 * валидация (dryRun) и финальная запись.
 *
 * Состояние разложено по нескольким переменным, чтобы UI мог реактивно отображать
 * прогресс на каждом шаге без потери частичных пользовательских правок.
 */
class ClientImportViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    /** Текущий шаг мастера. */
    var phase by mutableStateOf<ClientImportPhase>(ClientImportPhase.Upload)
        private set

    /** Иммутабельная форма импорта. */
    var form by mutableStateOf(ClientImportForm())
        private set

    /** Состояние асинхронной операции, активной на текущем шаге. */
    var action by mutableStateOf<ClientImportAction>(ClientImportAction.Idle)
        private set

    /** Результат разбора CSV: заголовки, образцы, уникальные значения. */
    var parsePreview by mutableStateOf<ClientImportParseResponse?>(null)
        private set

    /** Результат предпросмотра валидации (dryRun = true). */
    var validation by mutableStateOf<ClientImportCommitResponse?>(null)
        private set

    /** Финальный отчёт после фактического импорта (dryRun = false). */
    var commitResult by mutableStateOf<ClientImportCommitResponse?>(null)
        private set

    /** Список источников организации — для выпадающих списков на шаге маппинга. */
    var leadSources by mutableStateOf<List<LeadSourceDetailResponse>>(emptyList())
        private set

    /** Список существующих CustomFieldDefinition для CLIENT — для маппинга колонок в кастомные поля. */
    var customFieldDefs by mutableStateOf<List<CustomFieldDefinition>>(emptyList())
        private set

    init {
        loadReferences()
    }

    /** Загружает справочники, нужные на шаге маппинга. */
    private fun loadReferences() {
        scope.launch {
            api.leadSources.list().onRight { resp ->
                leadSources = resp.leadSources
            }
            api.customFields.list(CLIENT_ENTITY_TYPE).onRight { defs ->
                customFieldDefs = defs
            }
        }
    }

    /** Открывает системный диалог выбора файла, загружает его на сервер и парсит. */
    fun onPickFile() {
        scope.launch {
            val picked = pickAnyFile() ?: return@launch
            val (bytes, filename, contentType) = picked
            action = ClientImportAction.Working
            api.documents
                .upload(bytes, filename, contentType)
                .fold(
                    ifLeft = { action = ClientImportAction.Error(it.toClientsApiError()) },
                    ifRight = { uploaded ->
                        api.clients
                            .importParse(ClientImportParseRequest(uploaded.id))
                            .fold(
                                ifLeft = { action = ClientImportAction.Error(it.toClientsApiError()) },
                                ifRight = { preview ->
                                    parsePreview = preview
                                    form =
                                        form.copy(
                                            uploadId = uploaded.id,
                                            originalName = preview.originalName,
                                            columnMapping =
                                                preview.columns.associateWith { ImportTarget.Skip },
                                        )
                                    phase = ClientImportPhase.Mapping
                                    action = ClientImportAction.Idle
                                },
                            )
                    },
                )
        }
    }

    /** Меняет цель колонки в маппинге. */
    fun onColumnTargetChanged(column: String, target: ImportTarget) {
        form = form.copy(columnMapping = form.columnMapping + (column to target))
    }

    /** Меняет пол по умолчанию. */
    fun onDefaultGenderChanged(gender: Gender) {
        form = form.copy(defaultGender = gender)
    }

    /** Сопоставляет значение из колонки пола в конкретный [Gender]. */
    fun onGenderMappingChanged(csvValue: String, gender: Gender) {
        form = form.copy(genderMapping = form.genderMapping + (csvValue to gender))
    }

    /** Меняет действие для конкретного значения источника. */
    fun onLeadSourceMappingChanged(csvValue: String, action: LeadSourceAction) {
        form = form.copy(leadSourceMapping = form.leadSourceMapping + (csvValue to action))
    }

    /** Меняет пользовательский формат даты. Пустая строка означает «угадывать». */
    fun onDateFormatChanged(value: String) {
        form = form.copy(dateFormat = value)
    }

    /** Запускает dryRun, чтобы посчитать ошибки до фактического импорта. */
    fun onValidate() {
        val uploadId = form.uploadId ?: return
        action = ClientImportAction.Working
        scope.launch {
            api.clients
                .importCommit(
                    ClientImportCommitRequest(
                        uploadId = uploadId,
                        columnMapping = form.columnMapping.map { (col, target) -> ColumnMapping(col, target) },
                        defaultGender = form.defaultGender,
                        genderMapping = form.genderMapping,
                        leadSourceMapping = form.leadSourceMapping,
                        dateFormat = form.dateFormat.takeIf { it.isNotBlank() },
                        dryRun = true,
                    ),
                ).fold(
                    ifLeft = { action = ClientImportAction.Error(it.toClientsApiError()) },
                    ifRight = { result ->
                        validation = result
                        phase = ClientImportPhase.Preview
                        action = ClientImportAction.Idle
                    },
                )
        }
    }

    /** Запускает фактический импорт (dryRun = false). */
    fun onCommit() {
        val uploadId = form.uploadId ?: return
        action = ClientImportAction.Working
        scope.launch {
            api.clients
                .importCommit(
                    ClientImportCommitRequest(
                        uploadId = uploadId,
                        columnMapping = form.columnMapping.map { (col, target) -> ColumnMapping(col, target) },
                        defaultGender = form.defaultGender,
                        genderMapping = form.genderMapping,
                        leadSourceMapping = form.leadSourceMapping,
                        dateFormat = form.dateFormat.takeIf { it.isNotBlank() },
                        dryRun = false,
                    ),
                ).fold(
                    ifLeft = { action = ClientImportAction.Error(it.toClientsApiError()) },
                    ifRight = { result ->
                        commitResult = result
                        phase = ClientImportPhase.Done
                        action = ClientImportAction.Idle
                    },
                )
        }
    }

    /** Возвращает мастер на предыдущий шаг. */
    fun onBack() {
        phase =
            when (phase) {
                ClientImportPhase.Upload -> ClientImportPhase.Upload
                ClientImportPhase.Mapping -> ClientImportPhase.Upload
                ClientImportPhase.Preview -> ClientImportPhase.Mapping
                ClientImportPhase.Done -> ClientImportPhase.Mapping
            }
        if (phase == ClientImportPhase.Upload) {
            form = ClientImportForm()
            parsePreview = null
        }
        validation = null
        commitResult = null
        action = ClientImportAction.Idle
    }

    /** Скрывает баннер ошибки. */
    fun onErrorDismissed() {
        action = ClientImportAction.Idle
    }

    private companion object {
        const val CLIENT_ENTITY_TYPE = "CLIENT"
    }
}
