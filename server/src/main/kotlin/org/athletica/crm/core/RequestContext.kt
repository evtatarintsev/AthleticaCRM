package org.athletica.crm.core

import kotlin.uuid.Uuid

/**
 * Типобезопасный идентификатор пользователя.
 * Оборачивает [Uuid], исключая случайную подмену на [OrgId] или raw UUID.
 */
@JvmInline
value class UserId(val value: Uuid) {
    companion object {
        /** Генерирует новый UUIDv7 (монотонно возрастающий, подходит для PK). */
        fun new() = UserId(Uuid.generateV7())
    }
}

/** Конвертирует raw [Uuid] в типизированный [UserId]. */
fun Uuid.toUserId(): UserId = UserId(this)

/**
 * Типобезопасный идентификатор организации.
 * Оборачивает [Uuid], исключая случайную подмену на [UserId] или raw UUID.
 */
@JvmInline
value class OrgId(val value: Uuid) {
    companion object {
        /** Генерирует новый UUIDv7 (монотонно возрастающий, подходит для PK). */
        fun new() = OrgId(Uuid.generateV7())
    }
}

/** Конвертирует raw [Uuid] в типизированный [OrgId]. */
fun Uuid.toOrgId(): OrgId = OrgId(this)

/**
 * Контекст аутентифицированного HTTP-запроса.
 *
 * [lang] — язык из заголовка `Accept-Language`.
 * [userId] — идентификатор пользователя из JWT-токена.
 * [orgId] — идентификатор организации из JWT-токена.
 * [username] — имя пользователя из JWT-токена (денормализовано).
 * [clientIp] — IP-адрес клиента; IPv4 или IPv6; null если определить не удалось.
 */
data class RequestContext(
    val lang: Lang,
    val userId: UserId,
    val orgId: OrgId,
    val username: String,
    val clientIp: String?,
)
