package org.athletica.crm.core.tasks

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EntityId
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Уникальный идентификатор задачи. */
@Serializable
@JvmInline
value class TaskId(override val value: Uuid) : EntityId {
    companion object {
        /** Создаёт новый уникальный идентификатор задачи (UUID v7). */
        fun new() = TaskId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Конвертирует [Uuid] в [TaskId]. */
fun Uuid.toTaskId(): TaskId = TaskId(this)
