package org.athletica.crm.core

import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId

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
