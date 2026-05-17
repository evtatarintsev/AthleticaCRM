package org.athletica.crm.core.tasks

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EntityId
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class TaskAttachmentId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = TaskAttachmentId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toTaskAttachmentId(): TaskAttachmentId = TaskAttachmentId(this)

fun org.athletica.crm.core.entityids.TaskAttachmentId.toTaskAttachmentId(): org.athletica.crm.core.tasks.TaskAttachmentId =
    org.athletica.crm.core.tasks.TaskAttachmentId(this.value)
