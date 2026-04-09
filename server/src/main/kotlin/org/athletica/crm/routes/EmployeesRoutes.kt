package org.athletica.crm.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.employees.employeeList

context(db: Database)
fun Route.employeesRoutes() {
    route("/employees") {
        getWithContext("/list") {
            call.eitherToResponse {
                val employees = employeeList().bind()
                EmployeeListResponse(employees, employees.size.toUInt())
            }
        }
    }
}
