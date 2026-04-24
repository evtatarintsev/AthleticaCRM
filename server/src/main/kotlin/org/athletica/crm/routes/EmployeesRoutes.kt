package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.CreateRoleRequest
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.api.schemas.employees.RoleListResponse
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.api.schemas.employees.UpdateEmployeeRequest
import org.athletica.crm.api.schemas.employees.UpdateRoleRequest
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.employees.Roles
import org.athletica.crm.storage.Database
import org.athletica.crm.domain.employees.EmployeeRole as DomainEmployeeRole

context(db: Database)
fun RouteWithContext.employeesRoutes(employees: Employees, roles: Roles) {
    route("/employees") {
        get("/list") {
            call.eitherToResponse {
                db.transaction {
                    employees.list()
                }.let { employees ->
                    EmployeeListResponse(
                        employees.map { it.toListItem() },
                        employees.size.toUInt(),
                    )
                }
            }
        }

        get("/detail") {
            call.eitherToResponse {
                val id = call.request.queryParameters.asUuid("id").toEmployeeId()
                db.transaction {
                    employees.byId(id)
                }.toDetailResponse()
            }
        }

        post<CreateEmployeeRequest, EmployeeListItem>("/create") { request ->
            db.transaction {
                val allRoles = roles.list()
                val selectedRoles = allRoles.filter { it.id in request.roleIds }
                val permissions =
                    EmployeePermission(
                        roles = selectedRoles,
                        grantedPermissions = request.grantedPermissions,
                        revokedPermissions = request.revokedPermissions,
                    )
                employees
                    .new(request.id, request.name, request.phoneNo, request.email, request.avatarId, permissions)
            }.toListItem()
        }

        post<UpdateEmployeeRequest, Unit>("/update") { request ->
            db.transaction {
                val allRoles = roles.list()
                val selectedRoles = allRoles.filter { it.id in request.roleIds }
                val permissions =
                    EmployeePermission(
                        roles = selectedRoles,
                        grantedPermissions = request.grantedPermissions,
                        revokedPermissions = request.revokedPermissions,
                    )
                employees
                    .byId(request.id)
                    .withNew(
                        newName = request.name,
                        newPhoneNo = request.phoneNo,
                        newEmail = request.email,
                        newAvatarId = request.avatarId,
                        newPermissions = permissions,
                    ).save()
            }
        }

        post<SendEmployeeAccessRequest, Unit>("/send-access") { request ->
            db.transaction {
                employees
                    .byId(request.employeeId)
                    .invite(request.email, request.password)
            }
        }

        get("/roles") {
            call.eitherToResponse {
                db.transaction {
                    roles.list()
                }.let { roles ->
                    RoleListResponse(roles.map { RoleItem(it.id, it.name, it.permissions) })
                }
            }
        }

        post<CreateRoleRequest, RoleItem>("/roles/create") { request ->
            db.transaction {
                roles.new(DomainEmployeeRole(request.id, request.name, request.permissions))
            }
            RoleItem(request.id, request.name, request.permissions)
        }

        post<UpdateRoleRequest, RoleItem>("/roles/update") { request ->
            db.transaction {
                roles.update(DomainEmployeeRole(request.id, request.name, request.permissions))
            }
            RoleItem(request.id, request.name, request.permissions)
        }
    }
}

fun Employee.toListItem() =
    EmployeeListItem(
        id,
        name,
        avatarId,
        isOwner,
        isActive,
        joinedAt,
        permissions.roles.map { role -> EmployeeRole(role.id, role.name) },
    )

fun Employee.toDetailResponse() =
    EmployeeDetailResponse(
        id,
        name,
        avatarId,
        isOwner,
        isActive,
        joinedAt,
        permissions.roles.map { role -> EmployeeRole(role.id, role.name) },
        phoneNo,
        email?.value,
        permissions.grantedPermissions,
        permissions.revokedPermissions,
    )
