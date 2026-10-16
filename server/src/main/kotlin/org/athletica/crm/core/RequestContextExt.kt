package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId

/**
 * Возвращает [BranchId] из контекста, если тип контекста его содержит.
 *
 * - [EmployeeRequestContext] — текущий филиал сотрудника из JWT-токена.
 * - [SystemRequestContext] — целевой филиал операции; null для org-wide задач.
 * - [AdminRequestContext] — всегда null (администратор работает поверх org).
 */
val RequestContext.branchIdOrNull: BranchId?
    get() =
        when (this) {
            is EmployeeRequestContext -> branchId
            is SystemRequestContext -> branchId
            is AdminRequestContext -> null
        }

/**
 * Сотрудник, от имени которого выполняется операция; `null` — действие системы.
 *
 * - [EmployeeRequestContext] — сотрудник запроса.
 * - [SystemRequestContext] и [AdminRequestContext] — `null`: автор не сотрудник организации.
 */
val RequestContext.actingEmployeeId: EmployeeId?
    get() =
        when (this) {
            is EmployeeRequestContext -> employee.id
            is SystemRequestContext -> null
            is AdminRequestContext -> null
        }
