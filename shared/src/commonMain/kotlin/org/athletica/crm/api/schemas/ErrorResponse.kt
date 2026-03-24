package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

/** Ошибка конкретного поля формы. */
@Serializable
data class FieldError(
    /** Название поля. */
    val name: String,
    /** Человекочитаемое описание ошибки поля. */
    val error: String,
)

/** Стандартный ответ сервера при ошибке. */
@Serializable
data class ErrorResponse(
    /** Машиночитаемый код ошибки в формате SCREAMING_SNAKE_CASE. */
    val code: String,
    /** Человекочитаемое описание ошибки. */
    val message: String,
    /** Ошибки конкретных полей, заполняется при ошибках валидации. */
    val fields: List<FieldError>? = null,
)
