package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.db.Database
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.employees.createEmployee
import org.athletica.crm.usecases.employees.employeeList
import org.athletica.crm.usecases.employees.sendEmployeeAccess
import org.athletica.infra.mail.Mailbox

context(db: Database, audit: AuditLog, passwordHasher: PasswordHasher, mailbox: Mailbox)
fun Route.employeesRoutes() {
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
                createEmployee(request).bind()
            }
        }

        postWithContext("/send-access") {
            call.eitherToResponse {
                val request = call.receive<SendEmployeeAccessRequest>()
                sendEmployeeAccess(request).bind()
            }
        }
    }
}
