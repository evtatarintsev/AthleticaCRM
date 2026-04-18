package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.domain.auth.Users
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.employees.employeeList

context(db: Database, orgEmails: OrgEmails, users: Users)
fun Route.employeesRoutes(employees: Employees) {
    route("/employees") {
        getWithContext("/list") {
            call.eitherToResponse {
                val employees = employeeList().bind()
                EmployeeListResponse(employees, employees.size.toUInt())
            }
        }

        postWithContext("/create") {
            call.eitherToResponse {
                val request = call.receive<CreateEmployeeRequest>()
                db.transaction {
                    employees
                        .new(request.id, request.name, request.phoneNo, request.email, request.avatarId)
                        .also {
                            EmployeeListItem(
                                it.id,
                                it.name,
                                it.avatarId,
                                it.isOwner,
                                it.isActive,
                                it.joinedAt,
                                it.roles.map { role -> EmployeeRole(role.id, role.name) },
                            )
                        }
                }
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
    }
}
