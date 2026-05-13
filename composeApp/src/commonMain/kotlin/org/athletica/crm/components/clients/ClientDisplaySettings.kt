package org.athletica.crm.components.clients

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.api.schemas.settings.ClientsDisplaySettings
import org.athletica.crm.core.customfields.CustomFieldDefinition

/**
 * Настройки отображения таблицы клиентов.
 * [columns] — упорядоченный список видимых колонок (стандартных и кастомных).
 * Колонка «Имя» всегда видна и не входит в этот список.
 */
data class ClientDisplaySettings(
    val columns: List<ClientColumn> = defaultStandardColumns(),
)

/**
 * Опциональные колонки таблицы клиентов (кроме «Имя», которое всегда видно).
 * Может быть стандартной (на основе [ClientField]) или кастомной.
 */
sealed interface ClientColumn {
    /** Уникальный ключ, используемый при сохранении в API. */
    val apiKey: String

    /** Ширина колонки в пикселях. */
    val width: Dp

    /** Стандартная колонка на основе значения [ClientField]. */
    data class Standard(val clientField: ClientField) : ClientColumn {
        override val apiKey: String = clientField.apiKey
        override val width: Dp = STANDARD_WIDTHS[clientField] ?: 96.dp
    }

    /**
     * Кастомная колонка, основанная на пользовательском поле.
     * [label] — отображаемое название (из определения поля).
     */
    data class Custom(
        override val apiKey: String,
        val label: String,
        override val width: Dp = 96.dp,
    ) : ClientColumn
}

/** Ширины колонок для стандартных полей клиента. */
private val STANDARD_WIDTHS: Map<ClientField, Dp> =
    mapOf(
        ClientField.GENDER to 52.dp,
        ClientField.BIRTHDAY to 96.dp,
        ClientField.BALANCE to 84.dp,
        ClientField.GROUPS to 160.dp,
    )

/** Возвращает дефолтный набор стандартных колонок таблицы клиентов. */
private fun defaultStandardColumns(): List<ClientColumn> =
    listOf(
        ClientColumn.Standard(ClientField.GENDER),
        ClientColumn.Standard(ClientField.BIRTHDAY),
        ClientColumn.Standard(ClientField.BALANCE),
    )

/** Преобразует локальные настройки отображения в API-модель для сохранения на сервер. */
fun ClientDisplaySettings.toApiModel(): ClientsDisplaySettings =
    ClientsDisplaySettings(
        columns = columns.map { it.apiKey },
    )

/**
 * Преобразует API-модель настроек в локальные настройки отображения.
 * [availableCustomFields] — список доступных кастомных полей для восстановления метаданных Custom-колонок.
 * Неизвестные ключи (например, удалённые поля или устаревшие) молча отбрасываются.
 */
fun ClientsDisplaySettings.toDisplaySettings(availableCustomFields: List<CustomFieldDefinition>): ClientDisplaySettings {
    val customByKey = availableCustomFields.associateBy { it.fieldKey.value }
    return ClientDisplaySettings(
        columns =
            columns.mapNotNull { key ->
                ClientField.byKey(key)?.let { ClientColumn.Standard(it) }
                    ?: customByKey[key]?.let { ClientColumn.Custom(it.fieldKey.value, it.label) }
            },
    )
}
