package org.athletica.crm.assistant

import arrow.core.raise.Raise
import org.athletica.crm.Di
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.testName
import kotlin.uuid.Uuid

/** Создаёт настоящую организацию и контекст её владельца. */
context(raise: Raise<AppError>)
suspend fun assistantOwner(di: Di, timezone: String = "UTC"): EmployeeRequestContext =
    di.database.transaction {
        val owner =
            di.auth.signup(
                SignUpRequest(testName("Org"), testName("Owner"), "${Uuid.random()}@example.com", "password123", timezone, Currency.RUB),
                Lang.RU,
                null,
            )
        val employee = context(SystemContexts.forOrg(owner.orgId)) { di.employees.byId(owner.employeeId) }
        EmployeeRequestContext(Lang.RU, owner.orgId, Currency.RUB, owner.id, owner.branchId, owner.username, null, employee)
    }
