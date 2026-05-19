package org.athletica.crm.ui.list

import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.athletica.crm.api.client.ApiClientError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Тестовый элемент списка. */
private data class TestItem(val name: String, val weight: Int)

/** Тестовый фильтр. */
private data class TestFilter(val minWeight: Int = 0)

/** Конкретная тестовая ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
private class TestListPageViewModel(
    scope: TestScope,
    private val fetchResult: List<TestItem> =
        listOf(
            TestItem("Алексеев Иван", 80),
            TestItem("Борисова Мария", 60),
            TestItem("Иванов Пётр", 90),
        ),
) : ListPageViewModel<TestItem, TestFilter>(scope) {
    override fun defaultFilter(): TestFilter = TestFilter()

    override fun defaultSort(): SortState? = null

    override suspend fun fetch(): Either<ApiClientError, List<TestItem>> = Either.Right(fetchResult)

    override fun matches(
        item: TestItem,
        query: String,
        filter: TestFilter,
    ): Boolean =
        item.weight >= filter.minWeight &&
            (query.isEmpty() || item.name.contains(query, ignoreCase = true))

    override fun compare(
        a: TestItem,
        b: TestItem,
        sort: SortState,
    ): Int =
        when (sort.columnId.value) {
            "weight" ->
                if (sort.direction == SortDirection.Asc) {
                    a.weight - b.weight
                } else {
                    b.weight - a.weight
                }
            else -> a.name.compareTo(b.name)
        }

    /** Прямое выставление состояния для синхронных тестов фильтрации. */
    fun forceLoaded(items: List<TestItem>) {
        state = ListState.Loaded(items)
    }
}

/** Тесты для [ListPageViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListPageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `load устанавливает состояние Loaded с данными`() =
        runTest(dispatcher) {
            val vm = TestListPageViewModel(scope)
            vm.load()
            dispatcher.scheduler.advanceUntilIdle()
            assertIs<ListState.Loaded<TestItem>>(vm.state)
            assertEquals(3, (vm.state as ListState.Loaded).items.size)
        }

    @Test
    fun `setSearch фильтрует по имени`() {
        val vm = TestListPageViewModel(scope)
        vm.forceLoaded(
            listOf(
                TestItem("Алексеев Иван", 80),
                TestItem("Борисова Мария", 60),
            ),
        )
        vm.setSearch("ив")
        assertEquals(1, vm.visible.size)
        assertEquals("Алексеев Иван", vm.visible.first().name)
    }

    @Test
    fun `updateFilter пересчитывает visible и сбрасывает activeSavedViewId`() {
        val vm = TestListPageViewModel(scope)
        vm.forceLoaded(
            listOf(
                TestItem("Алексеев Иван", 80),
                TestItem("Борисова Мария", 60),
            ),
        )
        vm.applySystemView(
            SystemSavedView(
                id = SavedViewId.system("test"),
                filter = TestFilter(minWeight = 0),
            ),
        )
        assertEquals(SavedViewId.system("test"), vm.activeSavedViewId)

        vm.updateFilter(TestFilter(minWeight = 70))
        assertEquals(1, vm.visible.size)
        assertEquals("Алексеев Иван", vm.visible.first().name)
        assertNull(vm.activeSavedViewId)
    }

    @Test
    fun `cycleSort сортирует список по весу`() {
        val vm = TestListPageViewModel(scope)
        vm.forceLoaded(
            listOf(
                TestItem("А", 80),
                TestItem("Б", 60),
                TestItem("В", 90),
            ),
        )
        val weightCol = ColumnId("weight")
        vm.cycleSort(weightCol)
        assertEquals(listOf(60, 80, 90), vm.visible.map { it.weight })

        vm.cycleSort(weightCol)
        assertEquals(listOf(90, 80, 60), vm.visible.map { it.weight })

        vm.cycleSort(weightCol)
        assertNull(vm.sort)
    }

    @Test
    fun `applySystemView устанавливает фильтр сортировку и activeSavedViewId`() {
        val vm = TestListPageViewModel(scope)
        vm.forceLoaded(
            listOf(
                TestItem("А", 80),
                TestItem("Б", 60),
            ),
        )
        val view =
            SystemSavedView(
                id = SavedViewId.system("heavy"),
                filter = TestFilter(minWeight = 75),
                sort = SortState(ColumnId("weight"), SortDirection.Desc),
            )
        vm.applySystemView(view)
        assertEquals(SavedViewId.system("heavy"), vm.activeSavedViewId)
        assertEquals(1, vm.visible.size)
        assertEquals("А", vm.visible.first().name)
    }

    @Test
    fun `resetFilter сбрасывает фильтр и activeSavedViewId`() {
        val vm = TestListPageViewModel(scope)
        vm.forceLoaded(
            listOf(
                TestItem("А", 80),
                TestItem("Б", 60),
            ),
        )
        vm.updateFilter(TestFilter(minWeight = 70))
        assertEquals(1, vm.visible.size)

        vm.resetFilter()
        assertEquals(2, vm.visible.size)
        assertNull(vm.activeSavedViewId)
    }
}
