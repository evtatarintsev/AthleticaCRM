package org.athletica.crm.read.tasks

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.read.uploads.UploadRow
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Карточка задачи со всеми связанными именами.
 *
 * Своим типом, а не `TaskDetailResponse`, потому что ссылки на вложения подписываются
 * объектным хранилищем: проекция отдаёт [attachments] метаданными, а подпись и сборку
 * итогового ответа делает слой routes.
 */
data class TaskDetailRow(
    /** Идентификатор задачи. */
    val id: TaskId,
    /** Сотрудник, создавший задачу. */
    val createdBy: EmployeeId,
    /** Имя создателя задачи. */
    val createdByName: String,
    /** Назначенный исполнитель, либо `null`. */
    val assigneeId: EmployeeId?,
    /** Имя исполнителя, либо `null` если исполнитель не назначен. */
    val assigneeName: String?,
    /** Привязанный клиент, либо `null`. */
    val clientId: ClientId?,
    /** Имя привязанного клиента, либо `null`. */
    val clientName: String?,
    /** Заголовок задачи. */
    val title: String,
    /** Описание задачи. */
    val description: String,
    /** Текущий статус. */
    val status: TaskStatus,
    /** Начало срока выполнения. */
    val dueDate: Instant?,
    /** Конец срока выполнения. */
    val dueDateEnd: Instant?,
    /** Время фактического завершения. */
    val completedAt: Instant?,
    /** Время создания задачи. */
    val createdAt: Instant,
    /** Вложения задачи в порядке прикрепления. */
    val attachments: List<UploadRow>,
)

/**
 * Read-проекция карточки задачи: задача, имена создателя, исполнителя и клиента,
 * метаданные вложений — одним запросом.
 */
interface TaskDetailView {
    /** Возвращает карточку задачи [id]; ошибка, если задача не найдена в организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: TaskId): TaskDetailRow
}
