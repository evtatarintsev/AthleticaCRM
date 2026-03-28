package org.athletica.crm.security

import org.athletica.crm.core.PasswordHash
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Конфигурация хеширования паролей Argon2id.
 *
 * Параметры по умолчанию соответствуют минимальным рекомендациям OWASP 2024:
 * - memory  ≥ 19 MiB
 * - iterations ≥ 2
 *
 * [memory] — потребление памяти в КБ (default: 19 MiB = 19 456 КБ),
 * [iterations] — количество итераций, [parallelism] — степень параллелизма,
 * [saltLength] — длина соли в байтах (128 бит), [hashLength] — длина хеша в байтах (256 бит).
 */
data class PasswordConfig(
    val memory: Int = 19_456,
    val iterations: Int = 2,
    val parallelism: Int = 1,
    val saltLength: Int = 16,
    val hashLength: Int = 32,
)

/**
 * Сервис хеширования паролей на основе Argon2id.
 *
 * Argon2id — победитель Password Hashing Competition 2015,
 * первый выбор по рекомендациям OWASP 2024.
 * Устойчив к GPU-атакам (memory-hard) и side-channel-атакам (hybrid variant).
 *
 * Принимает [config] — параметры алгоритма.
 */
class PasswordHasher(private val config: PasswordConfig = PasswordConfig()) {
    /**
     * Генерирует хеш пароля со случайной солью.
     * Каждый вызов возвращает разный хеш — это нормально и ожидаемо.
     *
     * Принимает [password] — сырой пароль.
     * Возвращает хеш в стандартном формате PHC:
     * `$argon2id$v=19$m=19456,t=2,p=1$<salt_base64>$<hash_base64>`
     */
    fun hash(password: String): PasswordHash {
        val salt = ByteArray(config.saltLength).also { SecureRandom().nextBytes(it) }
        val hash = compute(password, salt, config.memory, config.iterations, config.parallelism, config.hashLength)

        val enc = Base64.getEncoder().withoutPadding()
        val phc =
            "\$argon2id\$v=19" +
                "\$m=${config.memory},t=${config.iterations},p=${config.parallelism}" +
                "\$${enc.encodeToString(salt)}" +
                "\$${enc.encodeToString(hash)}"

        return PasswordHash(phc)
    }

    /**
     * Проверяет, соответствует ли пароль сохранённому хешу.
     *
     * Использует сравнение за постоянное время ([MessageDigest.isEqual])
     * для защиты от timing-атак.
     *
     * Принимает [password] — сырой пароль и [storedHash] — хеш, ранее полученный из [hash].
     * Возвращает `true` если пароль совпадает.
     */
    fun verify(
        password: String,
        storedHash: PasswordHash,
    ): Boolean =
        runCatching {
            // PHC: $argon2id$v=19$m=...,t=...,p=...$<salt_b64>$<hash_b64>
            val parts = storedHash.value.split("$").filter { it.isNotEmpty() }
            require(parts.size == 5 && parts[0] == "argon2id")

            val params =
                parts[2].split(",").associate { segment ->
                    val (k, v) = segment.split("=")
                    k to v.toInt()
                }

            val dec = Base64.getDecoder()
            val salt = dec.decode(parts[3])
            val expected = dec.decode(parts[4])

            val actual =
                compute(
                    password = password,
                    salt = salt,
                    memory = params.getValue("m"),
                    iterations = params.getValue("t"),
                    parallelism = params.getValue("p"),
                    hashLength = expected.size,
                )

            MessageDigest.isEqual(actual, expected)
        }.getOrDefault(false)

    private fun compute(
        password: String,
        salt: ByteArray,
        memory: Int,
        iterations: Int,
        parallelism: Int,
        hashLength: Int,
    ): ByteArray {
        val params =
            Argon2Parameters
                .Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()

        return ByteArray(hashLength).also { out ->
            Argon2BytesGenerator().apply { init(params) }.generateBytes(password.toCharArray(), out)
        }
    }
}
