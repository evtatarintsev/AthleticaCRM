package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.api.schemas.groups.SetGroupScheduleRequest
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.core.entityids.GroupId

/**
 * Состояние диалога установки расписания группы.
 *
 * [effectiveFromText] хранится строкой, потому что дата вводится вручную и может быть
 * незавершённой; разобранное значение отдаёт [effectiveFrom].
 */
data class SetScheduleState(
    /** Слоты, которые будут действовать с даты вступления в силу. */
    val slots: List<ScheduleSlot> = emptyList(),
    /** Дата вступления в силу в формате `ГГГГ-ММ-ДД`. */
    val effectiveFromText: String = "",
    /** Залы филиала для выбора в редакторе слотов. */
    val halls: List<HallDetailResponse> = emptyList(),
    /** Ранее запланированное изменение расписания, если оно есть. */
    val plannedChangeAt: LocalDate? = null,
    /** Сохранение выполняется. */
    val isSaving: Boolean = false,
    /** Ошибка сохранения. */
    val error: GroupsApiError? = null,
) {
    /** Разобранная дата вступления в силу; `null` — сегодня либо ввод ещё не завершён. */
    val effectiveFrom: LocalDate? get() = runCatching { LocalDate.parse(effectiveFromText) }.getOrNull()

    /** Введена ли дата в недопустимом формате. */
    val isDateInvalid: Boolean get() = effectiveFromText.isNotBlank() && effectiveFrom == null

    /** Отменит ли сохранение ранее запланированное изменение. */
    val cancelsPlannedChange: Boolean
        get() = plannedChangeAt != null && (effectiveFrom == null || effectiveFrom!! <= plannedChangeAt)

    /** Можно ли отправлять форму. */
    val isValid: Boolean get() = !isDateInvalid && !isSaving

    fun withSlots(slots: List<ScheduleSlot>) = copy(slots = slots, error = null)

    fun withEffectiveFrom(text: String) = copy(effectiveFromText = text, error = null)

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
) {
    var state: SetScheduleState by mutableStateOf(
        SetScheduleState(slots = initialSlots, plannedChangeAt = plannedChangeAt),
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

    /** Меняет введённую дату вступления в силу. */
    fun onEffectiveFromChange(text: String) {
        state = state.withEffectiveFrom(text)
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
                        effectiveFrom = state.effectiveFrom,
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
