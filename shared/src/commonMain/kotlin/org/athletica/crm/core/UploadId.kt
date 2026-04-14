package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class UploadId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = UploadId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toUploadId(): UploadId = UploadId(this)
