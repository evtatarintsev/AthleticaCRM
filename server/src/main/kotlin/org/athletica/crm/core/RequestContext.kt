package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.permissions.Actor
import org.athletica.crm.domain.employees.EmployeePermission

/**
 * Контекст аутентифицированного HTTP-запроса.
 *
 * [lang] — язык из заголовка `Accept-Language`.
 * [userId] — идентификатор пользователя из JWT-токена.
 * [orgId] — идентификатор организации из JWT-токена.
 * [branchId] — идентификатор текущего филиала из JWT-токена.
 * [employeeId] — идентификатор сотрудника из JWT-токена.
 * [username] — имя пользователя из JWT-токена (денормализовано).
 * [clientIp] — IP-адрес клиента; IPv4 или IPv6; null если определить не удалось.
 */
data class RequestContext(
    val lang: Lang,
    val userId: UserId,
    val orgId: OrgId,
    val branchId: BranchId,
    val employeeId: EmployeeId,
    val username: String,
    val clientIp: String?,
    private val permission: EmployeePermission,
) : Actor by permission
