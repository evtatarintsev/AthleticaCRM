package org.athletica.crm.ui.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Вызывает [onLoadMore], когда прокрутка [listState] приближается к концу списка
 * (остаётся не более [buffer] элементов). Защита от лишних вызовов — на стороне
 * получателя (например, [ListPageViewModel.loadMore] игнорирует повторные вызовы).
 *
 * [listState] — состояние прокрутки наблюдаемого `LazyColumn`.
 * [buffer] — за сколько элементов до конца инициировать догрузку.
 * [onLoadMore] — действие догрузки следующей страницы.
 */
@Composable
fun LoadMoreOnScrollEnd(
    listState: LazyListState,
    buffer: Int = 5,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible >= totalItems - 1 - buffer
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
}
