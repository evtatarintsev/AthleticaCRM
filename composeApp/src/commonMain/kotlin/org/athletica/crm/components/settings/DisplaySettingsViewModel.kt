package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.settings.DisplaySettings

/**
 * Глобальный ViewModel для управления настройками отображения приложения.
 * Загружает настройки один раз при инициализации и обеспечивает синхронизацию с сервером.
 * Может расширяться другими секциями настроек по мере развития приложения.
 */
class DisplaySettingsViewModel(private val api: ApiClient, private val scope: CoroutineScope) {
    /**
     * Глобальные настройки отображения.
     * Включает настройки для всех модулей приложения (клиенты, группы и т.д.).
     */
    var displaySettings by mutableStateOf(DisplaySettings())
        private set

    init {
        scope.launch {
            api.displaySettings.get().fold(
                ifLeft = { /* ошибка загрузки — оставляем дефолт */ },
                ifRight = { displaySettings = it },
            )
        }
    }

    /**
     * Обновляет глобальные настройки отображения.
     * Обновление применяется сразу (optimistic), а затем синхронизируется с сервером.
     *
     * @param settings новые настройки отображения
     */
    fun update(settings: DisplaySettings) {
        displaySettings = settings
        scope.launch {
            api.displaySettings.update(settings)
        }
    }
}
