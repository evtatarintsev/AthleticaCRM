package org.athletica.crm.core

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Типобезопасный идентификатор пользователя.
 * Оборачивает [Uuid], исключая случайную подмену на [OrgId] или raw UUID.
 */
@JvmInline
value class UserId(val value: Uuid) {
    companion object {
        /** Генерирует новый UUIDv7 (монотонно возрастающий, подходит для PK). */
        fun new() = UserId(Uuid.generateV7())
    }
}

/** Конвертирует raw [Uuid] в типизированный [UserId]. */
fun Uuid.toUserId(): UserId = UserId(this)
