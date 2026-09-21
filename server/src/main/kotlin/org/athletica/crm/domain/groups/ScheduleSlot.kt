package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.time.Validity
import org.athletica.crm.i18n.Messages

/**
 * Версия правила расписания: группа занимается в [dayOfWeek] с [startAt] до [endAt]
 * в зале [hallId] на протяжении периода [validity].
 *
 * Версии не редактируются: изменение расписания закрывает текущую версию
 * и создаёт новую, поэтому история расписания сохраняется целиком.
 */
data class ScheduleSlot(
    /** Идентификатор версии слота; стабилен, на него ссылаются порождённые занятия. */
    val id: SlotId,
    /** Группа, расписанию которой принадлежит слот. */
    val groupId: GroupId,
    /** День недели. */
    val dayOfWeek: DayOfWeek,
    /** Время начала занятия. */
    val startAt: LocalTime,
    /** Время окончания занятия. */
    val endAt: LocalTime,
    /** Зал проведения занятия. */
    val hallId: HallId,
    /** Период действия версии. */
    val validity: Validity,
) {
    /** Совпадает ли слот с заданным правилом по всем атрибутам, кроме периода действия. */
    fun matches(slot: NewSlot): Boolean = dayOfWeek == slot.dayOfWeek && startAt == slot.startAt && endAt == slot.endAt && hallId == slot.hallId
}

/**
 * Правило расписания без периода действия и идентификатора — то, что задаёт человек,
 * устанавливая расписание группы. Период присваивается при сохранении.
 */
data class NewSlot(
    /** День недели. */
    val dayOfWeek: DayOfWeek,
    /** Время начала занятия. */
    val startAt: LocalTime,
    /** Время окончания занятия. */
    val endAt: LocalTime,
    /** Зал проведения занятия. */
    val hallId: HallId,
) {
    /** Ключ правила внутри расписания группы: день недели и время начала. */
    val key: Pair<DayOfWeek, LocalTime> get() = dayOfWeek to startAt

    /** Проверяет, что занятие заканчивается позже, чем начинается. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    fun validate() {
        if (endAt <= startAt) {
            raise(
                CommonDomainError(
                    "INVALID_SCHEDULE_TIME",
                    Messages.ScheduleEndBeforeStart.localize(ctx.lang, startAt, endAt),
                ),
            )
        }
    }
}
