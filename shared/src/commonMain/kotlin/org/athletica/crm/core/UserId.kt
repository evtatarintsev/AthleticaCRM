package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Типобезопасный идентификатор пользователя.
 * Оборачивает [Uuid], исключая случайную подмену на [OrgId] или raw UUID.
 */
@Serializable
@JvmInline
value class UserId(override val value: Uuid) : EntityId {
    companion object {
        /** Генерирует новый UUIDv7 (монотонно возрастающий, подходит для PK). */
        fun new() = UserId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Конвертирует raw [Uuid] в типизированный [UserId]. */
fun Uuid.toUserId(): UserId = UserId(this)
