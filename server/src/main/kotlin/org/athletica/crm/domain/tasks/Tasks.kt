package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

interface Tasks {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: TaskId): Task

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(filter: TaskFilter): List<Task>

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: TaskId,
        orgId: OrgId,
        createdBy: EmployeeId,
        title: String,
        description: String,
        assigneeId: EmployeeId?,
        clientId: ClientId?,
        dueDate: Instant?,
        dueDateEnd: Instant?,
    ): Task
}

data class TaskFilter(
    val assigneeId: EmployeeId?,
    val createdBy: EmployeeId?,
    val status: Set<TaskStatus>?,
    val dueDateFrom: Instant?,
    val dueDateTo: Instant?,
    val clientId: ClientId?,
    val searchText: String?,
)
