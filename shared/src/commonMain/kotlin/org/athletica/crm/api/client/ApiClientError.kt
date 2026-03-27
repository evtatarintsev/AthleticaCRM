package org.athletica.crm.api.client

import org.athletica.crm.api.schemas.FieldError

sealed interface ApiClientError {
    /**
     * Сессия истекла или пользователь не авторизован — необходима повторная аутентификация.
     */
    data object Unauthenticated : ApiClientError

    /**
     * Сервис отклонил запрос по бизнес-причине.
     * Например: конфликт данных, ошибки валидации полей.
     * [code] — машиночитаемый код ошибки, [message] — текст для отображения пользователю,
     * [fields] — ошибки отдельных полей при ошибках валидации.
     */
    data class ValidationError(
        val code: String,
        val message: String,
        val fields: List<FieldError>? = null,
    ) : ApiClientError

    /**
     * Сервис недоступен: нет сети, истёк таймаут, не удалось установить соединение и пр.
     */
    data class Unavailable(
        val cause: Throwable,
    ) : ApiClientError
}
