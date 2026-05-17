package org.athletica.crm.api.schemas.clients.import

import kotlinx.serialization.Serializable
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.UploadId

/**
 * Запрос на валидацию и/или фактический импорт клиентов из CSV.
 * Сначала вызывается с [dryRun] = true для предпросмотра ошибок,
 * затем — c [dryRun] = false для фактической записи.
 */
@Serializable
data class ClientImportCommitRequest(
    /** Идентификатор ранее загруженного CSV. */
    val uploadId: UploadId,
    /** Соответствия колонок CSV атрибутам клиента. */
    val columnMapping: List<ColumnMapping>,
    /** Пол, который проставляется клиенту, если колонка не замаплена или значение в строке не покрыто [genderMapping]. */
    val defaultGender: Gender,
    /** Маппинг конкретных значений колонки пола в [Gender]. Ключ — значение из CSV как есть. */
    val genderMapping: Map<String, Gender> = emptyMap(),
    /** Маппинг уникальных значений колонки источника в действия. Ключ — значение из CSV как есть. */
    val leadSourceMapping: Map<String, LeadSourceAction> = emptyMap(),
    /**
     * Опциональный кастомный формат даты в нотации [java.time.format.DateTimeFormatter] (например, `dd.MM.yyyy`).
     * Если null — сервер пробует распространённые форматы автоматически.
     */
    val dateFormat: String? = null,
    /** Если true — только валидирует и возвращает результат построчно, ничего не записывая. */
    val dryRun: Boolean,
)
