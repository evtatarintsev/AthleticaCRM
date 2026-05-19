package org.athletica.crm.ui.list

import kotlin.jvm.JvmInline

/**
 * Идентификатор сохранённого вида списка.
 * Префикс `system:` — встроенный пресет, `user:` — пользовательский.
 */
@JvmInline
value class SavedViewId(val value: String) {
    /** `true` если вид является системным (встроенным в код). */
    val isSystem: Boolean get() = value.startsWith("system:")

    companion object {
        /** Создаёт идентификатор системного вида с заданным суффиксом. */
        fun system(suffix: String): SavedViewId = SavedViewId("system:$suffix")

        /** Создаёт идентификатор пользовательского вида по строковому UUID. */
        fun user(uuid: String): SavedViewId = SavedViewId("user:$uuid")
    }
}

/**
 * UI-модель сохранённого вида для отображения в строке [SavedViewRow].
 * [id] — уникальный идентификатор.
 * [name] — уже разрешённая строка (через `stringResource` для системных или сохранённое имя для пользовательских).
 * [onApply] — применить этот вид.
 * [onRename] — `null` для системных видов.
 * [onDelete] — `null` для системных видов.
 */
data class SavedView(
    val id: SavedViewId,
    val name: String,
    val onApply: () -> Unit,
    val onRename: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
)

/**
 * Декларация системного (встроенного в код) сохранённого вида для конкретного раздела.
 * Хранится в виде констант рядом с ViewModel раздела.
 *
 * Имя вида ([nameRes]) разрешается в строку на UI-уровне через `stringResource`,
 * поэтому здесь оно не хранится. Страница самостоятельно конвертирует [SystemSavedView]
 * в [SavedView] при построении списка видов.
 *
 * [id] — идентификатор вида (создаётся через [SavedViewId.system]).
 * [filter] — типизированный фильтр раздела, применяемый вместе с видом.
 * [sort] — опциональная сортировка, применяемая вместе с фильтром.
 */
data class SystemSavedView<F>(
    val id: SavedViewId,
    val filter: F,
    val sort: SortState? = null,
)
