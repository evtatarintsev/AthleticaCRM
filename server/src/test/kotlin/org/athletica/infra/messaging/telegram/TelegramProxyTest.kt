package org.athletica.infra.messaging.telegram

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты разбора списка прокси [TelegramProxy.parseList]. */
class TelegramProxyTest {
    @Test
    fun `пустая строка — прямой доступ`() {
        assertEquals(emptyList(), assertIs<Either.Right<List<TelegramProxy>>>(TelegramProxy.parseList("  ")).value)
    }

    @Test
    fun `http-прокси без логина`() {
        val proxies = assertIs<Either.Right<List<TelegramProxy>>>(TelegramProxy.parseList("http://proxy.local:3128")).value

        assertEquals(listOf(TelegramProxy(TelegramProxyType.HTTP, "proxy.local", 3128, null)), proxies)
    }

    @Test
    fun `socks5 с логином и паролем и несколько записей`() {
        val proxies =
            assertIs<Either.Right<List<TelegramProxy>>>(
                TelegramProxy.parseList("socks5://user:p%40ss@10.0.0.1:1080, http://h2:8080"),
            ).value

        assertEquals(
            listOf(
                TelegramProxy(TelegramProxyType.SOCKS5, "10.0.0.1", 1080, ProxyCredentials("user", "p@ss")),
                TelegramProxy(TelegramProxyType.HTTP, "h2", 8080, null),
            ),
            proxies,
        )
    }

    @Test
    fun `мусор, неизвестная схема и запись без порта — ошибка без пароля в тексте`() {
        listOf("garbage", "ftp://h:21", "http://host", "socks5://u:secret@:1080").forEach { raw ->
            val error = assertIs<Either.Left<String>>(TelegramProxy.parseList(raw)).value
            assertTrue(error.isNotBlank())
            assertFalse(error.contains("secret"), error)
        }
    }
}
