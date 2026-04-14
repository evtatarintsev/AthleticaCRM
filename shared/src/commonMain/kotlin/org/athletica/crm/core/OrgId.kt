package org.athletica.crm.core

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Типобезопасный идентификатор организации.
 * Оборачивает [Uuid], исключая случайную подмену на [UserId] или raw UUID.
 */
@JvmInline
value class OrgId(override val value: Uuid) : EntityId {
    companion object {
        /** Генерирует новый UUIDv7 (монотонно возрастающий, подходит для PK). */
        fun new() = OrgId(Uuid.generateV7())
    }
}

/** Конвертирует raw [Uuid] в типизированный [OrgId]. */
fun Uuid.toOrgId(): OrgId = OrgId(this)
