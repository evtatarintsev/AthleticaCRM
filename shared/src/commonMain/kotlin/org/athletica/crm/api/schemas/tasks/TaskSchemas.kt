package org.athletica.crm.api.schemas.tasks

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import kotlin.time.Instant

/** Запрос на получение списка задач с фильтрами и пагинацией. */
@Serializable
data class TaskListRequest(
    /** Показывать только задачи, в которых текущий сотрудник — исполнитель или создатель. */
    val onlyMine: Boolean = false,
    /** Фильтрация по статусам. Пустой список — все статусы. */
    val statuses: List<TaskStatus> = emptyList(),
    /** Нижняя граница срока выполнения (включительно). */
    val dueDateFrom: Instant? = null,
    /** Верхняя граница срока выполнения (включительно). */
    val dueDateTo: Instant? = null,
    /** Фильтрация по клиенту. */
    val clientId: ClientId? = null,
    /** Полнотекстовый поиск по заголовку и описанию. */
    val searchText: String? = null,
    /** Максимальное количество записей в ответе. */
    val limit: Int = 50,
    /** Смещение для пагинации. */
    val offset: Int = 0,
)

/** Элемент списка задач. */
@Serializable
data class TaskListItemSchema(
    val id: TaskId,
    val title: String,
    /** Идентификатор исполнителя задачи, либо null если не назначен. */
    val assigneeId: EmployeeId?,
    /** Имя исполнителя задачи. */
    val assigneeName: String?,
    /** Идентификатор связанного клиента. */
    val clientId: ClientId?,
    /** Имя связанного клиента. */
    val clientName: String?,
    val status: TaskStatus,
    /** Начало срока выполнения. */
    val dueDate: Instant?,
    /** Конец срока выполнения. */
    val dueDateEnd: Instant?,
)

/** Ответ со списком задач и общим количеством. */
@Serializable
data class TaskListResponse(
    val tasks: List<TaskListItemSchema>,
    /** Общее количество задач, удовлетворяющих фильтру (без учёта пагинации). */
    val total: UInt,
)

/** Запрос на получение детальной информации о задаче. */
@Serializable
data class TaskDetailRequest(val taskId: TaskId)

/** Детальная информация о задаче. */
@Serializable
data class TaskDetailResponse(
    val id: TaskId,
    /** Идентификатор создателя задачи. */
    val createdBy: EmployeeId,
    /** Имя создателя задачи. */
    val createdByName: String,
    val assigneeId: EmployeeId?,
    val assigneeName: String?,
    val clientId: ClientId?,
    val clientName: String?,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val dueDate: Instant?,
    val dueDateEnd: Instant?,
    /** Время завершения задачи. Заполняется автоматически при переходе в статус COMPLETED. */
    val completedAt: Instant?,
    val createdAt: Instant,
    /** Список файловых вложений. */
    val attachments: List<UploadResponse>,
)

/** Запрос на создание задачи. Исполнитель и вложения устанавливаются отдельными запросами. */
@Serializable
data class CreateTaskRequest(
    /** Клиентский идентификатор (UUID v7), генерируется на клиенте. */
    val id: TaskId,
    val title: String,
    val description: String,
    val clientId: ClientId?,
    val dueDate: Instant?,
    val dueDateEnd: Instant?,
)

/** Запрос на обновление полей задачи. Исполнитель, статус и вложения — через отдельные запросы. */
@Serializable
data class UpdateTaskRequest(
    val id: TaskId,
    val title: String,
    val description: String,
    val clientId: ClientId?,
    val dueDate: Instant?,
    val dueDateEnd: Instant?,
)

/** Запрос на изменение статуса у одной или нескольких задач. */
@Serializable
data class UpdateTaskStatusRequest(
    /** Идентификаторы задач, которым меняется статус. */
    val taskIds: List<TaskId>,
    val status: TaskStatus,
)

/** Запрос на назначение исполнителя одной или нескольким задачам. */
@Serializable
data class AssignTaskRequest(
    /** Идентификаторы задач. */
    val taskIds: List<TaskId>,
    /** Новый исполнитель. */
    val assigneeId: EmployeeId,
)

/** Запрос на снятие исполнителя с одной или нескольких задач. */
@Serializable
data class UnassignTaskRequest(
    /** Идентификаторы задач. */
    val taskIds: List<TaskId>,
)

/** Запрос на прикрепление файла к задаче. */
@Serializable
data class AttachTaskUploadRequest(
    val taskId: TaskId,
    val uploadId: UploadId,
)

/** Запрос на открепление файла от задачи. */
@Serializable
data class DetachTaskUploadRequest(
    val taskId: TaskId,
    val uploadId: UploadId,
)

/** Ответ на операцию с несколькими задачами. */
@Serializable
data class BulkUpdateTasksResponse(
    /** Количество фактически обновлённых задач. */
    val updated: Int,
)
