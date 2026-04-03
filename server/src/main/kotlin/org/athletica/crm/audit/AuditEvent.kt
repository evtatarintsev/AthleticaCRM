package org.athletica.crm.audit

import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UserId
import kotlin.uuid.Uuid

/** Типы действий, которые логируются в системе аудита. */
enum class AuditActionType(val code: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    AUTH_LOGIN("auth_login"),
    AUTH_LOGOUT("auth_logout"),
    AUTH_SIGNUP("auth_signup"),
    MESSAGE_SEND("message_send"),
    EXPORT("export"),
    IMPORT("import"),
}

/**
 * Событие аудита для отправки в [AuditService].
 *
 * [orgId] — организация, в контексте которой произошло действие.
 * [userId] — пользователь, совершивший действие; null для системных действий.
 * [username] — имя пользователя на момент события (денормализовано, не меняется при удалении пользователя).
 * [actionType] — тип действия.
 * [entityType] — тип сущности, над которой совершено действие (например, "client", "group", "sport").
 * [entityId] — идентификатор сущности.
 * [data] — дополнительные данные в формате JSON-строки, специфичные для типа события.
 * [ipAddress] — IP-адрес клиента, IPv4 или IPv6.
 */
data class AuditEvent(
    val orgId: OrgId,
    val userId: UserId?,
    val username: String,
    val actionType: AuditActionType,
    val entityType: String? = null,
    val entityId: Uuid? = null,
    val data: String? = null,
    val ipAddress: String? = null,
)
