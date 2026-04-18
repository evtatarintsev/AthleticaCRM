package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class EmailAddress(val value: String) {
    override fun toString() = value
}

fun String.toEmailAddress() = EmailAddress(this)
