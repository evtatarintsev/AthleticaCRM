package org.athletica.crm.api.schemas.audit

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Одна запись в логе аудита. */
@Serializable
data class AuditLogItem(
    val id: Uuid,
    val userId: Uuid?,
    val username: String,
    val actionType: String,
    val entityType: String?,
    val entityId: Uuid?,
    val data: String?,
    val ipAddress: String?,
    val createdAt: String,
)

/** Ответ на запрос лога аудита с пагинацией. */
@Serializable
data class AuditLogListResponse(
    val items: List<AuditLogItem>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)
