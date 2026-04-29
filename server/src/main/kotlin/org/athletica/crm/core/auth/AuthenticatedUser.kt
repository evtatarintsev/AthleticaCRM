package org.athletica.crm.core.auth

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId

/** Аутентифицированный пользователь системы. Реализуется разными доменными моделями (security, usecases). */
interface AuthenticatedUser {
    val id: UserId
    val orgId: OrgId
    val branchId: BranchId
    val employeeId: EmployeeId
    val username: String
}
