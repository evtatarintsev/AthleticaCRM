package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Метка посещаемости из справочника организации: уточняет отметку
 * («опоздал», «болеет») и применима только к своему состоянию отметки.
 */
interface AttendanceLabel {
    /** Идентификатор метки. */
    val id: AttendanceLabelId

    /** Название метки; уникально в пределах организации без учёта регистра. */
    val name: String

    /** Состояние отметки, к которому метка применима; после создания не меняется. */
    val scope: AttendanceLabelScope

    /** Порядок метки в справочнике. */
    val position: Int

    /** Метка архивирована: не назначается новым отметкам, но остаётся на проставленных. */
    val isArchived: Boolean

    /** Применима ли метка к присутствию [presence]. */
    fun appliesTo(presence: AttendancePresence): Boolean = scope.appliesTo(presence)

    /** Копия метки с названием [name] (без записи в БД). */
    fun renamed(name: String): AttendanceLabel

    /** Копия метки с порядком [position] (без записи в БД). */
    fun movedTo(position: Int): AttendanceLabel

    /** Архивированная копия метки (без записи в БД). */
    fun archived(): AttendanceLabel

    /** Восстановленная из архива копия метки (без записи в БД). */
    fun restored(): AttendanceLabel

    /**
     * Сохраняет метку: INSERT при создании либо UPDATE существующей.
     * Ошибка `ATTENDANCE_LABEL_ALREADY_EXISTS`, если название занято в организации,
     * и `ATTENDANCE_LABEL_NAME_BLANK` для пустого названия.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()
}
