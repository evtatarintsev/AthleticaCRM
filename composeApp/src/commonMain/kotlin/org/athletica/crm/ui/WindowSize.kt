package org.athletica.crm.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Адаптивные брейкпоинты — аналог Bootstrap sm/md/xl.
 * Используется с [androidx.compose.foundation.layout.BoxWithConstraints].
 *
 * Пример:
 * ```
 * BoxWithConstraints {
 *     val windowSize = WindowSize.fromWidth(maxWidth)
 *     if (windowSize >= WindowSize.MEDIUM) { ... }
 * }
 * ```
 */
enum class WindowSize {
    /** < 600 dp — мобильный телефон. */
    COMPACT,

    /** 600–1199 dp — планшет / узкий десктоп. */
    MEDIUM,

    /** ≥ 1200 dp — широкий десктоп. */
    EXPANDED;

    companion object {
        fun fromWidth(width: Dp): WindowSize =
            when {
                width < 600.dp -> COMPACT
                width < 1200.dp -> MEDIUM
                else -> EXPANDED
            }
    }
}
