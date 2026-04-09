package org.athletica.crm.usecases.employees

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.time.toKotlinInstant
import kotlin.uuid.toJavaUuid

context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun createEmployee(request: CreateEmployeeRequest): Either<CommonDomainError, EmployeeListItem> =
    either {
        val now = java.time.Instant.now()
        try {
            db
                .sql(
                    """
                    INSERT INTO employees (id, user_id, org_id, name, avatar_id, phone_no, email)
                    VALUES (:id, :userId, :orgId, :name, :avatarId, :phoneNo, :email)
                    """.trimIndent(),
                )
                .bind("id", request.id)
                .bind("userId", ctx.userId.value)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .bind("avatarId", request.avatarId?.toJavaUuid())
                .bind("phoneNo", request.phoneNo)
                .bind("email", request.email)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("EMPLOYEE_ALREADY_EXISTS", Messages.EmployeeAlreadyExists.localize()))
        }

        EmployeeListItem(
            id = request.id,
            name = request.name,
            avatarId = request.avatarId,
            isOwner = false,
            isActive = true,
            joinedAt = now.toKotlinInstant(),
            roles = emptyList<EmployeeRole>(),
            phoneNo = request.phoneNo,
            email = request.email,
        ).also { audit.logCreate(it) }
    }

/** Логирует создание сотрудника: тип сущности `"employee"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: EmployeeListItem) = logCreate("employee", result.id, Json.encodeToString(result))
