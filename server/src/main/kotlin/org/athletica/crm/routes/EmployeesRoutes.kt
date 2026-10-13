package org.athletica.crm.routes

import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.CreateRoleRequest
import org.athletica.crm.api.schemas.employees.EmployeeDetailRequest
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.api.schemas.employees.RoleListResponse
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.api.schemas.employees.UpdateEmployeeRequest
import org.athletica.crm.api.schemas.employees.UpdateRoleRequest
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.employees.Roles
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.domain.employees.EmployeeRole as DomainEmployeeRole

context(db: Database)
fun RouteWithContext.employeesRoutes(employees: Employees, roles: Roles, views: ReadViews) {
    route("/employees") {
        get<Unit, EmployeeListResponse>("/list") {
            db.transaction {
                views.employeeList.list()
            }
        }

        get<EmployeeDetailRequest, EmployeeDetailResponse>("/detail") { request ->
            db.transaction {
                views.employeeDetail.byId(request.id)
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
                    .new(
                        id = request.id,
                        name = request.name,
                        phoneNo = request.phoneNo,
                        email = request.email,
                        avatarId = request.avatarId,
                        permissions = permissions,
                        availableBranches = request.toAvailableBranches(),
                    ).toListItem(selectedRoles)
            }
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
                        newAvailableBranches = request.toAvailableBranches(),
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

        get<Unit, RoleListResponse>("/roles") {
            db.transaction {
                roles.list()
            }.let { roles ->
                RoleListResponse(roles.map { RoleItem(it.id, it.name, it.permissions) })
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

/** Собирает элемент списка из только что созданного сотрудника и назначенных ему ролей [roles]. */
private fun Employee.toListItem(roles: List<DomainEmployeeRole>) =
    EmployeeListItem(
        id,
        name,
        avatarId,
        isOwner,
        isActive,
        joinedAt,
        roles.map { role -> EmployeeRole(role.id, role.name) },
    )

/** Конвертирует [CreateEmployeeRequest] в доменный [EmployeeBranchAccess]. */
private fun CreateEmployeeRequest.toAvailableBranches(): EmployeeBranchAccess = if (allBranchesAccess) EmployeeBranchAccess.All else EmployeeBranchAccess.Selected(branchIds)

/** Конвертирует [UpdateEmployeeRequest] в доменный [EmployeeBranchAccess]. */
private fun UpdateEmployeeRequest.toAvailableBranches(): EmployeeBranchAccess = if (allBranchesAccess) EmployeeBranchAccess.All else EmployeeBranchAccess.Selected(branchIds)
