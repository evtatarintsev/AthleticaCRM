package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Money(private val cent: Long) {
    operator fun plus(other: Money) = Money(cent + other.cent)

    operator fun minus(other: Money) = Money(cent - other.cent)
}
