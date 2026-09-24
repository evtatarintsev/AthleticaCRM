package org.athletica.crm.components.groups

import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.DayOfWeek.FRIDAY
import org.athletica.crm.core.DayOfWeek.MONDAY
import org.athletica.crm.core.DayOfWeek.SATURDAY
import org.athletica.crm.core.DayOfWeek.WEDNESDAY
import org.athletica.crm.core.entityids.HallId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты соответствия карточек редактора и слотов расписания. */
class SlotCardTest {
    private val hallA = HallId.new()
    private val hallB = HallId.new()
    private val weekdays = DayOfWeek.entries.take(5).toSet()

    private fun slot(
        day: DayOfWeek,
        start: Int,
        end: Int,
        hall: HallId,
    ) = ScheduleSlot(dayOfWeek = day, startAt = LocalTime(start, 0), endAt = LocalTime(end, 0), hallId = hall)

    private fun card(
        id: Int,
        days: Set<DayOfWeek>,
        start: Int,
        end: Int,
        hall: HallId?,
    ) = SlotCard(SlotCardId(id), days, LocalTime(start, 0), LocalTime(end, 0), hall)

    @Test
    fun `одинаковые время и зал собираются в одну карточку`() {
        val cards =
            listOf(slot(MONDAY, 15, 17, hallA), slot(WEDNESDAY, 15, 17, hallA), slot(SATURDAY, 10, 12, hallB))
                .toCards()

        assertEquals(
            listOf(card(0, setOf(MONDAY, WEDNESDAY), 15, 17, hallA), card(1, setOf(SATURDAY), 10, 12, hallB)),
            cards,
        )
    }

    @Test
    fun `разный зал даёт разные карточки`() {
        val cards = listOf(slot(MONDAY, 15, 17, hallA), slot(WEDNESDAY, 15, 17, hallB)).toCards()

        assertEquals(2, cards.size)
    }

    @Test
    fun `карточки упорядочены по первому дню, затем по времени начала`() {
        val slots = weekdays.flatMap { listOf(slot(it, 18, 19, hallA), slot(it, 9, 10, hallA)) }

        val cards = slots.toCards()

        assertEquals(listOf(LocalTime(9, 0), LocalTime(18, 0)), cards.map { it.startAt })
    }

    @Test
    fun `разворачивание карточек возвращает исходный набор слотов`() {
        val slots =
            listOf(
                slot(MONDAY, 15, 17, hallA),
                slot(WEDNESDAY, 15, 17, hallA),
                slot(FRIDAY, 15, 17, hallA),
                slot(SATURDAY, 10, 12, hallB),
                slot(MONDAY, 9, 10, hallB),
            )

        assertEquals(slots.toSet(), slots.toCards().toSlots().toSet())
    }

    @Test
    fun `карточка на три дня даёт три слота`() {
        val slots = listOf(card(0, setOf(MONDAY, WEDNESDAY, FRIDAY), 15, 17, hallA)).toSlots()

        assertEquals(
            listOf(slot(MONDAY, 15, 17, hallA), slot(WEDNESDAY, 15, 17, hallA), slot(FRIDAY, 15, 17, hallA)),
            slots,
        )
    }

    @Test
    fun `две карточки по будням дают десять слотов`() {
        val slots = listOf(card(0, weekdays, 9, 10, hallA), card(1, weekdays, 18, 19, hallA)).toSlots()

        assertEquals(10, slots.size)
    }

    @Test
    fun `карточка без зала не разворачивается`() {
        assertEquals(emptyList(), listOf(card(0, setOf(MONDAY), 9, 10, null)).toSlots())
    }
}
