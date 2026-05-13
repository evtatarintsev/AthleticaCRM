package org.athletica.crm.core.customfields

/**
 * Преобразует значение кастомного поля в строку для отображения в таблице или экспорта.
 * Логика общая для клиента и сервера, чтобы CSV-экспорт и UI показывали одинаковый текст.
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
