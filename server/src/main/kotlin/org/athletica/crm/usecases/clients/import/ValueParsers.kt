package org.athletica.crm.usecases.clients.import

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.Gender
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Чистые парсеры значений из CSV.
 * Стратегия: не угадывать локали — все неоднозначные значения должны быть явно сопоставлены
 * пользователем на шаге маппинга. Пустая строка/null во всех парсерах превращается в `null`.
 */
object ValueParsers {
    /**
     * Распространённые форматы дат, которые проверяются по умолчанию (если пользователь не задал свой).
     * Порядок имеет значение — первый подошедший выигрывает.
     */
    val DEFAULT_DATE_FORMATS: List<String> =
        listOf(
            "dd.MM.yyyy",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "d.M.yyyy",
        )

    /**
     * Парсит значение пола по пользовательскому маппингу.
     * [raw] — значение из ячейки CSV (как есть, без trim).
     * [mapping] — таблица «значение из CSV → [Gender]», собранная в UI на шаге маппинга.
     *
     * Семантика:
     * - пусто/null → `null` (клиенту проставится defaultGender уровнем выше);
     * - значение есть в [mapping] → возвращается соответствующий [Gender];
     * - значение не пустое и не покрыто [mapping] → ошибка `UNMAPPED_GENDER_VALUE`.
     */
    fun parseGender(raw: String?, mapping: Map<String, Gender>): Either<String, Gender?> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null.right()
        }
        return mapping[trimmed]?.right()
            ?: "UNMAPPED_GENDER_VALUE: $trimmed".left()
    }

    /**
     * Парсит дату из CSV.
     * Если [customPattern] задан — пробует только его (строгий разбор).
     * Иначе перебирает [DEFAULT_DATE_FORMATS]. Пустая строка → `null`.
     */
    fun parseDate(raw: String?, customPattern: String?): Either<String, LocalDate?> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null.right()
        }
        val patterns = customPattern?.let { listOf(it) } ?: DEFAULT_DATE_FORMATS
        patterns.forEach { pattern ->
            val parsed = tryParseDate(trimmed, pattern)
            if (parsed != null) {
                return parsed.right()
            }
        }
        return "INVALID_DATE: $trimmed".left()
    }

    /**
     * Парсит числовое значение (баланс, скидку и т.п.) из CSV.
     * Поддерживает запятую и точку как десятичный разделитель, удаляет обычные и неразрывные пробелы.
     * Пустая строка → `null`.
     */
    fun parseDecimal(raw: String?): Either<String, Double?> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null.right()
        }
        val normalized =
            trimmed
                .replace(" ", "")
                .replace(" ", "")
                .replace(',', '.')
        return normalized.toDoubleOrNull()?.right()
            ?: "INVALID_DECIMAL: $trimmed".left()
    }

    /**
     * Парсит ФИО клиента: trim и проверка непустоты.
     * Никакой постобработки — хвосты вида «(чт,сб)» остаются в имени.
     */
    fun parseName(raw: String?): Either<String, String> {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.isEmpty()) {
            "EMPTY_NAME".left()
        } else {
            trimmed.right()
        }
    }

    private fun tryParseDate(value: String, pattern: String): LocalDate? =
        try {
            java.time.LocalDate
                .parse(value, DateTimeFormatter.ofPattern(pattern))
                .toKotlinLocalDate()
        } catch (e: DateTimeParseException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
