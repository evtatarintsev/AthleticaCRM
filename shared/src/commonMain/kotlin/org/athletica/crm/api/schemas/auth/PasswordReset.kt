package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.Serializable

/** Запрос письма со ссылкой сброса пароля; ответ одинаков для любого адреса. */
@Serializable
data class ForgotPasswordRequest(
    /** Email (логин), на который отправить ссылку. */
    val email: String,
)

/** Установка нового пароля по ссылке из письма сброса. */
@Serializable
data class ResetPasswordRequest(
    /** Одноразовый токен из ссылки письма. */
    val token: String,
    /** Новый пароль. */
    val newPassword: String,
)
