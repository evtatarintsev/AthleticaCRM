package org.athletica.infra.yookassa

import java.util.Base64

/** Конфигурация интеграции с ЮKassa. */
data class YookassaConfig(
    /** ID магазина в ЮKassa. */
    val shopId: String,
    /** Секретный ключ из личного кабинета ЮKassa; используется для API и проверки подписей webhook. */
    val secretKey: String,
    /** Тестовый режим: true — деньги не списываются, используется тестовый шлюз ЮKassa. */
    val testMode: Boolean,
    /**
     * URL, на который ЮKassa перенаправляет пользователя после завершения оплаты.
     * Обязателен для типа подтверждения `redirect`.
     */
    val returnUrl: String,
    /** Базовый URL API ЮKassa. */
    val endpointUrl: String = "https://api.yookassa.ru/v3",
) {
    /** Заголовок HTTP Basic-аутентификации: Base64("shopId:secretKey"). */
    val basicAuthHeader: String
        get() = "Basic " + Base64.getEncoder().encodeToString("$shopId:$secretKey".toByteArray(Charsets.UTF_8))
}
