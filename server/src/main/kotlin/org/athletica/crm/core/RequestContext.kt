package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.SystemActor
import org.athletica.crm.core.permissions.SystemPermission
import org.athletica.crm.core.permissions.UserActor
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission

/**
 * Базовый контекст выполнения операции.
 * Содержит только поля, общие для всех типов actor'ов.
 *
 * Подтипы:
 * - [EmployeeRequestContext] — аутентифицированный HTTP-запрос от сотрудника
 * - [SystemRequestContext] — фоновая системная операция (cron, event handlers)
 * - [AdminRequestContext] — глобальный администратор Athletica (заглушка)
 */
sealed interface RequestContext {
    /** Язык пользователя или системы. */
    val lang: Lang

    /** Идентификатор организации, в контексте которой выполняется операция. */
    val orgId: OrgId

    /** Валюта организации; используется для построения [org.athletica.crm.core.money.Money] из БД и форматирования. */
    val currency: Currency
}

/**
 * Контекст аутентифицированного HTTP-запроса от сотрудника организации.
 *
 * [userId] — идентификатор пользователя из JWT-токена.
 * [branchId] — идентификатор текущего филиала из JWT-токена;
 *   [BranchId.notSelected] для bootstrap-эндпоинтов (`/auth/branches`), где филиал ещё не выбран.
 * [employeeId] — идентификатор сотрудника из JWT-токена.
 * [username] — имя пользователя из JWT-токена (денормализовано).
 * [clientIp] — IP-адрес клиента; IPv4 или IPv6; null если определить не удалось.
 * [availableBranches] — филиалы, доступные сотруднику ([EmployeeBranchAccess.All] либо
 *   [EmployeeBranchAccess.Selected]); загружается из агрегата `Employee` при сборке контекста.
 */
data class EmployeeRequestContext(
    override val lang: Lang,
    override val orgId: OrgId,
    override val currency: Currency,
    val userId: UserId,
    val branchId: BranchId,
    val employeeId: EmployeeId,
    val username: String,
    val clientIp: String?,
    val availableBranches: EmployeeBranchAccess = EmployeeBranchAccess.All,
    private val permission: EmployeePermission,
) : RequestContext,
    UserActor by permission

/**
 * Контекст системных фоновых операций (cron, event handlers, воркеры).
 * Не привязан к реальному пользователю или сотруднику.
 *
 * [branchId] — целевой филиал операции; null для org-wide операций.
 */
data class SystemRequestContext(
    override val lang: Lang = Lang.RU,
    override val orgId: OrgId,
    override val currency: Currency = Currency.RUB,
    val branchId: BranchId? = null,
) : RequestContext,
    SystemActor {
    override fun hasPermission(p: SystemPermission): Boolean = true
}

/**
 * Контекст глобального администратора системы Athletica.
 * Имеет все пользовательские и системные права.
 * Заглушка — реальные endpoint'ы и factory-функции появятся позже.
 *
 * [adminId] — идентификатор администратора.
 * [username] — имя администратора.
 * [clientIp] — IP-адрес клиента; null если определить не удалось.
 */
data class AdminRequestContext(
    override val lang: Lang,
    override val orgId: OrgId,
    override val currency: Currency,
    val adminId: UserId,
    val username: String,
    val clientIp: String?,
) : RequestContext,
    UserActor,
    SystemActor {
    override fun hasPermission(p: UserPermission): Boolean = true

    override fun hasPermission(p: SystemPermission): Boolean = true
}
