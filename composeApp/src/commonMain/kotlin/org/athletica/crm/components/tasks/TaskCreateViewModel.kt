package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.tasks.AttachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.pickAnyFile
import kotlin.time.Instant

/** Форма создания задачи. */
data class TaskForm(
    val title: String = "",
    val description: String = "",
    val clientId: ClientId? = null,
    val dueDate: Instant? = null,
    val dueDateEnd: Instant? = null,
    /** Выбранный исполнитель, либо null если не назначен. */
    val assigneeId: EmployeeId? = null,
    /** Имя выбранного исполнителя — для отображения в форме. */
    val assigneeName: String? = null,
    /** Уже загруженные файлы, которые будут прикреплены к задаче после создания. */
    val attachments: List<UploadResponse> = emptyList(),
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

    /** Открывает файл-пикер, загружает файл и добавляет его в список вложений формы. */
    fun pickAttachment() {
        scope.launch {
            val file = pickAnyFile() ?: return@launch
            api.documents.upload(file.first, file.second, file.third).onRight { upload ->
                form = form.copy(attachments = form.attachments + upload)
            }
        }
    }

    /** Убирает вложение [uploadId] из формы (файл ещё не привязан к задаче). */
    fun removeAttachment(uploadId: UploadId) {
        form = form.copy(attachments = form.attachments.filterNot { it.id == uploadId })
    }

    /** Отправляет задачу на сервер и прикрепляет выбранные файлы. */
    fun submit() {
        if (!form.isValid) return
        scope.launch {
            state = TaskCreateState.Submitting
            val taskId = TaskId.new()
            val result =
                api.tasks.create(
                    CreateTaskRequest(
                        id = taskId,
                        title = form.title,
                        description = form.description,
                        clientId = form.clientId,
                        dueDate = form.dueDate,
                        dueDateEnd = form.dueDateEnd,
                        assigneeId = form.assigneeId,
                    ),
                )
            state =
                result.fold(
                    ifLeft = { e ->
                        val msg = if (e is ApiClientError.ValidationError) e.message else "Ошибка создания задачи"
                        TaskCreateState.Error(msg)
                    },
                    ifRight = {
                        form.attachments.forEach { upload ->
                            api.tasks.attach(AttachTaskUploadRequest(taskId, upload.id))
                        }
                        TaskCreateState.Success
                    },
                )
        }
    }
}
