package org.athletica.crm

// На вебе сессия управляется через httpOnly cookie, которую выставляет сервер через Set-Cookie.
// Браузер отправляет её автоматически с каждым запросом — читать или писать её из JS нельзя.
// Проверка авторизации делается запросом к API: если сервер вернул 200 — сессия валидна.
class CookieAccessTokenStorage : AccessTokenStorage {
    override fun get(): String? = null // cookie недоступна из JS (httpOnly)
    override fun save(token: String) = Unit // Set-Cookie выставляет сервер
    override fun clear() = Unit // сервер сбрасывает cookie при логауте
}

actual fun getAccessTokenStorage(): AccessTokenStorage = CookieAccessTokenStorage()
