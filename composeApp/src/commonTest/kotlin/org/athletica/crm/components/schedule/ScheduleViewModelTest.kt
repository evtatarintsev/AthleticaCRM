package org.athletica.crm.components.schedule

import arrow.core.Either
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.api.schemas.schedule.ScheduleListRequest
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse
import org.athletica.crm.core.entityids.HallId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты загрузки расписания: перезапрос при смене недели и фильтров, однократная загрузка справочников. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    /** Среда недели 16–22 марта 2026. */
    private val today = LocalDate(2026, 3, 18)
    private val monday = LocalDate(2026, 3, 16)

    /** Источник, запоминающий запросы занятий и число загрузок справочников. */
    private class RecordingSource(
        private val sessionsResult: Either<ApiClientError, ScheduleListResponse> = Either.Right(ScheduleListResponse(emptyList())),
        private val dictionariesGate: CompletableDeferred<Unit>? = null,
    ) : ScheduleSource {
        val requests = mutableListOf<ScheduleListRequest>()
        var dictionaryLoads = 0

        override suspend fun sessions(request: ScheduleListRequest): Either<ApiClientError, ScheduleListResponse> {
            requests += request
            return sessionsResult
        }

        override suspend fun dictionaries(): ScheduleDictionaries {
            dictionaryLoads++
            dictionariesGate?.await()
            return ScheduleDictionaries(halls = listOf(HallDetailResponse(HallId.new(), "Зал")))
        }
    }

    @Test
    fun `при открытии загружает текущую неделю и справочники`() =
        runTest(UnconfinedTestDispatcher()) {
            val source = RecordingSource()
            val vm = ScheduleViewModel(source, backgroundScope, today)

            assertEquals(listOf(ScheduleListRequest(from = monday, to = LocalDate(2026, 3, 22))), source.requests)
            assertEquals(1, source.dictionaryLoads)
            assertEquals(1, vm.state.dictionaries.halls.size)
            assertIs<ScheduleLoadState.Loaded>(vm.state.data)
        }

    @Test
    fun `переключение недели перезапрашивает с теми же фильтрами без повторной загрузки справочников`() =
        runTest(UnconfinedTestDispatcher()) {
            val source = RecordingSource()
            val vm = ScheduleViewModel(source, backgroundScope, today)
            val hall = HallId.new()

            vm.onFiltersChange(ScheduleFilters(hallIds = setOf(hall)))
            vm.onNextWeek()
            vm.onPreviousWeek()
            vm.onPreviousWeek()

            assertEquals(
                listOf(
                    ScheduleListRequest(monday, LocalDate(2026, 3, 22)),
                    ScheduleListRequest(monday, LocalDate(2026, 3, 22), hallIds = listOf(hall)),
                    ScheduleListRequest(LocalDate(2026, 3, 23), LocalDate(2026, 3, 29), hallIds = listOf(hall)),
                    ScheduleListRequest(monday, LocalDate(2026, 3, 22), hallIds = listOf(hall)),
                    ScheduleListRequest(LocalDate(2026, 3, 9), LocalDate(2026, 3, 15), hallIds = listOf(hall)),
                ),
                source.requests,
            )
            assertEquals(1, source.dictionaryLoads)
            assertEquals(setOf(hall), vm.state.filters.hallIds)
        }

    @Test
    fun `справочники, пришедшие после занятий, не откатывают загруженные данные и фильтры`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val source = RecordingSource(dictionariesGate = gate)
            val vm = ScheduleViewModel(source, backgroundScope, today)
            val hall = HallId.new()
            vm.onFiltersChange(ScheduleFilters(hallIds = setOf(hall)))
            assertIs<ScheduleLoadState.Loaded>(vm.state.data)

            gate.complete(Unit)

            assertIs<ScheduleLoadState.Loaded>(vm.state.data)
            assertEquals(setOf(hall), vm.state.filters.hallIds)
            assertEquals(1, vm.state.dictionaries.halls.size)
        }

    @Test
    fun `ошибка загрузки и повтор`() =
        runTest(UnconfinedTestDispatcher()) {
            val source = RecordingSource(Either.Left(ApiClientError.Unavailable(IllegalStateException("down"))))
            val vm = ScheduleViewModel(source, backgroundScope, today)
            assertIs<ScheduleLoadState.Error>(vm.state.data)

            vm.onRetry()

            assertEquals(2, source.requests.size)
            assertEquals(source.requests[0], source.requests[1])
        }

    @Test
    fun `устаревший ответ не перезаписывает состояние новой недели`() {
        val state = ScheduleState.forWeekOf(today)
        val staleRequest = state.request
        val next = state.shiftedBy(1)

        val result = next.withResult(staleRequest, ScheduleLoadState.Loaded(emptyList()))

        assertEquals(ScheduleLoadState.Loading, result.data)
    }
}
