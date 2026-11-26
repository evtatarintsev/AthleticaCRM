package org.athletica.crm.domain.clientapp.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.auth.oneTimeTokenHash
import org.athletica.crm.domain.clientapp.codes.CodeChannel
import org.athletica.crm.domain.clientapp.codes.VerificationCode
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Одноразовые коды входа в кабинет. Строка кода пишется на каждый выпуск, для любого номера:
 * по строкам считаются лимиты частоты, поэтому посторонний номер ограничен так же, как приглашённый.
 */
interface ClientAppCodes {
    /** Выпускает код на [phone] в момент [now], если лимиты частоты позволяют. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun issue(phone: PhoneDigits, now: Instant): CodeIssue

    /**
     * Проверяет [code] против последнего кода номера [phone] в момент [now]. Неверный код тратит
     * попытку, верный гасит код только при [consumeAccepted]: транзакцию надо зафиксировать при любом исходе.
     * До принятия условий верный код проверяется без погашения.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun check(
        phone: PhoneDigits,
        code: VerificationCode,
        now: Instant,
        consumeAccepted: Boolean = true,
    ): CodeCheck

    /** Запоминает, что код [id] доставлен способом [channel]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun delivered(id: Uuid, channel: CodeChannel)
}

/** Исход выпуска кода. */
sealed interface CodeIssue {
    /** Через сколько можно запросить следующий код. */
    val retryAfter: Duration

    /** Код [code] выпущен, его строка — [id]. */
    data class Issued(val id: Uuid, val code: VerificationCode, override val retryAfter: Duration) : CodeIssue

    /** Лимит частоты: код не выпущен. */
    data class RateLimited(override val retryAfter: Duration) : CodeIssue
}

/** Исход проверки кода. */
enum class CodeCheck {
    /** Код верный; погашен, если проверка запрашивала погашение. */
    ACCEPTED,

    /** Код неверный, попытка засчитана. */
    WRONG,

    /** Кода нет, он истёк, погашен или попытки кончились — нужен новый код. */
    EXPIRED,
}

/** Хэш кода [code] строки [id], как он хранится в `client_app_codes.code_hash`: SHA-256 от `id` и кода подряд. */
fun codeHash(id: Uuid, code: VerificationCode): String = oneTimeTokenHash("$id${code.value}")

/**
 * Момент, с которого номеру можно выпустить следующий код, по моментам выпуска [issuedDesc]
 * за последние сутки (от нового к старому): 60 с после последнего, не больше 5 в час и 10 в сутки.
 * `null` — ограничений нет.
 */
fun nextCodeAllowedAt(issuedDesc: List<Instant>): Instant? =
    listOfNotNull(
        issuedDesc.firstOrNull()?.plus(CODE_INTERVAL),
        issuedDesc.getOrNull(CODES_PER_HOUR - 1)?.plus(1.hours),
        issuedDesc.getOrNull(CODES_PER_DAY - 1)?.plus(1.days),
    ).maxOrNull()

/** Реализация [ClientAppCodes] поверх таблицы `client_app_codes`. */
class DbClientAppCodes : ClientAppCodes {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun issue(phone: PhoneDigits, now: Instant): CodeIssue {
        tr.sql("SELECT pg_advisory_xact_lock(hashtext(:phone))").bind("phone", phone.value).firstOrNull { true }
        val issued = issuedSince(phone, now - 1.days)
        val allowedAt = nextCodeAllowedAt(issued)
        if (allowedAt != null && allowedAt > now) {
            return CodeIssue.RateLimited(allowedAt - now)
        }
        val id = Uuid.generateV7()
        val code = VerificationCode.random()
        tr.sql(
            """
            INSERT INTO client_app_codes (id, phone_digits, code_hash, created_at, expires_at)
            VALUES (:id, :phone, :hash, :now, :expiresAt)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("phone", phone.value)
            .bind("hash", codeHash(id, code))
            .bind("now", now)
            .bind("expiresAt", now + CODE_TTL)
            .execute()
        val retryAt = checkNotNull(nextCodeAllowedAt(listOf(now) + issued))
        return CodeIssue.Issued(id, code, retryAt - now)
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun check(
        phone: PhoneDigits,
        code: VerificationCode,
        now: Instant,
        consumeAccepted: Boolean,
    ): CodeCheck {
        val latest =
            tr.sql(
                """
                SELECT id, code_hash, expires_at, attempts, used_at FROM client_app_codes
                WHERE phone_digits = :phone ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE
                """.trimIndent(),
            )
                .bind("phone", phone.value)
                .firstOrNull { row -> LatestCode(row.asUuid("id"), row.asString("code_hash"), row.asInstant("expires_at"), row.asInt("attempts"), row.asInstantOrNull("used_at")) }
        if (latest == null || latest.usedAt != null || latest.expiresAt <= now || latest.attempts >= MAX_ATTEMPTS) {
            return CodeCheck.EXPIRED
        }
        if (latest.hash != codeHash(latest.id, code)) {
            tr.sql("UPDATE client_app_codes SET attempts = attempts + 1 WHERE id = :id").bind("id", latest.id).execute()
            return CodeCheck.WRONG
        }
        if (consumeAccepted) {
            tr.sql("UPDATE client_app_codes SET used_at = :now WHERE id = :id").bind("id", latest.id).bind("now", now).execute()
        }
        return CodeCheck.ACCEPTED
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun delivered(id: Uuid, channel: CodeChannel) {
        tr.sql("UPDATE client_app_codes SET channel = :channel WHERE id = :id").bind("id", id).bind("channel", channel.name).execute()
    }

    /** Моменты выпуска кодов номера [phone] после [since], от нового к старому. */
    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun issuedSince(phone: PhoneDigits, since: Instant): List<Instant> =
        tr.sql(
            """
            SELECT created_at FROM client_app_codes
            WHERE phone_digits = :phone AND created_at > :since
            ORDER BY created_at DESC LIMIT $CODES_PER_DAY
            """.trimIndent(),
        )
            .bind("phone", phone.value)
            .bind("since", since)
            .list { it.asInstant("created_at") }

    /** Последний код номера. */
    private data class LatestCode(
        /** Идентификатор строки кода. */
        val id: Uuid,
        /** Хэш кода ([codeHash]). */
        val hash: String,
        /** Момент, после которого код не действует. */
        val expiresAt: Instant,
        /** Потраченные неверные попытки. */
        val attempts: Int,
        /** Момент входа по коду; `null` — код не использован. */
        val usedAt: Instant?,
    )
}

/** Срок действия кода. */
val CODE_TTL = 5.minutes

/** Минимальный промежуток между кодами на номер. */
private val CODE_INTERVAL = 60.seconds

/** Не больше стольких кодов на номер за час. */
private const val CODES_PER_HOUR = 5

/** Не больше стольких кодов на номер за сутки. */
private const val CODES_PER_DAY = 10

/** Столько неверных попыток гасят код. */
private const val MAX_ATTEMPTS = 5
