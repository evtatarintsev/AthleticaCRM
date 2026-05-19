package org.athletica.crm.ui.list

import kotlin.jvm.JvmInline

/**
 * Идентификатор колонки таблицы. Используется для привязки сортировки и сохранения настроек отображения.
 * Значение должно быть стабильным (не зависит от локали) — например, `"name"`, `"balance"`.
 */
@JvmInline
value class ColumnId(val value: String)
