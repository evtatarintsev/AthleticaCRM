package org.athletica.crm.core.tasks

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
}
