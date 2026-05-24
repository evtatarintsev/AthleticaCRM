package org.athletica.crm.core

import org.athletica.crm.core.entityids.BranchId

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
