package org.athletica.crm

import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.EmployeeStub
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission

/**
 * Собирает контекст запроса сотрудника для тестов. Сотрудник — [EmployeeStub] с идентификатором
 * [employeeId], именем [employeeName], настройкой прав [permissions] и доступом к филиалам
 * [availableBranches]; запрос выполняется в организации [orgId] и филиале [branchId].
 * Сессионные поля ([userId], [username], [clientIp]), язык [lang] и валюта [currency]
 * получают значения по умолчанию, которые тест может переопределить.
 *
 * Единственное место в тестах, которое создаёт [EmployeeRequestContext] напрямую:
 * смена состава контекста правит только эту функцию.
 */
fun employeeContext(
    orgId: OrgId,
    branchId: BranchId = BranchId.new(),
    employeeId: EmployeeId = EmployeeId.new(),
    permissions: EmployeePermission = EmployeePermission(),
    availableBranches: EmployeeBranchAccess = EmployeeBranchAccess.All,
    employeeName: String = "Сотрудник",
    userId: UserId = UserId.new(),
    username: String = "test@example.com",
    lang: Lang = Lang.RU,
    clientIp: String? = null,
    currency: Currency = Currency.RUB,
): EmployeeRequestContext {
    val employee =
        EmployeeStub(
            id = employeeId,
            userId = userId,
            name = employeeName,
            permissionSettings = permissions,
            availableBranches = availableBranches,
        )
    return EmployeeRequestContext(
        lang = lang,
        orgId = orgId,
        currency = currency,
        userId = userId,
        branchId = branchId,
        username = username,
        clientIp = clientIp,
        employee = employee,
    )
}
