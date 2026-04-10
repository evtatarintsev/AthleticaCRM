package org.athletica.infra.mail

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromAddress: String,
    val fromName: String,
    val useTls: Boolean = true,
)

class SmtpMailbox(private val config: SmtpConfig) : Mailbox {
    private val session: Session =
        run {
            val props =
                Properties().apply {
                    put("mail.smtp.host", config.host)
                    put("mail.smtp.port", config.port.toString())
                    put("mail.smtp.auth", "true")
                    if (config.useTls) {
                        put("mail.smtp.starttls.enable", "true")
                    }
                }
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication(config.username, config.password)
                },
            )
        }

    override suspend fun send(email: Email) {
        withContext(Dispatchers.IO) {
            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(config.fromAddress, config.fromName, "UTF-8"))
                    setRecipients(
                        Message.RecipientType.TO,
                        email.to.map { InternetAddress(it.value) }.toTypedArray(),
                    )
                    subject = email.subject.value

                    val textPart = MimeBodyPart().apply { setText(email.text.value, "UTF-8") }
                    val htmlPart = MimeBodyPart().apply { setContent(email.html.value, "text/html; charset=UTF-8") }
                    setContent(MimeMultipart("alternative", textPart, htmlPart))
                }
            Transport.send(message)
        }
    }
}
