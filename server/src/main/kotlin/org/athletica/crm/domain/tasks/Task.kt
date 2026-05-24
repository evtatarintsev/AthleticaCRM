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
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/** Агрегат задачи. Хранит состояние и умеет сохранять себя в БД. */
interface Task {
    val id: TaskId

    /** Идентификатор организации-владельца задачи. */
    val orgId: OrgId

    /** Сотрудник, создавший задачу. */
    val createdBy: EmployeeId

    /** Назначенный исполнитель, либо null если не назначен. */
    var assigneeId: EmployeeId?

    /** Привязанный клиент, либо null. */
    var clientId: ClientId?

    var title: String

    var description: String

    var status: TaskStatus

    /** Начало срока выполнения. */
    var dueDate: Instant?

    /** Конец срока выполнения. */
    var dueDateEnd: Instant?

    /** Время фактического завершения. Проставляется автоматически при переходе в COMPLETED. */
    val completedAt: Instant?

    val createdAt: Instant

    /** Список идентификаторов файловых вложений. */
    var attachments: List<UploadId>

    /**
     * Сохраняет изменения задачи в БД.
     * Обновляет [completedAt]: устанавливает NOW() при переходе в COMPLETED, сбрасывает при выходе.
     * Выполняет диффинг [attachments]: удаляет ушедшие, добавляет новые связки.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()
}
