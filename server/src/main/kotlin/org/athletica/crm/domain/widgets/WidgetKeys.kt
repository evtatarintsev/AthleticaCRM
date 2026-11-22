package org.athletica.crm.domain.widgets

import java.security.SecureRandom
import java.util.Base64

private val random = SecureRandom()

/** Новый публичный ключ виджета: 32 случайных байта в base64url без выравнивания (43 символа). */
internal fun newWidgetKey(): String = ByteArray(32).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
