package org.athletica.crm.core

import org.athletica.crm.core.entityids.EmployeeId

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
