package org.athletica.crm.components.clients

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.api.schemas.customfields.CustomFieldValue
import org.athletica.crm.api.schemas.settings.ClientsDisplaySettings

/**
 * Настройки отображения таблицы клиентов.
 * [columns] — упорядоченный список видимых колонок (стандартных и кастомных).
 * Колонка «Имя» всегда видна и не входит в этот список.
 */
data class ClientDisplaySettings(
    val columns: List<ClientColumn> = ClientColumn.Standard.entries.toList(),
)

/**
 * Опциональные колонки таблицы клиентов (кроме «Имя», которое всегда видно).
 * Может быть стандартной (Gender, BirthYear, Debt) или кастомной.
 */
sealed interface ClientColumn {
    /** Уникальный ключ, используемый при сохранении в API. */
    val apiKey: String

    /** Ширина колонки в пикселях. */
    val width: Dp

    /**
     * Стандартные колонки приложения.
     */
    enum class Standard(override val apiKey: String, override val width: Dp) : ClientColumn {
        Gender("gender", 52.dp),
        BirthYear("birth_year", 68.dp),
        Debt("debt", 84.dp),
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

/**
 * Преобразует локальные настройки отображения в API-модель для сохранения на сервер.
 */
fun ClientDisplaySettings.toApiModel(): ClientsDisplaySettings =
    ClientsDisplaySettings(
        columns = columns.map { it.apiKey },
    )

/**
 * Преобразует API-модель настроек в локальные настройки отображения.
 * [availableCustomFields] — список доступных кастомных полей для восстановления метаданных Custom-колонок.
 */
fun ClientsDisplaySettings.toDisplaySettings(
    availableCustomFields: List<CustomFieldDefinitionSchema>,
): ClientDisplaySettings {
    val customByKey = availableCustomFields.associateBy { it.fieldKey }
    return ClientDisplaySettings(
        columns =
            columns.mapNotNull { key ->
                ClientColumn.Standard.entries.find { it.apiKey == key }
                    ?: customByKey[key]?.let { ClientColumn.Custom(it.fieldKey, it.label) }
            },
    )
}

/**
 * Преобразует значение кастомного поля в строку для отображения в таблице.
 */
fun CustomFieldValue.displayValue(): String =
    when (this) {
        is CustomFieldValue.Text -> value
        is CustomFieldValue.Number -> {
            if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        }
        is CustomFieldValue.Bool -> if (value) "✓" else "—"
        is CustomFieldValue.Date -> value.toString()
        is CustomFieldValue.Select -> value
    }

/**
 * Возвращает значение кастомного поля по его ключу, либо null если поле не найдено.
 */
fun ClientListItem.field(fieldKey: String): CustomFieldValue? = customFields.firstOrNull { it.fieldKey == fieldKey }
