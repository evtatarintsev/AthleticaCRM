package org.athletica.crm.ui.list

import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** Тестовый делегат для [ListPageViewModel]. */
private class TestListPageDelegate(
    val fetchResult: List<TestItem> =
        listOf(
            TestItem("Алексеев Иван", 80),
            TestItem("Борисова Мария", 60),
            TestItem("Иванов Пётр", 90),
        ),
) : ListPageDelegate<TestItem, TestFilter> {
    override fun defaultFilter(): TestFilter = TestFilter()

    override fun defaultSort(): SortState? = null

    override suspend fun fetch(
        filter: TestFilter,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<TestItem>> = Either.Right(FetchResult(fetchResult))

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
}

/** Тесты для [ListPageViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListPageViewModelTest {
    @Test
    fun `load устанавливает состояние Loaded с данными`() =
        runTest(UnconfinedTestDispatcher()) {
            val delegate = TestListPageDelegate()
            val vm = ListPageViewModel(delegate, backgroundScope)
            vm.load()
            val data = vm.state.data
            assertIs<ListData.Loaded<TestItem>>(data)
            assertEquals(3, data.items.size)
        }

    @Test
    fun `setSearch фильтрует по имени`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm =
                ListPageViewModel(
                    TestListPageDelegate(
                        fetchResult =
                            listOf(
                                TestItem("Алексеев Иван", 80),
                                TestItem("Борисова Мария", 60),
                            ),
                    ),
                    backgroundScope,
                )
            vm.load()
            vm.setSearch("ив")
            assertEquals(1, vm.visible.size)
            assertEquals("Алексеев Иван", vm.visible.first().name)
        }

    @Test
    fun `setFilter пересчитывает visible и сбрасывает activeSavedViewId`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm =
                ListPageViewModel(
                    TestListPageDelegate(
                        fetchResult =
                            listOf(
                                TestItem("Алексеев Иван", 80),
                                TestItem("Борисова Мария", 60),
                            ),
                    ),
                    backgroundScope,
                )
            vm.load()
            vm.applySystemView(
                SystemSavedView(
                    id = SavedViewId.system("test"),
                    filter = TestFilter(minWeight = 0),
                ),
            )
            assertEquals(SavedViewId.system("test"), vm.state.activeSavedViewId)

            vm.setFilter(TestFilter(minWeight = 70))
            assertEquals(1, vm.visible.size)
            assertEquals("Алексеев Иван", vm.visible.first().name)
            assertNull(vm.state.activeSavedViewId)
        }

    @Test
    fun `cycleSort сортирует список по весу`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm =
                ListPageViewModel(
                    TestListPageDelegate(
                        fetchResult =
                            listOf(
                                TestItem("А", 80),
                                TestItem("Б", 60),
                                TestItem("В", 90),
                            ),
                    ),
                    backgroundScope,
                )
            vm.load()
            val weightCol = ColumnId("weight")
            vm.cycleSort(weightCol)
            assertEquals(listOf(60, 80, 90), vm.visible.map { it.weight })

            vm.cycleSort(weightCol)
            assertEquals(listOf(90, 80, 60), vm.visible.map { it.weight })

            vm.cycleSort(weightCol)
            assertNull(vm.state.sort)
        }

    @Test
    fun `applySystemView устанавливает фильтр сортировку и activeSavedViewId`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm =
                ListPageViewModel(
                    TestListPageDelegate(
                        fetchResult =
                            listOf(
                                TestItem("А", 80),
                                TestItem("Б", 60),
                            ),
                    ),
                    backgroundScope,
                )
            vm.load()
            val view =
                SystemSavedView(
                    id = SavedViewId.system("heavy"),
                    filter = TestFilter(minWeight = 75),
                    sort = SortState(ColumnId("weight"), SortDirection.Desc),
                )
            vm.applySystemView(view)
            assertEquals(SavedViewId.system("heavy"), vm.state.activeSavedViewId)
            assertEquals(1, vm.visible.size)
            assertEquals("А", vm.visible.first().name)
        }

    @Test
    fun `resetFilter сбрасывает фильтр и activeSavedViewId`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm =
                ListPageViewModel(
                    TestListPageDelegate(
                        fetchResult =
                            listOf(
                                TestItem("А", 80),
                                TestItem("Б", 60),
                            ),
                    ),
                    backgroundScope,
                )
            vm.load()
            vm.setFilter(TestFilter(minWeight = 70))
            assertEquals(1, vm.visible.size)

            vm.resetFilter()
            assertEquals(2, vm.visible.size)
            assertNull(vm.state.activeSavedViewId)
        }
}
