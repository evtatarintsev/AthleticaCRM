package org.athletica.crm.components.groups

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.core.DayOfWeek.MONDAY
import org.athletica.crm.core.DayOfWeek.WEDNESDAY
import org.athletica.crm.core.entityids.HallId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Тесты переходов состояния диалога установки расписания группы. */
class SetScheduleStateTest {
    private val today = LocalDate(2026, 9, 24)
    private val hallA = HallDetailResponse(HallId.new(), "Большой")
    private val hallB = HallDetailResponse(HallId.new(), "Малый")

    private fun card(
        id: Int,
        vararg days: org.athletica.crm.core.DayOfWeek,
        start: Int = 15,
        end: Int = 17,
        hall: HallId? = hallA.id,
    ) = SlotCard(SlotCardId(id), days.toSet(), LocalTime(start, 0), LocalTime(end, 0), hall)

    private fun stateWith(vararg cards: SlotCard) = SetScheduleState(today = today, cards = cards.toList())

    @Test
    fun `по умолчанию дата вступления в силу — сегодня и в запрос не передаётся`() {
        val state = SetScheduleState(today = today)

        assertEquals(today, state.effectiveFrom)
        assertNull(state.requestedEffectiveFrom)
    }

    @Test
    fun `выбранная будущая дата передаётся в запрос`() {
        val future = LocalDate(2026, 10, 1)
        val state = SetScheduleState(today = today).withEffectiveFrom(future)

        assertEquals(future, state.requestedEffectiveFrom)
    }

    @Test
    fun `дата в прошлом заменяется сегодняшней`() {
        val state = SetScheduleState(today = today).withEffectiveFrom(LocalDate(2026, 9, 1))

        assertEquals(today, state.effectiveFrom)
    }

    @Test
    fun `дата не позже запланированного изменения отменяет его`() {
        val planned = LocalDate(2026, 10, 1)
        val state = SetScheduleState(today = today, plannedChangeAt = planned)

        assertTrue(state.cancelsPlannedChange)
        assertTrue(state.withEffectiveFrom(planned).cancelsPlannedChange)
        assertFalse(state.withEffectiveFrom(LocalDate(2026, 10, 2)).cancelsPlannedChange)
    }

    @Test
    fun `добавленная карточка получает новый идентификатор и не отмечает дни`() {
        val state = stateWith(card(0, MONDAY)).withCardAdded(listOf(hallA, hallB))

        val added = state.cards.last()
        assertEquals(SlotCardId(1), added.id)
        assertTrue(added.days.isEmpty())
        assertEquals(2, state.nextCardId)
    }

    @Test
    fun `идентификатор не переиспользуется после удаления`() {
        val state = stateWith(card(0, MONDAY), card(1, WEDNESDAY)).withCardRemoved(SlotCardId(1)).withCardAdded(listOf(hallA))

        assertEquals(listOf(SlotCardId(0), SlotCardId(2)), state.cards.map { it.id })
    }

    @Test
    fun `изменение и удаление затрагивают только карточку с указанным идентификатором`() {
        val state = stateWith(card(0, MONDAY), card(1, WEDNESDAY))

        val changed = state.withCardChanged(card(1, WEDNESDAY, start = 18, end = 19))
        assertEquals(listOf(card(0, MONDAY), card(1, WEDNESDAY, start = 18, end = 19)), changed.cards)

        assertEquals(listOf(card(1, WEDNESDAY)), state.withCardRemoved(SlotCardId(0)).cards)
    }

    @Test
    fun `при единственном зале он подставляется в новую карточку`() {
        val state = stateWith().withCardAdded(listOf(hallA))

        assertEquals(hallA.id, state.cards.single().hallId)
    }

    @Test
    fun `при нескольких залах зал новой карточки не выбран`() {
        val state = stateWith().withCardAdded(listOf(hallA, hallB))

        assertNull(state.cards.single().hallId)
    }

    @Test
    fun `новая карточка берёт время последней, а без карточек — с полуночи на час`() {
        val first = stateWith().withCardAdded(listOf(hallA)).cards.single()
        assertEquals(LocalTime(0, 0) to LocalTime(1, 0), first.startAt to first.endAt)

        val next = stateWith(card(0, MONDAY, start = 18, end = 19)).withCardAdded(listOf(hallA)).cards.last()
        assertEquals(LocalTime(18, 0) to LocalTime(19, 0), next.startAt to next.endAt)
    }

    @Test
    fun `карточка без дней не проходит проверку`() {
        val state = stateWith(card(0))

        assertEquals(mapOf(SlotCardId(0) to listOf(SlotCardError.NoDays)), state.cardErrors)
        assertFalse(state.isValid)
    }

    @Test
    fun `карточка без зала не проходит проверку`() {
        assertEquals(mapOf(SlotCardId(0) to listOf(SlotCardError.NoHall)), stateWith(card(0, MONDAY, hall = null)).cardErrors)
    }

    @Test
    fun `время окончания не позже начала не проходит проверку`() {
        assertEquals(
            mapOf(SlotCardId(0) to listOf(SlotCardError.EndNotAfterStart)),
            stateWith(card(0, MONDAY, start = 17, end = 15)).cardErrors,
        )
        assertEquals(
            mapOf(SlotCardId(0) to listOf(SlotCardError.EndNotAfterStart)),
            stateWith(card(0, MONDAY, start = 15, end = 15)).cardErrors,
        )
    }

    @Test
    fun `повтор дня и времени начала помечает обе карточки`() {
        val state = stateWith(card(0, MONDAY, WEDNESDAY), card(1, MONDAY, end = 16))

        val duplicate = listOf(SlotCardError.Duplicate(MONDAY, LocalTime(15, 0)))
        assertEquals(mapOf(SlotCardId(0) to duplicate, SlotCardId(1) to duplicate), state.cardErrors)
        assertFalse(state.isValid)
    }

    @Test
    fun `одно время в разные дни — не повтор`() {
        assertTrue(stateWith(card(0, MONDAY), card(1, WEDNESDAY)).isValid)
    }

    @Test
    fun `пустое расписание можно сохранить`() {
        assertTrue(stateWith().isValid)
    }
}
