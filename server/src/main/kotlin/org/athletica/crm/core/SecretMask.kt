package org.athletica.crm.core

/** Видимая часть маски вместо скрытых символов. */
private const val MASK = "••••"

/** Сколько последних символов секрета видно в маске. */
private const val VISIBLE_TAIL = 4

/** Минимальная длина секрета, у которого показываются последние символы. */
private const val MIN_MASKED_LENGTH = 12

/**
 * Маска секрета: `••••` и последние 4 символа; секрет короче 12 символов маскируется
 * полностью, чтобы не раскрыть заметную его часть.
 */
fun String.secretMask(): String =
    if (length >= MIN_MASKED_LENGTH) {
        MASK + takeLast(VISIBLE_TAIL)
    } else {
        MASK
    }
