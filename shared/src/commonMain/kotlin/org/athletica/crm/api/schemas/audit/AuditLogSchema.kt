package org.athletica.crm.api.schemas.audit

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.UserId
import kotlin.uuid.Uuid

/** Параметры запроса лога аудита с пагинацией и фильтрами. */
@Serializable
data class AuditLogListRequest(
    val page: Int = 0,
    val pageSize: Int = 50,
    val actionType: String? = null,
    val userId: UserId? = null,
    val entityType: String? = null,
    val from: String? = null,
    val to: String? = null,
)

/** Одна запись в логе аудита. */
@Serializable
data class AuditLogItem(
    val id: Uuid,
    val userId: Uuid?,
    val username: String,
    val actionType: String,
    val description: String,
    val entityType: String?,
    val entityId: Uuid?,
    val subject: AuditLogSubject?,
    val data: String?,
    val ipAddress: String?,
    val createdAt: String,
)

/** Цель записи аудита для отображения и перехода в web-клиенте. */
@Serializable
data class AuditLogSubject(
    val type: String,
    val id: Uuid,
    val title: String,
    val isAvailable: Boolean,
    /** Идентификатор связанной цели для маршрутов, отличных от самой сущности. */
    val targetId: Uuid? = null,
)

/** Участник, встречающийся в журнале текущей организации. */
@Serializable
data class AuditLogUser(
    val id: UserId,
    val username: String,
)

/** Список авторов событий аудита текущей организации. */
@Serializable
data class AuditLogUsersResponse(
    val items: List<AuditLogUser>,
)

/** Ответ на запрос лога аудита с пагинацией. */
@Serializable
data class AuditLogListResponse(
    val items: List<AuditLogItem>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)
