package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [AttendanceLabels], оборачивающий выдаваемые метки в [AuditAttendanceLabel]. */
class AuditAttendanceLabels(private val delegate: AttendanceLabels, private val audit: AuditLog) : AttendanceLabels by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(includeArchived: Boolean) = delegate.list(includeArchived).map { AuditAttendanceLabel(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: AttendanceLabelId, name: String, scope: AttendanceLabelScope) = AuditAttendanceLabel(delegate.new(id, name, scope), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: AttendanceLabelId) = AuditAttendanceLabel(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: Collection<AttendanceLabelId>) = delegate.byIds(ids).map { AuditAttendanceLabel(it, audit) }
}

/** Декоратор [AttendanceLabel], записывающий сохранение метки в журнал аудита. */
class AuditAttendanceLabel(private val delegate: AttendanceLabel, private val audit: AuditLog) : AttendanceLabel by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate(
                "attendance_label",
                id,
                Json.encodeToString(AttendanceLabelAuditData(name, scope, position, isArchived)),
            )
        }

    override fun renamed(name: String) = AuditAttendanceLabel(delegate.renamed(name), audit)

    override fun movedTo(position: Int) = AuditAttendanceLabel(delegate.movedTo(position), audit)

    override fun archived() = AuditAttendanceLabel(delegate.archived(), audit)

    override fun restored() = AuditAttendanceLabel(delegate.restored(), audit)
}

/** Снимок метки для журнала аудита. */
@Serializable
private data class AttendanceLabelAuditData(
    /** Название метки. */
    val name: String,
    /** Применимость метки. */
    val scope: AttendanceLabelScope,
    /** Порядок метки. */
    val position: Int,
    /** Признак архивности. */
    val isArchived: Boolean,
)
