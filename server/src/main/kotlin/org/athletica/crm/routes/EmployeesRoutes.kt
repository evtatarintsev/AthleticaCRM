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
import org.athletica.crm.domain.employees.EmployeePermissionSelection
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
                employees
                    .new(
                        id = request.id,
                        name = request.name,
                        phoneNo = request.phoneNo,
                        email = request.email,
                        avatarId = request.avatarId,
                        permissions = EmployeePermissionSelection(request.roleIds, request.grantedPermissions, request.revokedPermissions),
                        availableBranches = request.toAvailableBranches(),
                    ).toListItem()
            }
        }

        post<UpdateEmployeeRequest, Unit>("/update") { request ->
            db.transaction {
                employees
                    .byId(request.id)
                    .withNew(
                        newName = request.name,
                        newPhoneNo = request.phoneNo,
                        newEmail = request.email,
                        newAvatarId = request.avatarId,
                        newPermissions = EmployeePermissionSelection(request.roleIds, request.grantedPermissions, request.revokedPermissions),
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

/** Собирает элемент списка из только что созданного сотрудника и назначенных ему ролей. */
private fun Employee.toListItem() =
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
