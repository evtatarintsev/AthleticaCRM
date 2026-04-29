package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.branches.BranchDetailResponse

/** Запрос на получение списка доступных филиалов. Используется перед выбором филиала для авторизации. */
@Serializable
data class AuthBranchesRequest(
    /** Имя пользователя. */
    val username: String,
    /** Пароль пользователя. */
    val password: String,
)

/** Ответ со списком филиалов, в которые данный пользователь может авторизоваться. */
@Serializable
data class AuthBranchesResponse(
    /** Список доступных филиалов. */
    val branches: List<BranchDetailResponse>,
)
