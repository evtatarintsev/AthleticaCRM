package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Типобезопасный идентификатор организации.
 * Оборачивает [Uuid], исключая случайную подмену на [UserId] или raw UUID.
 */
@Serializable
@JvmInline
value class OrgId(override val value: Uuid) : EntityId {
    companion object {
        /** Генерирует новый UUIDv7 (монотонно возрастающий, подходит для PK). */
        fun new() = OrgId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Конвертирует raw [Uuid] в типизированный [OrgId]. */
fun Uuid.toOrgId(): OrgId = OrgId(this)
