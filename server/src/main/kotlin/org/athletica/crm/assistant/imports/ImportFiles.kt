package org.athletica.crm.assistant.imports

import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import org.athletica.crm.assistant.assistantError
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.read.clients.import.CsvReader
import org.dhatim.fastexcel.reader.Cell
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import java.io.IOException

/** Тип вложения, определённый по содержимому файла. */
enum class AttachmentKind { XLSX, CSV, IMAGE }

/** Загруженный файл сообщения: имя для ленты, содержимое и определённый тип. */
class Attachment(
    val name: String,
    val bytes: ByteArray,
    val kind: AttachmentKind,
    val mimeType: String,
)

/** Проверка набора вложений и детерминированное чтение таблиц. */
object ImportFiles {
    /** Предел размера одного файла. */
    const val MAX_BYTES = 10L * 1024 * 1024

    /** Предел числа фото в одном сообщении. */
    const val MAX_IMAGES = 10

    /** Предел строк данных в одном импорте. */
    const val MAX_ROWS = 1000

    /** Запас строк на заголовок и название таблицы над ним. */
    private const val HEADER_ROWS = 10

    /** Определяет тип [bytes] по сигнатуре, а не по имени; `null` — формат не поддерживается. */
    fun detect(bytes: ByteArray): Pair<AttachmentKind, String>? =
        when {
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> AttachmentKind.IMAGE to "image/jpeg"
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> AttachmentKind.IMAGE to "image/png"
            bytes.size >= 12 && bytes.startsWith(0x52, 0x49, 0x46, 0x46) && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> AttachmentKind.IMAGE to "image/webp"
            bytes.startsWith(0x50, 0x4B, 0x03, 0x04) -> if (isXlsx(bytes)) AttachmentKind.XLSX to XLSX_MIME else null
            bytes.startsWith(0xD0, 0xCF, 0x11, 0xE0) || bytes.startsWith(0x25, 0x50, 0x44, 0x46) -> null
            bytes.isNotEmpty() && bytes.take(8192).none { it == 0.toByte() } -> AttachmentKind.CSV to "text/csv"
            else -> null
        }

    /** Допустимы одна таблица или от одного до [MAX_IMAGES] изображений одного сообщения. */
    context(raise: Raise<AppError>)
    fun checkSet(kinds: List<AttachmentKind>) {
        val images = kinds.count { it == AttachmentKind.IMAGE }
        if (images in 1 until kinds.size || (images == 0 && kinds.size > 1)) {
            raise(assistantError("IMPORT_ATTACHMENT_SET"))
        }
        if (images > MAX_IMAGES) {
            raise(assistantError("IMPORT_TOO_MANY_PHOTOS"))
        }
    }

    /** Строки таблицы [attachment]: все непустые листы xlsx или строки csv. */
    context(raise: Raise<AppError>)
    fun table(attachment: Attachment): ImportSource {
        val sheets =
            when (attachment.kind) {
                AttachmentKind.XLSX -> xlsx(attachment.bytes)
                AttachmentKind.CSV -> csv(attachment.bytes)
                AttachmentKind.IMAGE -> raise(assistantError("IMPORT_UNSUPPORTED_FORMAT"))
            }.filter { it.rows.isNotEmpty() }
        if (sheets.isEmpty()) {
            raise(assistantError("IMPORT_FILE_INVALID"))
        }
        return ImportSource(sheets).also { checkRows(it) }
    }

    /**
     * Лист заведомо длиннее [MAX_ROWS] строк данных отклоняется до создания черновика; запас — на
     * заголовок и строки над ним. Точный предел после выбора заголовка проверяет [ImportRows.parse].
     */
    context(raise: Raise<AppError>)
    fun checkRows(source: ImportSource) {
        if (source.sheets.any { it.rows.size > MAX_ROWS + HEADER_ROWS }) {
            raise(assistantError("IMPORT_TOO_MANY_ROWS"))
        }
    }

    /** Ячейки csv читаются существующим [CsvReader]; строка заголовка остаётся обычной строкой. */
    context(raise: Raise<AppError>)
    private fun csv(bytes: ByteArray): List<SourceSheet> {
        val records = either { CsvReader.records(bytes) }.fold({ raise(assistantError("IMPORT_FILE_INVALID")) }, { it })
        val rows =
            records
                .mapIndexed { index, cells -> SourceRow(index + 1, index + 1, null, cells.map { SourceCell(it?.takeIf(String::isNotBlank)) }) }
                .filter { row -> row.cells.any { it.value != null } }
        return listOf(SourceSheet("", rows))
    }

    /** Даты — календарные ISO-даты, числа — без экспоненты, чтобы телефон не стал `8.9E10`. */
    context(raise: Raise<AppError>)
    private fun xlsx(bytes: ByteArray): List<SourceSheet> =
        try {
            ReadableWorkbook(bytes.inputStream(), ReadingOptions(true, false)).use { workbook ->
                workbook.sheets.toList().map { sheet ->
                    val rows =
                        sheet.openStream().use { stream ->
                            stream.toList().mapNotNull { row ->
                                val cells = (0 until row.cellCount).map { SourceCell(row.getCell(it)?.let(::text)) }
                                if (cells.any { it.value != null }) SourceRow(row.rowNum, row.rowNum, null, cells.dropLastWhile { it.value == null }) else null
                            }
                        }
                    SourceSheet(sheet.name, rows)
                }
            }
        } catch (_: IOException) {
            raise(assistantError("IMPORT_FILE_INVALID"))
        } catch (_: IllegalStateException) {
            raise(assistantError("IMPORT_FILE_INVALID"))
        }

    /** Текст ячейки xlsx в виде, пригодном для разбора: пустая ячейка — `null`. */
    private fun text(cell: Cell): String? =
        when (cell.type) {
            CellType.NUMBER ->
                if (isDate(cell)) {
                    cell.asDate().toLocalDate().toString()
                } else {
                    cell.asNumber().stripTrailingZeros().toPlainString()
                }
            CellType.EMPTY, CellType.ERROR -> null
            else -> cell.text
        }?.trim()?.takeIf(String::isNotEmpty)

    /** Встроенные форматы дат Excel и пользовательские форматы с днём, месяцем или годом. */
    private fun isDate(cell: Cell): Boolean {
        val id = cell.dataFormatId ?: return false
        if (id in 14..22 || id in 45..47) {
            return true
        }
        val format = cell.dataFormatString?.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "") ?: return false
        return format.any { it in "dDyY" } || (format.contains('m', ignoreCase = true) && !format.contains('0') && !format.contains('#'))
    }

    /** xlsx — zip с книгой Excel внутри; имена записей zip хранятся несжатыми. Прочие zip (docx и т.п.) не поддерживаются. */
    private fun isXlsx(bytes: ByteArray): Boolean = String(bytes, Charsets.ISO_8859_1).contains("xl/workbook.xml")

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
