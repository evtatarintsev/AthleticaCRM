package org.athletica.crm.api.schemas.sessions

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/**
 * Параметры запроса списка занятий за период с опциональной фильтрацией по группе.
 * Даты периода обязательны, а его длина ограничена сверху: чтение не материализует
 * занятия, поэтому неограниченный период означал бы только неограниченную выборку.
 */
@Serializable
data class SessionListRequest(
    /** Первый день периода, включительно. */
    val from: LocalDate,
    /** Последний день периода, включительно. */
    val to: LocalDate,
    /** Группа, занятия которой запрошены; `null` — все группы организации. */
    val groupId: GroupId? = null,
)
