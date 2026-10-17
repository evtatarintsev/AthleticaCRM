package org.athletica.crm.domain.attendancecontrol

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Настройки контроля посещаемости организации: напоминание о незакрытых журналах занятий. */
interface AttendanceControl {
    /** Создавать ли задачу «Закрыть журнал» по занятию, журнал которого не закрыт вовремя. */
    val overdueTaskEnabled: Boolean

    /** Сколько времени после окончания занятия даётся на закрытие журнала до создания задачи. */
    val overdueTaskDelay: JournalCloseDelay

    /** Настройки с новыми значениями [enabled] и [delay]; сохраняются отдельно через [save]. */
    fun withNew(enabled: Boolean, delay: JournalCloseDelay): AttendanceControl

    /** Сохраняет настройки организации из [ctx]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    companion object {
        /** Задача по незакрытому журналу создаётся по умолчанию. */
        const val DEFAULT_OVERDUE_TASK_ENABLED = true
    }
}

/** Репозиторий настроек контроля посещаемости. */
interface AttendanceControls {
    /** Настройки организации из [ctx]; если их ещё не сохраняли — значения по умолчанию. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun current(): AttendanceControl
}
