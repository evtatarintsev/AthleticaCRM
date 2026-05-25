package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.tasks.TaskId
import kotlin.time.Instant

/** Форма создания задачи. Исполнитель и вложения устанавливаются после создания отдельными запросами. */
data class TaskForm(
    val title: String = "",
    val description: String = "",
    val clientId: ClientId? = null,
    val dueDate: Instant? = null,
    val dueDateEnd: Instant? = null,
) {
    /** true если форма заполнена минимально для отправки. */
    val isValid: Boolean get() = title.isNotBlank()
}

/** Состояние экрана создания задачи. */
sealed class TaskCreateState {
    /** Форма готова к заполнению. */
    data object Idle : TaskCreateState()

    /** Отправка запроса. */
    data object Submitting : TaskCreateState()

    /** Задача успешно создана. */
    data object Success : TaskCreateState()

    /** Ошибка при создании. */
    data class Error(val message: String) : TaskCreateState()
}

/**
 * ViewModel экрана создания задачи.
 * Управляет формой и отправкой данных на сервер.
 */
class TaskCreateViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var form by mutableStateOf(TaskForm())
        private set

    var state by mutableStateOf<TaskCreateState>(TaskCreateState.Idle)
        private set

    /** Обновляет поле формы. */
    fun updateForm(update: TaskForm.() -> TaskForm) {
        form = form.update()
    }

    /** Отправляет задачу на сервер. */
    fun submit() {
        if (!form.isValid) return
        scope.launch {
            state = TaskCreateState.Submitting
            val result =
                api.tasks.create(
                    CreateTaskRequest(
                        id = TaskId.new(),
                        title = form.title,
                        description = form.description,
                        clientId = form.clientId,
                        dueDate = form.dueDate,
                        dueDateEnd = form.dueDateEnd,
                    ),
                )
            state =
                result.fold(
                    ifLeft = { e ->
                        val msg = if (e is ApiClientError.ValidationError) e.message else "Ошибка создания задачи"
                        TaskCreateState.Error(msg)
                    },
                    ifRight = { TaskCreateState.Success },
                )
        }
    }
}
