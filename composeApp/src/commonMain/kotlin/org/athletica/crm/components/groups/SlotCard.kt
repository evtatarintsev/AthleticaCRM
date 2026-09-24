package org.athletica.crm.components.groups

import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.HallId
import kotlin.jvm.JvmInline

/** Локальный идентификатор карточки внутри редактора расписания; на сервер не передаётся. */
@JvmInline
value class SlotCardId(val value: Int)

/**
 * Карточка редактора расписания: одно занятие, повторяющееся в несколько дней недели.
 * Каждый день из [days] соответствует одному слоту расписания с временем и залом карточки.
 */
data class SlotCard(
    /** Идентификатор карточки в редакторе. */
    val id: SlotCardId,
    /** Дни недели, в которые проходит занятие. */
    val days: Set<DayOfWeek>,
    /** Время начала занятия. */
    val startAt: LocalTime,
    /** Время окончания занятия. */
    val endAt: LocalTime,
    /** Зал занятия; `null`, пока зал не выбран. */
    val hallId: HallId?,
)

/** Причина, по которой карточку нельзя сохранить. */
sealed interface SlotCardError {
    /** Не отмечено ни одного дня недели. */
    data object NoDays : SlotCardError

    /** Не выбран зал. */
    data object NoHall : SlotCardError

    /** Время окончания не позже времени начала. */
    data object EndNotAfterStart : SlotCardError

    /** День [day] с временем начала [startAt] встречается ещё в другой карточке. */
    data class Duplicate(
        /** Повторяющийся день недели. */
        val day: DayOfWeek,
        /** Повторяющееся время начала. */
        val startAt: LocalTime,
    ) : SlotCardError
}

/**
 * Собирает слоты с одинаковыми временем начала, временем окончания и залом в одну карточку.
 * Карточки упорядочены по первому дню недели, затем по времени начала; идентификаторы
 * назначаются по порядку, начиная с нуля.
 */
fun List<ScheduleSlot>.toCards(): List<SlotCard> =
    groupBy { Triple(it.startAt, it.endAt, it.hallId) }
        .map { (key, slots) -> key to slots.map { it.dayOfWeek }.toSet() }
        .sortedWith(compareBy({ (_, days) -> days.minOf { it.ordinal } }, { (key, _) -> key.first }))
        .mapIndexed { index, (key, days) ->
            SlotCard(
                id = SlotCardId(index),
                days = days,
                startAt = key.first,
                endAt = key.second,
                hallId = key.third,
            )
        }

/** Разворачивает карточки в слоты расписания по одному на каждый день; карточки без зала пропускаются. */
fun List<SlotCard>.toSlots(): List<ScheduleSlot> =
    flatMap { card ->
        val hallId = card.hallId ?: return@flatMap emptyList()
        card.days.sortedBy { it.ordinal }.map { day ->
            ScheduleSlot(dayOfWeek = day, startAt = card.startAt, endAt = card.endAt, hallId = hallId)
        }
    }
