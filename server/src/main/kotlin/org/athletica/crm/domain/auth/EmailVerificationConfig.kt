package org.athletica.crm.domain.auth

import java.net.URI

class EmailVerificationConfig(publicUrl: String) {
    private val base = URI(publicUrl)

    init {
        require(base.host != null && base.rawUserInfo == null && base.rawQuery == null && base.rawFragment == null) {
            "CRM_PUBLIC_URL must be an absolute URL without credentials, query or fragment"
        }
        require(base.scheme == "https" || (base.scheme == "http" && base.host in setOf("localhost", "127.0.0.1", "[::1]"))) {
            "CRM_PUBLIC_URL must use HTTPS (HTTP is allowed only on localhost)"
        }
    }

    fun confirmationLink(token: String): String = link("confirm-email", token)

    /** Ссылка на страницу сброса пароля веб-клиента с токеном [token] во фрагменте. */
    fun passwordResetLink(token: String): String = link("reset-password", token)

    private fun link(page: String, token: String): String {
        require(token.matches(Regex("[A-Za-z0-9_-]+")))
        return "${base.toASCIIString().trimEnd('/')}/$page#token=$token"
    }
}
