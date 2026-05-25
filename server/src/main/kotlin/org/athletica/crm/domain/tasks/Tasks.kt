package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/** Репозиторий задач. */
interface Tasks {
    /** Возвращает задачу по идентификатору. Бросает ошибку если задача не найдена или принадлежит другой организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: TaskId): Task

    /**
     * Возвращает список задач по идентификаторам.
     * Если хотя бы один идентификатор не найден или принадлежит другой организации — бросает ошибку.
     * Порядок результата соответствует порядку [ids].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<TaskId>): List<Task>

    /** Возвращает постраничный список задач с применением [filter]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(filter: TaskFilter): TaskList

    /**
     * Создаёт новую задачу со статусом PENDING и сохраняет её в БД.
     * Исполнитель и вложения устанавливаются отдельными операциями на агрегате.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: TaskId,
        title: String,
        description: String,
        clientId: ClientId?,
        dueDate: Instant?,
        dueDateEnd: Instant?,
    ): Task
}

/** Результат постраничного запроса задач. */
data class TaskList(
    val items: List<Task>,
    /** Общее количество задач, удовлетворяющих фильтру (без учёта пагинации). */
    val total: UInt,
)

/** Параметры фильтрации списка задач. */
data class TaskFilter(
    /** Если true — возвращать только задачи, где текущий сотрудник исполнитель или создатель. */
    val onlyMine: Boolean,
    /** Фильтр по статусам. Пустое множество — все статусы. */
    val statuses: Set<TaskStatus>,
    val dueDateFrom: Instant?,
    val dueDateTo: Instant?,
    val clientId: ClientId?,
    /** Подстрока для поиска по заголовку и описанию (ILIKE). */
    val searchText: String?,
    val limit: Int,
    val offset: Int,
)
