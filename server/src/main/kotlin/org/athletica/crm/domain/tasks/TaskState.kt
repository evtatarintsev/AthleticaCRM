package org.athletica.crm.domain.tasks

import org.athletica.crm.core.tasks.TaskStatus
import kotlin.time.Instant

/** Состояние задачи. Время завершения есть только у завершённой задачи. */
sealed interface TaskState {
    /** Статус задачи для хранения и API. */
    val status: TaskStatus

    /** Незавершённое состояние: задачу можно перевести в другое незавершённое или завершить. */
    sealed interface Open : TaskState

    /** Задача создана, ещё не взята в работу. */
    data object Pending : Open {
        override val status = TaskStatus.PENDING
    }

    /** Задача выполняется. */
    data object InProgress : Open {
        override val status = TaskStatus.IN_PROGRESS
    }

    /** Выполнение приостановлено. */
    data object Paused : Open {
        override val status = TaskStatus.PAUSED
    }

    /** Задача завершена в момент [at]. */
    data class Completed(
        /** Момент завершения. */
        val at: Instant,
    ) : TaskState {
        override val status = TaskStatus.COMPLETED
    }
}
