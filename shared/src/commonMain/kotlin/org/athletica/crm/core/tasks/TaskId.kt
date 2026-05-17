package org.athletica.crm.core.tasks

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EntityId
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class TaskId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = TaskId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toTaskId(): TaskId = TaskId(this)

fun org.athletica.crm.core.entityids.TaskId.toTaskId(): org.athletica.crm.core.tasks.TaskId =
    org.athletica.crm.core.tasks.TaskId(this.value)
