package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class PasswordHash(
    val value: String,
)
