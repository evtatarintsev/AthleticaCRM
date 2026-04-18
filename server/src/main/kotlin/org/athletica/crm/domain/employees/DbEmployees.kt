package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toEmailAddress
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
        email: EmailAddress?,
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
        val roles = rolesByEmployeeId()
        return tr
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
                    roles = roles[id] ?: emptyList(),
                    phoneNo = row.asStringOrNull("phone_no"),
                    email = row.asStringOrNull("email")?.toEmailAddress(),
                    orgId = ctx.orgId,
                )
            }
            ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Employee> {
        val roles = rolesByEmployeeId()
        return tr
            .sql(
                """
                SELECT e.id, e.name, e.avatar_id, e.is_owner, e.is_active, e.joined_at, e.phone_no, e.email, e.user_id
                FROM employees e
                WHERE e.org_id = :orgId
                ORDER BY e.is_owner DESC, e.name
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .list { row ->
                val id = row.asUuid("id").toEmployeeId()
                DbEmployee(
                    id = id,
                    name = row.asString("name"),
                    avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                    isOwner = row.asBoolean("is_owner"),
                    isActive = row.asBoolean("is_active"),
                    joinedAt = row.asInstant("joined_at"),
                    phoneNo = row.asStringOrNull("phone_no"),
                    email = row.asStringOrNull("email")?.toEmailAddress(),
                    orgId = ctx.orgId,
                    userId = row.asUuidOrNull("user_id")?.toUserId(),
                    roles = roles[id] ?: emptyList(),
                )
            }
    }

    context(ctx: RequestContext, tr: Transaction)
    suspend fun rolesByEmployeeId(): Map<EmployeeId, List<EmployeeRole>> =
        tr
            .sql(
                """
                SELECT er.employee_id, r.id AS role_id, r.name AS role_name
                FROM employee_roles er
                JOIN roles r ON r.id = er.role_id
                JOIN employees e ON e.id = er.employee_id
                WHERE e.org_id = :orgId
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .list { row ->
                val employeeId = row.asUuid("employee_id").toEmployeeId()
                val role =
                    EmployeeRole(
                        id = row.asUuid("role_id"),
                        name = row.asString("role_name"),
                    )
                employeeId to role
            }
            .groupBy({ it.first }, { it.second })
}
