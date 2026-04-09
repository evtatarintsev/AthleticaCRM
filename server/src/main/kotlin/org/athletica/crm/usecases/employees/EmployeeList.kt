package org.athletica.crm.usecases.employees

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun employeeList(): Either<CommonDomainError, List<EmployeeListItem>> =
    either {
        data class EmployeeRow(
            val id: Uuid,
            val name: String,
            val avatarId: Uuid?,
            val isOwner: Boolean,
            val isActive: Boolean,
            val joinedAt: java.time.Instant,
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
                .bind("orgId", ctx.orgId.value)
                .list { row ->
                    EmployeeRow(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        name = row.get("name", String::class.java)!!,
                        avatarId = row.get("avatar_id", java.util.UUID::class.java)?.toKotlinUuid(),
                        isOwner = row.get("is_owner", Boolean::class.java)!!,
                        isActive = row.get("is_active", Boolean::class.java)!!,
                        joinedAt = row.get("joined_at", java.time.OffsetDateTime::class.java)!!.toInstant(),
                        phoneNo = row.get("phone_no", String::class.java),
                        email = row.get("email", String::class.java),
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
                .bind("orgId", ctx.orgId.value)
                .list { row ->
                    val employeeId = row.get("employee_id", java.util.UUID::class.java)!!.toKotlinUuid()
                    val role =
                        EmployeeRole(
                            id = row.get("role_id", java.util.UUID::class.java)!!.toKotlinUuid(),
                            name = row.get("role_name", String::class.java)!!,
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
                joinedAt = row.joinedAt.toKotlinInstant(),
                roles = rolesByEmployeeId[row.id] ?: emptyList(),
                phoneNo = row.phoneNo,
                email = row.email,
            )
        }
    }
