package org.athletica.crm.usecases.clients.import

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser

/**
 * Низкоуровневая обёртка над commons-csv: определяет кодировку и разделитель,
 * читает байты CSV и возвращает заголовки + список строк (значения по позициям).
 */
object CsvReader {
    private val SUPPORTED_DELIMITERS = listOf(';', ',', '\t')
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** Результат разбора CSV: список заголовков и список строк (значения позиционно). */
    data class Parsed(
        val headers: List<String>,
        val rows: List<List<String?>>,
    )

    /**
     * Парсит [bytes] как CSV.
     * Определяет кодировку (UTF-8 c BOM → UTF-8 → Windows-1251 fallback) и разделитель
     * (по первой строке: тот, что даёт больше колонок).
     * Возвращает заголовки в порядке появления и список строк-данных.
     */
    fun parse(bytes: ByteArray): Parsed {
        val text = decode(bytes)
        val delimiter = detectDelimiter(text)
        val format: CSVFormat =
            CSVFormat.Builder
                .create()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(false)
                .build()

        val parser: CSVParser = CSVParser(java.io.StringReader(text), format)
        parser.use { p ->
            val headers: List<String> = p.headerNames
            val rows: List<List<String?>> =
                p.records.map { record ->
                    headers.indices.map { i ->
                        if (i < record.size()) {
                            val v: String = record.get(i)
                            if (v.isEmpty()) null else v
                        } else {
                            null
                        }
                    }
                }
            return Parsed(headers, rows)
        }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == UTF8_BOM[0] &&
            bytes[1] == UTF8_BOM[1] &&
            bytes[2] == UTF8_BOM[2]
        ) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        return tryDecode(bytes, Charsets.UTF_8)
            ?: String(bytes, charset("Windows-1251"))
    }

    private fun tryDecode(bytes: ByteArray, charset: java.nio.charset.Charset): String? =
        try {
            val decoder =
                charset.newDecoder().apply {
                    onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                }
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            null
        }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val best = SUPPORTED_DELIMITERS.maxBy { delim -> firstLine.count { ch -> ch == delim } }
        val count = firstLine.count { ch -> ch == best }
        return if (count > 0) best else ','
    }
}
