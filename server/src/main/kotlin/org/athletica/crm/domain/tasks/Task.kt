package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/** Неизменяемый агрегат задачи. Все трансформации возвращают новый экземпляр. */
interface Task {
    val id: TaskId

    /** Идентификатор организации-владельца задачи. */
    val orgId: OrgId

    /** Сотрудник, создавший задачу. */
    val createdBy: EmployeeId

    /** Назначенный исполнитель, либо null если не назначен. */
    val assigneeId: EmployeeId?

    /** Привязанный клиент, либо null. */
    val clientId: ClientId?

    val title: String

    val description: String

    val status: TaskStatus

    /** Начало срока выполнения. */
    val dueDate: Instant?

    /** Конец срока выполнения. */
    val dueDateEnd: Instant?

    /** Время фактического завершения. Проставляется автоматически при переходе в COMPLETED. */
    val completedAt: Instant?

    val createdAt: Instant

    /** Список идентификаторов файловых вложений. */
    val attachments: List<UploadId>

    /**
     * Сохраняет текущее состояние задачи в БД.
     * Обновляет [completedAt]: устанавливает NOW() при переходе в COMPLETED, сбрасывает при выходе.
     * Выполняет диффинг [attachments]: удаляет ушедшие, добавляет новые связки.
     */
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /**
     * Возвращает копию задачи с обновлёнными полями.
     * Требует права на редактирование: без [org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_TASKS]
     * разрешено только для задач, где сотрудник — создатель или исполнитель.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun withNew(
        newTitle: String,
        newDescription: String,
        newClientId: ClientId?,
        newDueDate: Instant?,
        newDueDateEnd: Instant?,
    ): Task

    /**
     * Возвращает копию задачи с назначенным [employee].
     * Требует права на редактирование.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun assignTo(employee: Employee): Task

    /**
     * Возвращает копию задачи со снятым исполнителем.
     * Требует права на редактирование.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun unassign(): Task

    /**
     * Возвращает копию задачи с изменённым [newStatus].
     * Требует права на редактирование.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun status(newStatus: TaskStatus): Task

    /**
     * Возвращает копию задачи с добавленным вложением [uploadId].
     * Идемпотентна: если вложение уже есть, возвращает текущий экземпляр без изменений.
     * Требует права на редактирование.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun attach(uploadId: UploadId): Task

    /**
     * Возвращает копию задачи с удалённым вложением [uploadId].
     * Требует права на редактирование.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun detach(uploadId: UploadId): Task
}
