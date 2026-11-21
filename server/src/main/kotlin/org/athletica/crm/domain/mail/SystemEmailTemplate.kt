package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.Lang
import org.athletica.crm.i18n.Messages
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Subject
import java.net.URI

data class EmailAction(val label: String, val url: String)

fun systemEmail(
    to: EmailAddress,
    lang: Lang,
    subject: String,
    title: String,
    paragraphs: List<String>,
    footer: String,
    greeting: String? = null,
    action: EmailAction? = null,
): Email {
    require(subject.none { it == '\r' || it == '\n' }) { "Email subject must not contain line breaks" }
    action?.let {
        val uri = URI(it.url)
        require(uri.scheme in setOf("https", "http") && uri.host != null && uri.rawUserInfo == null) { "Invalid email action URL" }
    }
    val text = listOfNotNull(title, greeting, *paragraphs.toTypedArray(), action?.let { "${it.label}: ${it.url}" }, footer).joinToString("\n\n")
    val html =
        """
        <!doctype html><html lang="${lang.code}"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;background:#f4f6f8;color:#182230;font-family:Arial,sans-serif;line-height:1.6">
        <div style="max-width:560px;margin:24px auto;padding:24px;background:#ffffff;overflow-wrap:anywhere;word-break:break-word">
        <p style="font-size:20px;font-weight:bold;color:#176b55">AthleticaCRM</p><h1 style="font-size:24px">${title.html()}</h1>
        ${greeting?.let { "<p>${it.html()}</p>" }.orEmpty()}
        ${paragraphs.joinToString("") { "<p>${it.html()}</p>" }}
        ${action?.let { "<p><a href=\"${it.url.html()}\" style=\"display:inline-block;max-width:100%;box-sizing:border-box;padding:12px 20px;background:#176b55;color:#ffffff;border-radius:6px;text-decoration:none\">${it.label.html()}</a></p><p><a href=\"${it.url.html()}\" style=\"color:#176b55;word-break:break-all\">${it.url.html()}</a></p>" }.orEmpty()}
        <hr style="border:0;border-top:1px solid #e1e5ea"><p style="font-size:13px;color:#52606d">${footer.html()}</p>
        </div></body></html>
        """.trimIndent()
    return Email(Subject(subject), EmailTextBody(text), EmailHtmlBody(html), listOf(to))
}

fun registrationEmail(
    to: EmailAddress,
    name: String,
    lang: Lang,
    link: String,
): Email =
    systemEmail(
        to = to,
        lang = lang,
        subject = Messages.RegistrationEmailTitle.localize(lang),
        title = Messages.RegistrationEmailTitle.localize(lang),
        greeting = Messages.EmailGreeting.localize(lang, name),
        paragraphs = listOf(Messages.RegistrationEmailBody.localize(lang), Messages.EmailConfirmationExpiry.localize(lang)),
        action = EmailAction(Messages.ConfirmEmailAction.localize(lang), link),
        footer = Messages.RegistrationEmailFooter.localize(lang),
    )

/** Письмо со ссылкой [link] для сброса пароля пользователя [name] на адрес [to], на языке [lang]. */
fun passwordResetEmail(
    to: EmailAddress,
    name: String,
    lang: Lang,
    link: String,
): Email =
    systemEmail(
        to = to,
        lang = lang,
        subject = Messages.PasswordResetEmailTitle.localize(lang),
        title = Messages.PasswordResetEmailTitle.localize(lang),
        greeting = Messages.EmailGreeting.localize(lang, name),
        paragraphs = listOf(Messages.PasswordResetEmailBody.localize(lang), Messages.PasswordResetExpiry.localize(lang)),
        action = EmailAction(Messages.PasswordResetAction.localize(lang), link),
        footer = Messages.PasswordResetEmailFooter.localize(lang),
    )

private fun String.html(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
