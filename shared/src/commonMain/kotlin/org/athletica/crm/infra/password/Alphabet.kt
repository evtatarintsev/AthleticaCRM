package org.athletica.crm.infra.password

import kotlin.random.Random

/**
 * Определяет набор символов для генерации пароля.
 */
data class Alphabet(
    /** Включать заглавные латинские буквы (A–Z). */
    val upperCaseChars: Boolean,
    /** Включать строчные латинские буквы (a–z). */
    val lowerCaseChars: Boolean,
    /** Включать цифры (0–9). */
    val numbers: Boolean,
    /** Включать специальные символы (!@#$ и т.п.). */
    val special: Boolean,
)

/**
 * Генерирует случайный пароль длиной [length] символов из символов данного алфавита.
 *
 * Если [seed] передан — генерация детерминирована и воспроизводима (удобно для тестов).
 * Если [seed] не передан — используется [Random.Default].
 *
 * Бросает [IllegalArgumentException], если ни один набор символов не включён.
 */
fun Alphabet.randomPassword(length: Int, seed: Long? = null): String {
    val chars =
        buildString {
            if (upperCaseChars) {
                append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            }
            if (lowerCaseChars) {
                append("abcdefghijklmnopqrstuvwxyz")
            }
            if (numbers) {
                append("0123456789")
            }
            if (special) {
                append("!@#\$%^&*()-_=+[]{}|;:,.<>?")
            }
        }
    require(chars.isNotEmpty()) { "Alphabet must include at least one character set" }
    val random = if (seed != null) Random(seed) else Random.Default
    return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
}
