package org.athletica.crm.domain.clientapp.auth

import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clientapp.codes.VerificationCode
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Тесты кодов входа в кабинет ([DbClientAppCodes]): лимиты частоты, срок действия и попытки. */
class DbClientAppCodesTest {
    private val codes = DbClientAppCodes()
    private val stranger = checkNotNull(PhoneDigits.from("+79990000000"))
    private val t0 = Instant.parse("2026-11-26T10:00:00Z")

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Выпускает код на [phone] в момент [at]. */
    private suspend fun issue(at: Instant, phone: PhoneDigits = stranger): CodeIssue = orThrow { TestPostgres.db.transaction { codes.issue(phone, at) } }

    /** Проверяет код [code] номера [stranger] в момент [at]. */
    private suspend fun check(code: VerificationCode, at: Instant): CodeCheck = orThrow { TestPostgres.db.transaction { codes.check(stranger, code, at) } }

    /** Другой код, чем [code]. */
    private fun otherThan(code: VerificationCode): VerificationCode = orThrow { VerificationCode.from(if (code.value == "000000") "111111" else "000000") }

    @Test
    fun `без выпущенных кодов ограничений нет`() {
        assertNull(nextCodeAllowedAt(emptyList()))
    }

    @Test
    fun `второй код через 20 секунд для постороннего номера — лимит до конца минуты`() =
        runTest {
            val first = assertIs<CodeIssue.Issued>(issue(t0))
            assertEquals(60.seconds, first.retryAfter)

            val second = assertIs<CodeIssue.RateLimited>(issue(t0 + 20.seconds))
            assertEquals(40.seconds, second.retryAfter)
        }

    @Test
    fun `шестой код за час не выпускается`() =
        runTest {
            repeat(5) { assertIs<CodeIssue.Issued>(issue(t0 + (it * 61).seconds)) }

            val sixth = assertIs<CodeIssue.RateLimited>(issue(t0 + (5 * 61).seconds))
            assertEquals(t0 + 1.hours, t0 + (5 * 61).seconds + sixth.retryAfter)
        }

    @Test
    fun `одиннадцатый код за сутки не выпускается и сообщает, когда можно`() =
        runTest {
            repeat(10) { assertIs<CodeIssue.Issued>(issue(t0 + (it * 61).minutes)) }

            val at = t0 + (10 * 61).minutes
            val eleventh = assertIs<CodeIssue.RateLimited>(issue(at))
            assertEquals(t0 + 1.days, at + eleventh.retryAfter)
        }

    @Test
    fun `лимиты считаются по номеру`() =
        runTest {
            issue(t0)

            assertIs<CodeIssue.Issued>(issue(t0 + 1.seconds, checkNotNull(PhoneDigits.from("+79991111111"))))
        }

    @Test
    fun `верный код принимается один раз`() =
        runTest {
            val issued = assertIs<CodeIssue.Issued>(issue(t0))

            assertEquals(CodeCheck.ACCEPTED, check(issued.code, t0 + 1.minutes))
            assertEquals(CodeCheck.EXPIRED, check(issued.code, t0 + 2.minutes))
        }

    @Test
    fun `код через 6 минут истёк`() =
        runTest {
            val issued = assertIs<CodeIssue.Issued>(issue(t0))

            assertEquals(CodeCheck.EXPIRED, check(issued.code, t0 + 6.minutes))
        }

    @Test
    fun `после 5 неверных попыток не принимается даже верный код`() =
        runTest {
            val issued = assertIs<CodeIssue.Issued>(issue(t0))

            repeat(5) { assertEquals(CodeCheck.WRONG, check(otherThan(issued.code), t0 + 1.minutes)) }
            assertEquals(CodeCheck.EXPIRED, check(issued.code, t0 + 1.minutes))
        }

    @Test
    fun `проверяется только последний код номера`() =
        runTest {
            val first = assertIs<CodeIssue.Issued>(issue(t0))
            val second = assertIs<CodeIssue.Issued>(issue(t0 + 2.minutes))

            if (first.code != second.code) {
                assertEquals(CodeCheck.WRONG, check(first.code, t0 + 3.minutes))
            }
            assertEquals(CodeCheck.ACCEPTED, check(second.code, t0 + 3.minutes))
        }

    @Test
    fun `хэш кода — SHA-256 от id строки и кода, как в БД`() =
        runTest {
            val issued = assertIs<CodeIssue.Issued>(issue(t0))
            val sqlHash =
                either<AppError, String?> {
                    TestPostgres.db.sql("SELECT encode(sha256(convert_to(id::text || :code, 'UTF8')), 'hex') AS h FROM client_app_codes WHERE id = :id")
                        .bind("code", issued.code.value)
                        .bind("id", issued.id)
                        .firstOrNull { it.asString("h") }
                }.getOrNull()

            assertEquals(codeHash(issued.id, issued.code), sqlHash)
        }
}
