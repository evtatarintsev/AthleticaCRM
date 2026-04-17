package org.athletica.crm.usecases.employees

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.toEmployeeId
import org.athletica.crm.core.toUploadId
import org.athletica.crm.db.Database
import org.athletica.crm.db.asBoolean
import org.athletica.crm.db.asInstant
import org.athletica.crm.db.asString
import org.athletica.crm.db.asStringOrNull
import org.athletica.crm.db.asUuid
import org.athletica.crm.db.asUuidOrNull
import kotlin.time.Instant

/**
 * Возвращает список всех сотрудников организации из контекста [ctx].
 *
 * Выполняет два запроса:
 * 1. Загружает строки сотрудников (`employees`), отсортированных: владелец первым, далее по имени.
 * 2. Загружает роли сотрудников (`employee_roles` JOIN `roles`) и группирует их по `employee_id`.
 *
 * Возвращает [List<EmployeeListItem>] с заполненными ролями, либо [CommonDomainError] при сбое.
 */
context(db: Database, ctx: RequestContext)
suspend fun employeeList(): Either<CommonDomainError, List<EmployeeListItem>> =
    either {
        data class EmployeeRow(
            val id: EmployeeId,
            val name: String,
            val avatarId: UploadId?,
            val isOwner: Boolean,
            val isActive: Boolean,
            val joinedAt: Instant,
            val phoneNo: String?,
            val email: String?,
        )

        val rows =
            db
                .sql(
                    """
                    SELECT e.id, e.name, e.avatar_id, e.is_owner, e.is_active, e.joined_at, e.phone_no, e.email
                    FROM employees e
                    WHERE e.org_id = :orgId
                    ORDER BY e.is_owner DESC, e.name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    EmployeeRow(
                        id = row.asUuid("id").toEmployeeId(),
                        name = row.asString("name"),
                        avatarId = row.asUuidOrNull("avatar_id")?.toUploadId(),
                        isOwner = row.asBoolean("is_owner"),
                        isActive = row.asBoolean("is_active"),
                        joinedAt = row.asInstant("joined_at"),
                        phoneNo = row.asStringOrNull("phone_no"),
                        email = row.asStringOrNull("email"),
                    )
                }

        val rolesByEmployeeId =
            db
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

        rows.map { row ->
            EmployeeListItem(
                id = row.id,
                name = row.name,
                avatarId = row.avatarId,
                isOwner = row.isOwner,
                isActive = row.isActive,
                joinedAt = row.joinedAt,
                roles = rolesByEmployeeId[row.id] ?: emptyList(),
                phoneNo = row.phoneNo,
                email = row.email,
            )
        }
    }
