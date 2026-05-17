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

interface Task {
    val id: TaskId
    val orgId: OrgId
    val createdBy: EmployeeId
    val assigneeId: EmployeeId?
    val clientId: ClientId?
    val title: String
    val description: String
    val status: TaskStatus
    val dueDate: Instant?
    val dueDateEnd: Instant?
    val completedAt: Instant?
    val createdAt: Instant
    val updatedAt: Instant

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun updateStatus(status: TaskStatus)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun attachUpload(uploadId: UploadId)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun detachUpload(uploadId: UploadId)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun attachmentUploadIds(): List<UploadId>
}
