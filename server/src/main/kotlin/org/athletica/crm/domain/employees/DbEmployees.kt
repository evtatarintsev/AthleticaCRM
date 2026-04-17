package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toEmployeeId
import org.athletica.crm.core.toUploadId
import org.athletica.crm.core.toUserId
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock

class DbEmployees : Employees {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: String?,
        avatarId: UploadId?,
    ): Employee {
        try {
            tr.sql(
                """
                INSERT INTO employees (id, org_id, name, avatar_id, phone_no, email, is_active)
                VALUES (:id, :orgId, :name, :avatarId, :phoneNo, :email, false)
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .bind("avatarId", avatarId)
                .bind("phoneNo", phoneNo)
                .bind("email", email)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            if (e.message?.contains("employees_pkey") == true) {
                raise(CommonDomainError("EMPLOYEE_ALREADY_EXISTS", Messages.EmployeeAlreadyExists.localize()))
            }
            throw e
        }
        return DbEmployee(
            id = id, userId = null,
            name = name, avatarId = avatarId, isOwner = false, isActive = false,
            joinedAt = Clock.System.now(), roles = emptyList(), phoneNo = phoneNo, email = email,
            orgId = ctx.orgId,
        )
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId): Employee {
        val employee =
            tr
                .sql(
                    """
                    SELECT id, user_id, name, avatar_id, is_owner, is_active, joined_at, phone_no, email
                    FROM employees
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    DbEmployee(
                        id = row.asUuid("id").toEmployeeId(),
                        userId = row.asUuidOrNull("user_id")?.toUserId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        isOwner = row.asBoolean("is_owner"),
                        isActive = row.asBoolean("is_active"),
                        joinedAt = row.asInstant("joined_at"),
                        roles = emptyList(),
                        phoneNo = row.asStringOrNull("phone_no"),
                        email = row.asStringOrNull("email"),
                        orgId = ctx.orgId,
                    )
                }
                ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))

        val roles =
            tr
                .sql(
                    """
                    SELECT r.id AS role_id, r.name AS role_name
                    FROM employee_roles er
                    JOIN roles r ON r.id = er.role_id
                    WHERE er.employee_id = :employeeId
                    """.trimIndent(),
                )
                .bind("employeeId", id)
                .list { row ->
                    EmployeeRole(
                        id = row.asUuid("role_id"),
                        name = row.asString("role_name"),
                    )
                }

        return DbEmployee(
            id = employee.id,
            userId = employee.userId,
            name = employee.name,
            avatarId = employee.avatarId,
            isOwner = employee.isOwner,
            isActive = employee.isActive,
            joinedAt = employee.joinedAt,
            roles = roles,
            phoneNo = employee.phoneNo,
            email = employee.email,
            orgId = ctx.orgId,
        )
    }
}
