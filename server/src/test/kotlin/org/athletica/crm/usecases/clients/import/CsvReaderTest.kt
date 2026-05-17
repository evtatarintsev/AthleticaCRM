package org.athletica.crm.usecases.clients.import

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvReaderTest {
    @Test
    fun `парсит CSV с разделителем точка-с-запятой`() {
        val csv =
            """
            ФИО;Пол
            Иванов;Мужской
            Петрова;Женский
            """.trimIndent()
        val parsed = CsvReader.parse(csv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("ФИО", "Пол"), parsed.headers)
        assertEquals(listOf(listOf("Иванов", "Мужской"), listOf("Петрова", "Женский")), parsed.rows)
    }

    @Test
    fun `парсит CSV с разделителем запятая`() {
        val csv = "Name,Gender\nJohn,Male\nJane,Female\n"
        val parsed = CsvReader.parse(csv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("Name", "Gender"), parsed.headers)
        assertEquals(2, parsed.rows.size)
    }

    @Test
    fun `парсит UTF-8 с BOM`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = "ФИО;Пол\nИванов;Мужской\n".toByteArray(Charsets.UTF_8)
        val parsed = CsvReader.parse(bom + csv)
        assertEquals(listOf("ФИО", "Пол"), parsed.headers)
        assertEquals(listOf("Иванов", "Мужской"), parsed.rows[0])
    }

    @Test
    fun `парсит Windows-1251 для русского текста`() {
        val csv = "ФИО;Пол\nИванов;Мужской\n"
        val bytes = csv.toByteArray(charset("Windows-1251"))
        val parsed = CsvReader.parse(bytes)
        assertEquals(listOf("ФИО", "Пол"), parsed.headers)
        assertEquals(listOf("Иванов", "Мужской"), parsed.rows[0])
    }

    @Test
    fun `пустые ячейки превращаются в null`() {
        val csv = "ФИО;Дата\nИванов;\nПетров;15.11.2019\n"
        val parsed = CsvReader.parse(csv.toByteArray(Charsets.UTF_8))
        assertEquals(null, parsed.rows[0][1])
        assertEquals("15.11.2019", parsed.rows[1][1])
    }
}
