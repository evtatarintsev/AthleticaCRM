package org.athletica.crm.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Новый непредсказуемый одноразовый токен для ссылки из письма: 32 случайных байта в base64url без выравнивания. */
internal fun newOneTimeToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

/** SHA-256 токена [token] в шестнадцатеричном виде — так токен хранится в БД. */
internal fun oneTimeTokenHash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Проверяет, что строка имеет формат токена из [newOneTimeToken], до обращения к БД. */
internal fun String.isOneTimeTokenFormat(): Boolean = matches(Regex("[A-Za-z0-9_-]{43}"))
