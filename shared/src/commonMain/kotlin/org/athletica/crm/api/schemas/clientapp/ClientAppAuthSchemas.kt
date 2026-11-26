package org.athletica.crm.api.schemas.clientapp

import kotlinx.serialization.Serializable

/** Запрос кода входа в кабинет на номер [phone] в любом формате; [viaSms] — человек попросил SMS вместо Telegram. */
@Serializable
data class ClientAppRequestCodeRequest(
    val phone: String,
    val viaSms: Boolean = false,
)

/**
 * Ответ на запрос кода — одинаковый для любого номера и без способа доставки.
 * [sent] — код выпущен; `false` — лимит частоты, новый код не выпускался.
 * [retryAfterSeconds] — через сколько секунд можно запросить следующий код.
 * [termsVersion] — текущая версия условий использования: её кабинет передаёт во входе после принятия.
 */
@Serializable
data class ClientAppRequestCodeResponse(
    val sent: Boolean,
    val retryAfterSeconds: Long,
    val termsVersion: String,
)

/**
 * Вход по номеру [phone] и коду [code]. [acceptedTermsVersion] — версия условий, которую человек
 * принял на экране; без неё вход с верным кодом аккаунта, не принявшего текущую версию,
 * отвечает `CLIENT_APP_TERMS_REQUIRED` без погашения кода.
 */
@Serializable
data class ClientAppLoginRequest(
    val phone: String,
    val code: String,
    val acceptedTermsVersion: String? = null,
)
