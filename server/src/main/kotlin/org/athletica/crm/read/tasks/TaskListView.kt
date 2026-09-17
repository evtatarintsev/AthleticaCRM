package org.athletica.crm.read.tasks

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Параметры выборки списка задач. Значения уже нормализованы вызывающим слоем
 * (пустой поисковый запрос приведён к `null`).
 */
data class TaskListQuery(
    /** Только задачи, где текущий сотрудник исполнитель или создатель. */
    val onlyMine: Boolean,
    /** Фильтр по статусам. Пустое множество — все статусы. */
    val statuses: Set<TaskStatus>,
    /** Нижняя граница срока выполнения. */
    val dueDateFrom: Instant?,
    /** Верхняя граница срока выполнения. */
    val dueDateTo: Instant?,
    /** Только задачи, привязанные к этому клиенту. */
    val clientId: ClientId?,
    /** Подстрока для поиска по заголовку и описанию (регистронезависимо). */
    val searchText: String?,
    /** Размер страницы. */
    val limit: Int,
    /** Смещение от начала выборки. */
    val offset: Int,
)

/**
 * Read-проекция списка задач: подставляет имена исполнителя и связанного клиента
 * join'ом, не загружая агрегаты сотрудников и клиентов.
 *
 * Видимость чужих задач определяется здесь же: без
 * [org.athletica.crm.core.permissions.UserPermission.CAN_VIEW_ALL_TASKS] сотрудник
 * видит только задачи, где он исполнитель или создатель.
 */
interface TaskListView {
    /** Возвращает страницу задач согласно [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun page(query: TaskListQuery): TaskListResponse
}
