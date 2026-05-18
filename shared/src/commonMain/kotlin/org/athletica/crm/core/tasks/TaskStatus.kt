package org.athletica.crm.core.tasks

import kotlinx.serialization.Serializable

/** Статус задачи. */
@Serializable
enum class TaskStatus {
    /** Задача создана, ещё не взята в работу. */
    PENDING,

    /** Задача выполняется. */
    IN_PROGRESS,

    /** Выполнение приостановлено. */
    PAUSED,

    /** Задача завершена. */
    COMPLETED,
}
