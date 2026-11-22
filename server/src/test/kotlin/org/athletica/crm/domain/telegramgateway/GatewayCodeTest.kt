package org.athletica.crm.domain.telegramgateway

import arrow.core.Either
import arrow.core.raise.context.either
import org.athletica.crm.core.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты границ кода [GatewayCode] и срока [GatewayCodeTtl]. */
class GatewayCodeTest {
    private fun code(value: String): Either<DomainError, GatewayCode> = either { GatewayCode.from(value) }

    private fun ttl(seconds: Int): Either<DomainError, GatewayCodeTtl> = either { GatewayCodeTtl.from(seconds) }

    @Test
    fun `код из 4 и 8 цифр допустим`() {
        assertEquals("1234", assertIs<Either.Right<GatewayCode>>(code("1234")).value.value)
        assertEquals("12345678", assertIs<Either.Right<GatewayCode>>(code("12345678")).value.value)
    }

    @Test
    fun `код короче 4, длиннее 8 или с нецифровыми символами отклоняется`() {
        listOf("123", "123456789", "12a4", "12 34", "١٢٣٤", "").forEach {
            assertEquals("INVALID_GATEWAY_CODE", assertIs<Either.Left<DomainError>>(code(it)).value.code, it)
        }
    }

    @Test
    fun `код не попадает в строковое представление`() {
        assertEquals("GatewayCode(***)", assertIs<Either.Right<GatewayCode>>(code("482915")).value.toString())
    }

    @Test
    fun `срок на границах 30 и 3600 допустим, за ними — нет`() {
        assertEquals(30, assertIs<Either.Right<GatewayCodeTtl>>(ttl(30)).value.seconds)
        assertEquals(3600, assertIs<Either.Right<GatewayCodeTtl>>(ttl(3600)).value.seconds)
        listOf(29, 3601, 10, 0).forEach {
            assertEquals("INVALID_GATEWAY_CODE_TTL", assertIs<Either.Left<DomainError>>(ttl(it)).value.code, it.toString())
        }
    }
}
