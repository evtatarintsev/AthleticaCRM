package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.api.schemas.groups.SetGroupScheduleRequest
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.core.entityids.GroupId
import kotlin.time.Clock

/**
 * Состояние диалога установки расписания группы.
 *
 * Дата вступления в силу [effectiveFrom] выбирается в календаре и не может быть раньше [today].
 */
data class SetScheduleState(
    /** Сегодняшняя дата на устройстве — нижняя граница выбора и значение по умолчанию. */
    val today: LocalDate,
    /** Дата вступления в силу. */
    val effectiveFrom: LocalDate = today,
    /** Карточки редактора; развёрнутые в слоты, они будут действовать с даты вступления в силу. */
    val cards: List<SlotCard> = emptyList(),
    /** Идентификатор, который получит следующая добавленная карточка. */
    val nextCardId: Int = cards.size,
    /** Залы филиала для выбора в редакторе слотов. */
    val halls: List<HallDetailResponse> = emptyList(),
    /** Ранее запланированное изменение расписания, если оно есть. */
    val plannedChangeAt: LocalDate? = null,
    /** Сохранение выполняется. */
    val isSaving: Boolean = false,
    /** Ошибка сохранения. */
    val error: GroupsApiError? = null,
) {
    /** Отменит ли сохранение ранее запланированное изменение. */
    val cancelsPlannedChange: Boolean
        get() = plannedChangeAt != null && effectiveFrom <= plannedChangeAt

    /** Ошибки карточек по их идентификаторам; карточки без ошибок в словаре отсутствуют. */
    val cardErrors: Map<SlotCardId, List<SlotCardError>>
        get() {
            val duplicates =
                cards
                    .flatMap { card -> card.days.map { day -> Triple(day, card.startAt, card.id) } }
                    .groupBy({ (day, startAt, _) -> day to startAt }, { (_, _, id) -> id })
                    .filterValues { it.size > 1 }
            return cards
                .associate { card ->
                    card.id to
                        buildList {
                            if (card.days.isEmpty()) {
                                add(SlotCardError.NoDays)
                            }
                            if (card.hallId == null) {
                                add(SlotCardError.NoHall)
                            }
                            if (card.endAt <= card.startAt) {
                                add(SlotCardError.EndNotAfterStart)
                            }
                            duplicates
                                .filterValues { card.id in it }
                                .keys
                                .sortedWith(compareBy({ it.first.ordinal }, { it.second }))
                                .forEach { (day, startAt) -> add(SlotCardError.Duplicate(day, startAt)) }
                        }
                }.filterValues { it.isNotEmpty() }
        }

    /** Можно ли отправлять форму. */
    val isValid: Boolean get() = !isSaving && cardErrors.isEmpty()

    /**
     * Дата для запроса: `null`, если выбрано сегодня, — тогда сервер подставит своё «сегодня»
     * и не отклонит запрос из-за разницы часовых поясов устройства и сервера.
     */
    val requestedEffectiveFrom: LocalDate? get() = effectiveFrom.takeIf { it != today }

    /**
     * Добавляет карточку без выбранных дней. Зал подставляется, только если в [halls] ровно один зал;
     * время берётся из последней карточки, а без карточек — 00:00–01:00.
     */
    fun withCardAdded(halls: List<HallDetailResponse>): SetScheduleState {
        val last = cards.lastOrNull()
        val card =
            SlotCard(
                id = SlotCardId(nextCardId),
                days = emptySet(),
                startAt = last?.startAt ?: LocalTime(0, 0),
                endAt = last?.endAt ?: LocalTime(1, 0),
                hallId = halls.singleOrNull()?.id,
            )
        return copy(cards = cards + card, nextCardId = nextCardId + 1, error = null)
    }

    /** Заменяет карточку с идентификатором [card] её новой версией и сбрасывает ошибку. */
    fun withCardChanged(card: SlotCard) = copy(cards = cards.map { if (it.id == card.id) card else it }, error = null)

    /** Удаляет карточку с идентификатором [id] и сбрасывает ошибку. */
    fun withCardRemoved(id: SlotCardId) = copy(cards = cards.filterNot { it.id == id }, error = null)

    /** Устанавливает дату вступления в силу [date], не допуская даты раньше [today]. */
    fun withEffectiveFrom(date: LocalDate) = copy(effectiveFrom = maxOf(date, today), error = null)

    /** Устанавливает список залов [halls] для редактора слотов. */
    fun withHalls(halls: List<HallDetailResponse>) = copy(halls = halls)
}

/**
 * ViewModel диалога установки расписания группы.
 * Отправляет весь набор слотов вместе с датой вступления в силу и по успеху вызывает [onSaved].
 */
class SetScheduleViewModel(
    private val api: ApiClient,
    private val groupId: GroupId,
    private val scope: CoroutineScope,
    initialSlots: List<ScheduleSlot>,
    plannedChangeAt: LocalDate?,
    private val onSaved: () -> Unit,
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
) {
    var state: SetScheduleState by mutableStateOf(
        SetScheduleState(today = today, cards = initialSlots.toCards(), plannedChangeAt = plannedChangeAt),
    )
        private set

    init {
        loadHalls()
    }

    /** Загружает залы филиала для выбора в слотах. */
    private fun loadHalls() {
        scope.launch {
            api.halls.list().fold(ifLeft = {}, ifRight = { state = state.withHalls(it.halls) })
        }
    }

    /** Добавляет новую карточку. */
    fun onCardAdd() {
        state = state.withCardAdded(state.halls)
    }

    /** Заменяет карточку её изменённой версией [card]. */
    fun onCardChange(card: SlotCard) {
        state = state.withCardChanged(card)
    }

    /** Удаляет карточку с идентификатором [id]. */
    fun onCardRemove(id: SlotCardId) {
        state = state.withCardRemoved(id)
    }

    /** Меняет дату вступления в силу на выбранную в календаре [date]. */
    fun onEffectiveFromChange(date: LocalDate) {
        state = state.withEffectiveFrom(date)
    }

    /** Отправляет расписание на сервер. */
    fun onSave() {
        if (!state.isValid) {
            return
        }
        scope.launch {
            state = state.copy(isSaving = true, error = null)
            api
                .groups
                .setSchedule(
                    SetGroupScheduleRequest(
                        groupId = groupId,
                        effectiveFrom = state.requestedEffectiveFrom,
                        slots = state.cards.toSlots(),
                    ),
                ).fold(
                    ifLeft = { state = state.copy(isSaving = false, error = it.toGroupsApiError()) },
                    ifRight = {
                        state = state.copy(isSaving = false)
                        onSaved()
                    },
                )
        }
    }
}
