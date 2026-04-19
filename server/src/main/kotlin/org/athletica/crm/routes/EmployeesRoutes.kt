package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.CreateRoleRequest
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.api.schemas.employees.RoleListResponse
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeRole as DomainEmployeeRole
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.employees.Roles
import org.athletica.crm.storage.Database

context(db: Database)
fun Route.employeesRoutes(employees: Employees, roles: Roles) {
    route("/employees") {
        getWithContext("/list") {
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

        postWithContext("/create") {
            call.eitherToResponse {
                val request = call.receive<CreateEmployeeRequest>()
                db.transaction {
                    employees
                        .new(request.id, request.name, request.phoneNo, request.email, request.avatarId)
                }.toListItem()
            }
        }

        postWithContext("/send-access") {
            call.eitherToResponse {
                val request = call.receive<SendEmployeeAccessRequest>()
                db.transaction {
                    employees
                        .byId(request.employeeId)
                        .invite(request.email, request.password)
                }
            }
        }

        getWithContext("/roles") {
            call.eitherToResponse {
                db.transaction {
                    roles.list()
                }.let { roles ->
                    RoleListResponse(roles.map { RoleItem(it.id, it.name, it.permissions) })
                }
            }
        }

        postWithContext("/roles/create") {
            call.eitherToResponse {
                val request = call.receive<CreateRoleRequest>()
                db.transaction {
                    roles.new(DomainEmployeeRole(request.id, request.name, request.permissions))
                }
                RoleItem(request.id, request.name, request.permissions)
            }
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
