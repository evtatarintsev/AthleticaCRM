package org.athletica.infra.mail

import org.athletica.crm.core.EmailAddress

@JvmInline
value class Subject(val value: String)

@JvmInline
value class EmailTextBody(val value: String)

@JvmInline
value class EmailHtmlBody(val value: String)

data class Email(
    val subject: Subject,
    val text: EmailTextBody,
    val html: EmailHtmlBody,
    val to: List<EmailAddress>,
)
