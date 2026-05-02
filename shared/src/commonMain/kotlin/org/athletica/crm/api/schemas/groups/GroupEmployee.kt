package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId

/** Компактные данные преподавателя для отображения в списке преподавателей группы. */
@Serializable
data class GroupEmployee(
    /** Уникальный идентификатор преподавателя. */
    val id: EmployeeId,
    /** Имя преподавателя. */
    val name: String,
    /** Идентификатор аватара преподавателя. */
    val avatarId: UploadId? = null,
)
