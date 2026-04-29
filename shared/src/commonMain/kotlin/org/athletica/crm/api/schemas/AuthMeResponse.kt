package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId

/** Ответ с данными авторизованного пользователя. */
@Serializable
data class AuthMeResponse(
    /** Уникальный идентификатор пользователя в строковом формате UUID. */
    val id: UserId,
    /** Логин. */
    val username: String,
    /** Имя . */
    val name: String,
    /** Аватар пользователя. */
    val avatarId: UploadId? = null,
    /** Информация об организации. */
    val orgInfo: OrgInfo,
    /** Текущий активный филиал. */
    val currentBranch: BranchDetailResponse,
)

@Serializable
data class OrgInfo(
    val name: String,
    /**
     * Баланс организации. null если недоступен пользователю.
     */
    val balance: Double?,
)
