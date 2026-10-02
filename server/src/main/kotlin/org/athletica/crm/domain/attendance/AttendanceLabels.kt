package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Справочник меток посещаемости организации. */
interface AttendanceLabels {
    /** Метки организации по порядку; архивные — только при [includeArchived]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(includeArchived: Boolean): List<AttendanceLabel>

    /**
     * Создаёт несохранённую метку [name] с применимостью [scope] в конце справочника;
     * запись в БД — через [AttendanceLabel.save].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(id: AttendanceLabelId, name: String, scope: AttendanceLabelScope): AttendanceLabel

    /** Метка по идентификатору, включая архивные; ошибка `ATTENDANCE_LABEL_NOT_FOUND`, если её нет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: AttendanceLabelId): AttendanceLabel

    /**
     * Метки по идентификаторам, включая архивные. Дубликаты в [ids] игнорируются;
     * ошибка `ATTENDANCE_LABEL_NOT_FOUND`, если хотя бы одной нет.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: Collection<AttendanceLabelId>): List<AttendanceLabel>
}
