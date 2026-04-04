package org.athletica.crm.audit

import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
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
 * Событие аудита для отправки в [PostgresAuditLog].
 *
 * [orgId] — организация, в контексте которой произошло действие.
 * [userId] — пользователь, совершивший действие; null для системных действий.
 * [username] — имя пользователя на момент события (денормализовано, не меняется при удалении пользователя).
 * [actionType] — тип действия.
 * [entityType] — тип сущности, над которой совершено действие (например, "client", "group", "discipline").
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

/**
 * Логирует успешный вход пользователя с идентификатором [userId] из организации [orgId].
 * [clientIp] — IP-адрес клиента; null если определить не удалось.
 */
fun AuditLog.logLogin(
    orgId: OrgId,
    userId: UserId,
    username: String,
    clientIp: String?,
) {
    log(
        AuditEvent(
            orgId = orgId,
            userId = userId,
            username = username,
            actionType = AuditActionType.AUTH_LOGIN,
            entityType = null,
            entityId = null,
            ipAddress = clientIp,
        ),
    )
}

/**
 * Логирует регистрацию нового пользователя с идентификатором [userId] в организации [orgId].
 * [clientIp] — IP-адрес клиента; null если определить не удалось.
 */
fun AuditLog.logSignUp(
    orgId: OrgId,
    userId: UserId,
    username: String,
    clientIp: String?,
) {
    log(
        AuditEvent(
            orgId = orgId,
            userId = userId,
            username = username,
            actionType = AuditActionType.AUTH_SIGNUP,
            entityType = null,
            entityId = null,
            ipAddress = clientIp,
        ),
    )
}

/**
 * Логирует создание сущности типа [entityType] с идентификатором [entityId].
 * [data] — JSON-снапшот созданной сущности на момент события.
 * Организация, пользователь и IP берутся из контекста запроса [ctx].
 */
context(ctx: RequestContext)
fun AuditLog.logCreate(entityType: String, entityId: Uuid, data: String) {
    log(
        AuditEvent(
            orgId = ctx.orgId,
            userId = ctx.userId,
            username = ctx.username,
            actionType = AuditActionType.CREATE,
            ipAddress = ctx.clientIp,
            entityType = entityType,
            entityId = entityId,
            data = data,
        ),
    )
}

/**
 * Логирует обновление сущности типа [entityType] с идентификатором [entityId].
 * [data] — JSON-снапшот сущности после изменения.
 * Организация, пользователь и IP берутся из контекста запроса [ctx].
 */
context(ctx: RequestContext)
fun AuditLog.logUpdate(entityType: String, entityId: Uuid, data: String) {
    log(
        AuditEvent(
            orgId = ctx.orgId,
            userId = ctx.userId,
            username = ctx.username,
            actionType = AuditActionType.UPDATE,
            ipAddress = ctx.clientIp,
            entityType = entityType,
            entityId = entityId,
            data = data,
        ),
    )
}

/**
 * Логирует удаление сущности типа [entityType] с идентификатором [entityId].
 * [data] — JSON-снапшот сущности после изменения.
 * Организация, пользователь и IP берутся из контекста запроса [ctx].
 */
context(ctx: RequestContext)
fun AuditLog.logDelete(entityType: String, entityId: Uuid, data: String) {
    log(
        AuditEvent(
            orgId = ctx.orgId,
            userId = ctx.userId,
            username = ctx.username,
            actionType = AuditActionType.DELETE,
            ipAddress = ctx.clientIp,
            entityType = entityType,
            entityId = entityId,
            data = data,
        ),
    )
}
