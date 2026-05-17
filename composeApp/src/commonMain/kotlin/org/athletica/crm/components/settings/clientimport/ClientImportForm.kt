package org.athletica.crm.components.settings.clientimport

import org.athletica.crm.api.schemas.clients.import.ImportTarget
import org.athletica.crm.api.schemas.clients.import.LeadSourceAction
import org.athletica.crm.components.clients.ClientsApiError
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.UploadId

/**
 * Иммутабельное состояние формы импорта клиентов из CSV.
 * Хранится в [ClientImportViewModel] и копируется при каждом изменении.
 */
data class ClientImportForm(
    /** Идентификатор загруженного CSV, появляется после успешной загрузки на сервер. */
    val uploadId: UploadId? = null,
    /** Имя загруженного файла, отображается в шапке мастера. */
    val originalName: String = "",
    /** Соответствие колонок CSV атрибутам клиента: ключ — имя колонки, значение — цель. */
    val columnMapping: Map<String, ImportTarget> = emptyMap(),
    /** Пол по умолчанию для строк, где значение пола не покрыто [genderMapping] или колонка не замаплена. */
    val defaultGender: Gender = Gender.MALE,
    /** Маппинг значений колонки пола в [Gender]. Ключ — значение из CSV как есть. */
    val genderMapping: Map<String, Gender> = emptyMap(),
    /** Маппинг значений колонки источника в действия (использовать существующий / создать / пропустить). */
    val leadSourceMapping: Map<String, LeadSourceAction> = emptyMap(),
    /** Опциональный формат даты в нотации [java.time.format.DateTimeFormatter]; пустая строка → авто. */
    val dateFormat: String = "",
)

/** Этап мастера импорта: загрузка, маппинг, предпросмотр результата, итог. */
sealed class ClientImportPhase {
    /** Шаг 1: выбор файла. */
    data object Upload : ClientImportPhase()

    /** Шаг 2: задание соответствий колонок и значений. */
    data object Mapping : ClientImportPhase()

    /** Шаг 3: предпросмотр результата валидации (dryRun). */
    data object Preview : ClientImportPhase()

    /** Шаг 4: финальный отчёт после фактической записи. */
    data object Done : ClientImportPhase()
}

/** Состояние асинхронной операции на текущем шаге. */
sealed class ClientImportAction {
    /** Никакой операции не выполняется, можно взаимодействовать с формой. */
    data object Idle : ClientImportAction()

    /** Выполняется загрузка / парсинг / валидация / импорт. */
    data object Working : ClientImportAction()

    /** Сервер вернул ошибку — содержит [error] для показа в UI. */
    data class Error(val error: ClientsApiError) : ClientImportAction()
}
