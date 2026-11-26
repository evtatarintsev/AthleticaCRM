package org.athletica.crm.domain.clientapp

/** Настройки кабинета клиента из `clientApp` в `application.conf`. */
data class ClientAppConfig(
    /** Адрес кабинета для ссылок в приглашениях (`CABINET_PUBLIC_URL`). */
    val publicUrl: String,
    /** Текущая версия условий использования и политики обработки ПД; смена требует принять их заново. */
    val termsVersion: String,
    /** Ставить ли cookie кабинета с `Secure`: выключается только в режиме разработки по HTTP. */
    val secureCookies: Boolean,
)
