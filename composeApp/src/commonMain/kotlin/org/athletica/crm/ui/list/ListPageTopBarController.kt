package org.athletica.crm.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Контроллер заголовка верхней панели приложения.
 * Страница списка пишет в него [title] и [subtitle] в `LaunchedEffect`,
 * [org.athletica.crm.MainScreen] читает и отображает через `MainTopAppBar`.
 *
 * Создаётся один раз в `MainScreen` и пробрасывается через [LocalListPageTopBar].
 */
class ListPageTopBarController {
    /** Заголовок текущего раздела или страницы. */
    var title: String by mutableStateOf("")
        private set

    /** Подзаголовок (например, количество записей или активный фильтр). `null` — скрыт. */
    var subtitle: String? by mutableStateOf(null)
        private set

    /**
     * Обновляет заголовок и подзаголовок. Вызывается из страницы списка.
     */
    fun set(
        title: String,
        subtitle: String? = null,
    ) {
        this.title = title
        this.subtitle = subtitle
    }
}

/**
 * CompositionLocal для доступа к контроллеру заголовка из любой страницы списка.
 * Должен быть предоставлен в `MainScreen` через `CompositionLocalProvider` перед `NavHost`.
 */
val LocalListPageTopBar =
    staticCompositionLocalOf<ListPageTopBarController> {
        error("LocalListPageTopBar не предоставлен. Оберни NavHost в CompositionLocalProvider в MainScreen.")
    }
