package org.athletica.crm.routes.export

import kotlinx.datetime.LocalDate
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Общая выгрузка таблиц: правила CSV и типы ячеек xlsx. */
class TableExportTest {
    private val table =
        ExportTable(
            headers = listOf("Показатель", "Сумма", "Доля", "Дата", "Количество"),
            rows =
                listOf(
                    listOf(ExportCell.Text("=HYPERLINK(\"x\")"), ExportCell.Amount(Money(-8_000, Currency.RUB)), ExportCell.Percent(0.125), ExportCell.Day(LocalDate(2026, 11, 1)), ExportCell.Number(-80)),
                    listOf(ExportCell.Text("Йога, утро"), ExportCell.Amount(Money(120_050, Currency.RUB)), ExportCell.Empty, ExportCell.Empty, ExportCell.Number(3)),
                ),
        )

    /** CSV: BOM, кавычки вокруг запятой, апостроф перед формулой, числа без апострофа. */
    @Test
    fun csvEscapesTextButNotNumbers() {
        val bytes = table.csv()
        assertEquals(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3))
        val lines = bytes.toString(Charsets.UTF_8).removePrefix("﻿").lines()
        assertEquals("Показатель,Сумма,Доля,Дата,Количество", lines[0])
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\",-80.00,0.125,2026-11-01,-80", lines[1])
        assertEquals("\"Йога, утро\",1200.50,,,3", lines[2])
    }

    /** xlsx: заголовок — текст, суммы и проценты — числовые ячейки с форматом, дата — дата. */
    @Test
    fun xlsxWritesTypedCells() {
        ReadableWorkbook(table.xlsx().inputStream(), ReadingOptions(true, false)).use { workbook ->
            val rows = workbook.firstSheet.read()
            assertEquals("Показатель", rows[0].getCell(0).asString())
            val first = rows[1]
            assertEquals(CellType.STRING, first.getCell(0).type)
            assertEquals("=HYPERLINK(\"x\")", first.getCell(0).asString())
            assertEquals(CellType.NUMBER, first.getCell(1).type)
            assertEquals(0, BigDecimal("-80.00").compareTo(first.getCell(1).asNumber()))
            assertTrue(first.getCell(1).dataFormatString.contains("₽"), first.getCell(1).dataFormatString)
            assertEquals(CellType.NUMBER, first.getCell(2).type)
            assertEquals("0.0%", first.getCell(2).dataFormatString)
            assertEquals(LocalDate(2026, 11, 1).toString(), first.getCell(3).asDate().toLocalDate().toString())
            assertEquals(CellType.NUMBER, first.getCell(4).type)
        }
    }
}
