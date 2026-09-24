package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
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
    /** Слоты, которые будут действовать с даты вступления в силу. */
    val slots: List<ScheduleSlot> = emptyList(),
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

    /** Можно ли отправлять форму. */
    val isValid: Boolean get() = !isSaving

    /**
     * Дата для запроса: `null`, если выбрано сегодня, — тогда сервер подставит своё «сегодня»
     * и не отклонит запрос из-за разницы часовых поясов устройства и сервера.
     */
    val requestedEffectiveFrom: LocalDate? get() = effectiveFrom.takeIf { it != today }

    /** Заменяет набор слотов и сбрасывает ошибку. */
    fun withSlots(slots: List<ScheduleSlot>) = copy(slots = slots, error = null)

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
        SetScheduleState(today = today, slots = initialSlots, plannedChangeAt = plannedChangeAt),
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

    /** Заменяет набор слотов. */
    fun onSlotsChange(slots: List<ScheduleSlot>) {
        state = state.withSlots(slots)
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
                        slots = state.slots.map { it.copy(hallName = null, validity = null) },
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
