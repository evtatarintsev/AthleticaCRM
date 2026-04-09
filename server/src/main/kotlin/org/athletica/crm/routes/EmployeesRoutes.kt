package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.employees.createEmployee
import org.athletica.crm.usecases.employees.employeeList

context(db: Database, audit: AuditLog)
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
    }
}
