package org.athletica.crm.assistant.imports

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.athletica.crm.core.errors.AppError
import org.dhatim.fastexcel.Workbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Книга xlsx для тестов: лист → строки ячеек; [LocalDate] пишется ячейкой-датой. */
fun xlsx(vararg sheets: Pair<String, List<List<Any?>>>): ByteArray {
    val out = ByteArrayOutputStream()
    Workbook(out, "test", "1.0").use { workbook ->
        sheets.forEach { (name, rows) ->
            val sheet = workbook.newWorksheet(name)
            rows.forEachIndexed { r, cells ->
                cells.forEachIndexed { c, value ->
                    when (value) {
                        is String -> sheet.value(r, c, value)
                        is Number -> sheet.value(r, c, value)
                        is LocalDate -> {
                            sheet.value(r, c, value)
                            sheet.style(r, c).format("dd.mm.yyyy").set()
                        }
                    }
                }
            }
        }
        workbook.finish()
    }
    return out.toByteArray()
}

/** Тип по содержимому, набор вложений и детерминированное чтение таблиц. */
class ImportFilesTest {
    private fun code(result: Either<AppError, Any?>): String = assertIs<Either.Left<AppError>>(result).value.code

    @Test
    fun detectsByContent() {
        assertEquals(AttachmentKind.XLSX, ImportFiles.detect(xlsx("Лист1" to listOf(listOf("ФИО"))))?.first)
        assertEquals(AttachmentKind.CSV, ImportFiles.detect("ФИО;Телефон\nИванов;8909".toByteArray())?.first)
        assertEquals("image/jpeg", ImportFiles.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0))?.second)
        assertEquals("image/png", ImportFiles.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D))?.second)
        assertEquals("image/webp", ImportFiles.detect("RIFF0000WEBPVP8 ".toByteArray())?.second)
        assertNull(ImportFiles.detect(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())), "старый .xls")
        assertNull(ImportFiles.detect("%PDF-1.7\n".toByteArray()), "pdf")
        assertNull(ImportFiles.detect(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0)), "zip без книги Excel")
    }

    @Test
    fun checksAttachmentSet() {
        either { ImportFiles.checkSet(listOf(AttachmentKind.XLSX)) }.getOrNull()!!
        either { ImportFiles.checkSet(List(10) { AttachmentKind.IMAGE }) }.getOrNull()!!
        assertEquals("ASSISTANT_IMPORT_ATTACHMENT_SET", code(either { ImportFiles.checkSet(listOf(AttachmentKind.CSV, AttachmentKind.IMAGE)) }))
        assertEquals("ASSISTANT_IMPORT_ATTACHMENT_SET", code(either { ImportFiles.checkSet(listOf(AttachmentKind.CSV, AttachmentKind.XLSX)) }))
        assertEquals("ASSISTANT_IMPORT_TOO_MANY_PHOTOS", code(either { ImportFiles.checkSet(List(11) { AttachmentKind.IMAGE }) }))
    }

    /** Телефон числом, ячейка-дата, строки над заголовком и второй лист. */
    @Test
    fun readsXlsx() {
        val bytes =
            xlsx(
                "Клиенты" to listOf(listOf("Список клиентов"), listOf(), listOf("ФИО", "Телефон", "Дата рождения"), listOf("Иванов Иван", 89091302211, LocalDate.of(2014, 3, 5))),
                "Пустой" to listOf(),
                "Тренеры" to listOf(listOf("Имя"), listOf("Петров")),
            )
        val source = either { ImportFiles.table(Attachment("a.xlsx", bytes, AttachmentKind.XLSX, "x")) }.getOrNull()!!
        assertEquals(listOf("Клиенты", "Тренеры"), source.sheets.map { it.name })
        val rows = source.sheets.first().rows
        assertEquals(listOf(1, 3, 4), rows.map { it.n })
        assertEquals(listOf("Иванов Иван", "89091302211", "2014-03-05"), rows.last().cells.map { it.value })
    }

    @Test
    fun rejectsTooManyCsvRows() {
        val csv = (listOf("ФИО;Телефон") + List(1500) { "Клиент $it;8909130${it.toString().padStart(4, '0')}" }).joinToString("\n")
        assertEquals("ASSISTANT_IMPORT_TOO_MANY_ROWS", code(either { ImportFiles.table(Attachment("a.csv", csv.toByteArray(), AttachmentKind.CSV, "text/csv")) }))
        val ok = (listOf("Список", "ФИО;Телефон") + List(1000) { "Клиент $it;1" }).joinToString("\n")
        assertEquals("Список", either { ImportFiles.table(Attachment("a.csv", ok.toByteArray(), AttachmentKind.CSV, "text/csv")) }.getOrNull()!!.sheets.single().rows.first().cells.single().value)
    }

    /** Лист xlsx длиннее предела отклоняется при потоковом чтении. */
    @Test
    fun rejectsTooManyXlsxRows() {
        val book = xlsx("Лист1" to listOf(listOf<Any?>("ФИО")) + List(1500) { listOf<Any?>("Клиент $it") })
        assertEquals("ASSISTANT_IMPORT_TOO_MANY_ROWS", code(either { ImportFiles.table(Attachment("a.xlsx", book, AttachmentKind.XLSX, "x")) }))
    }

    /** Повреждённая книга с подписью xlsx — понятная ошибка, а не сбой сервера. */
    @Test
    fun rejectsBrokenXlsx() {
        fun zip(vararg entries: Pair<String, String>): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                entries.forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
            return out.toByteArray()
        }
        val good = xlsx("Лист1" to listOf(listOf("ФИО")))
        listOf(
            zip("[Content_Types].xml" to "<Types/>", "xl/workbook.xml" to "not xml"),
            zip("xl/workbook.xml" to "<workbook/>"),
            good.copyOf(good.size / 2),
        ).forEach { bytes ->
            assertEquals("ASSISTANT_IMPORT_FILE_INVALID", code(either { ImportFiles.table(Attachment("a.xlsx", bytes, AttachmentKind.XLSX, "x")) }))
        }
    }

    /** Модель видит не больше 15 строк из 800 и значения коротких колонок. */
    @Test
    fun sampleIsBounded() {
        val rows = List(800) { SourceRow(it + 1, it + 1, null, listOf(SourceCell("Клиент $it"), SourceCell(if (it % 2 == 0) "М" else "Ж"))) }
        val text = ImportSource(listOf(SourceSheet("", rows))).sample(Uuid.random(), "table")
        val json = Json.parseToJsonElement(text.lines()[2]).jsonObject
        val sheet = json["sheets"]!!.jsonArray.single().jsonObject
        assertEquals(15, sheet["sample"]!!.jsonArray.size)
        assertEquals(listOf("\"М\"", "\"Ж\""), (sheet["shortColumnValues"] as JsonObject)["1"]!!.jsonArray.map { it.toString() })
        assertNull((sheet["shortColumnValues"] as JsonObject)["0"])
        assertEquals(false, text.contains("Клиент 15"))
    }
}
