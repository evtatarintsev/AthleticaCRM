package org.athletica.crm.routes.export

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.RoutingCall
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.athletica.crm.api.schemas.export.ExportFormat
import org.athletica.crm.core.money.Money
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/** Ячейка выгрузки: тип определяет запись в CSV и формат ячейки в Excel. */
sealed interface ExportCell {
    /** Текст; в CSV экранируется от формул апострофом. */
    data class Text(
        /** Значение ячейки. */
        val value: String,
    ) : ExportCell

    /** Целое число. */
    data class Number(
        /** Значение ячейки. */
        val value: Long,
    ) : ExportCell

    /** Доля: `0.125` — 12,5%. */
    data class Percent(
        /** Значение ячейки как доля единицы. */
        val value: Double,
    ) : ExportCell

    /** Денежная сумма; пишется числом без символа валюты, в Excel — с форматом валюты. */
    data class Amount(
        /** Значение ячейки. */
        val value: Money,
    ) : ExportCell

    /** Дата. */
    data class Day(
        /** Значение ячейки. */
        val value: LocalDate,
    ) : ExportCell

    /** Пустая ячейка. */
    data object Empty : ExportCell
}

/** Текстовая ячейка из [value]; `null` — пустая ячейка. */
fun textCell(value: String?): ExportCell = if (value.isNullOrEmpty()) ExportCell.Empty else ExportCell.Text(value)

/** Таблица выгрузки: заголовки [headers] и строки [rows] одинаковой длины. */
data class ExportTable(
    /** Заголовки колонок. */
    val headers: List<String>,
    /** Строки таблицы; в каждой столько же ячеек, сколько заголовков. */
    val rows: List<List<ExportCell>>,
) {
    init {
        require(rows.all { it.size == headers.size }) { "строка выгрузки не совпадает по длине с заголовками" }
    }
}

/**
 * Готовит ответ-файл [filename] (без расширения) в формате [format] с таблицей [table]:
 * выставляет заголовки ответа и возвращает содержимое файла.
 */
fun RoutingCall.exportFile(
    filename: String,
    format: ExportFormat,
    table: ExportTable,
): ByteArray {
    val (contentType, bytes) =
        when (format) {
            ExportFormat.CSV -> "text/csv; charset=UTF-8" to table.csv()
            ExportFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to table.xlsx()
        }
    response.headers.append(HttpHeaders.ContentType, contentType)
    response.headers.append(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "$filename.${format.extension}").toString(),
    )
    return bytes
}

/** CSV в UTF-8 с BOM: текст, который табличный редактор принял бы за формулу, — с апострофом. */
internal fun ExportTable.csv(): ByteArray {
    val text = StringBuilder()
    CSVPrinter(text, CSVFormat.DEFAULT.builder().setHeader(*headers.toTypedArray()).get()).use { csv ->
        rows.forEach { row -> csv.printRecord(row.map { it.csv() }) }
    }
    return ("﻿" + text).toByteArray(Charsets.UTF_8)
}

/** Значение ячейки для CSV. */
private fun ExportCell.csv(): String =
    when (this) {
        is ExportCell.Text -> if (value.trimStart().firstOrNull() in FORMULA_STARTS || value.firstOrNull() in CONTROL_STARTS) "'$value" else value
        is ExportCell.Number -> value.toString()
        is ExportCell.Percent -> BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        is ExportCell.Amount -> value.decimal().toPlainString()
        is ExportCell.Day -> value.toString()
        ExportCell.Empty -> ""
    }

/** Книга Excel с одним листом: заголовок жирный, суммы, проценты и даты — числовые ячейки с форматом. */
internal fun ExportTable.xlsx(): ByteArray {
    val out = ByteArrayOutputStream()
    val workbook = Workbook(out, "Athletica", "1.0")
    val sheet = workbook.newWorksheet("Data")
    headers.forEachIndexed { column, header ->
        sheet.value(0, column, header)
        sheet.style(0, column).bold().set()
    }
    rows.forEachIndexed { index, row -> row.forEachIndexed { column, cell -> sheet.write(index + 1, column, cell) } }
    headers.indices.forEach { column ->
        val width = (listOf(headers[column].length) + rows.map { it[column].width() }).max()
        sheet.width(column, (width + 2).coerceIn(8, 60).toDouble())
    }
    workbook.finish()
    return out.toByteArray()
}

/** Записывает ячейку [cell] в строку [row] и колонку [column] листа. */
private fun Worksheet.write(
    row: Int,
    column: Int,
    cell: ExportCell,
) {
    when (cell) {
        is ExportCell.Text -> value(row, column, cell.value)
        is ExportCell.Number -> value(row, column, cell.value)
        is ExportCell.Percent -> {
            value(row, column, cell.value)
            style(row, column).format("0.0%").set()
        }
        is ExportCell.Amount -> {
            value(row, column, cell.value.decimal())
            style(row, column).format(cell.value.excelFormat()).set()
        }
        is ExportCell.Day -> {
            value(row, column, cell.value.toJavaLocalDate())
            style(row, column).format("yyyy-mm-dd").set()
        }
        ExportCell.Empty -> Unit
    }
}

/** Примерная ширина ячейки в символах для подбора ширины колонки. */
private fun ExportCell.width(): Int =
    when (this) {
        is ExportCell.Text -> value.length
        is ExportCell.Amount -> value.decimal().toPlainString().length + 4
        ExportCell.Empty -> 0
        else -> csv().length
    }

/** Сумма в основных единицах валюты. */
private fun Money.decimal(): BigDecimal = BigDecimal.valueOf(minorUnits, currency.fractionDigits)

/** Числовой формат Excel суммы: разряды, дробная часть валюты и её символ. */
private fun Money.excelFormat(): String {
    val fraction = if (currency.fractionDigits > 0) "." + "0".repeat(currency.fractionDigits) else ""
    return "#,##0$fraction\\ \"${currency.symbol}\""
}

/** Первые символы, с которых табличный редактор начинает формулу. */
private val FORMULA_STARTS = listOf('=', '+', '-', '@')

/** Управляющие символы в начале значения, которые тоже ведут к формуле. */
private val CONTROL_STARTS = listOf('\t', '\r', '\n')
