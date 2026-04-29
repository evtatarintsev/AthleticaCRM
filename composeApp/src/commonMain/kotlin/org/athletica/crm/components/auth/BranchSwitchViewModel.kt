package org.athletica.crm.components.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.auth.SwitchBranchRequest
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.core.entityids.BranchId

/** Состояние экрана переключения филиала. */
sealed class BranchSwitchState {
    /** Начальное состояние: список ещё не загружен. */
    data object Idle : BranchSwitchState()

    /** Загрузка списка или выполнение переключения. */
    data object Loading : BranchSwitchState()

    /** Список загружен, ожидание выбора. */
    data class Loaded(val branches: List<BranchDetailResponse>) : BranchSwitchState()

    /** Ошибка загрузки или переключения. */
    data object Error : BranchSwitchState()
}

/**
 * ViewModel для диалога выбора/переключения филиала.
 * Загружает доступные филиалы через [ApiClient.myBranches] и выполняет переключение
 * через [ApiClient.switchBranch] при выборе пользователя.
 */
class BranchSwitchViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onSwitched: () -> Unit,
) {
    var state by mutableStateOf<BranchSwitchState>(BranchSwitchState.Idle)
        private set

    /** Загружает список доступных филиалов. Вызывается при открытии диалога. */
    fun load() {
        scope.launch {
            state = BranchSwitchState.Loading
            api.myBranches().fold(
                ifLeft = { state = BranchSwitchState.Error },
                ifRight = { state = BranchSwitchState.Loaded(it.branches) },
            )
        }
    }

    /** Переключает пользователя в филиал [branchId]. При успехе вызывает [onSwitched]. */
    fun switchTo(branchId: BranchId) {
        scope.launch {
            state = BranchSwitchState.Loading
            api.switchBranch(SwitchBranchRequest(branchId)).fold(
                ifLeft = { state = BranchSwitchState.Error },
                ifRight = { onSwitched() },
            )
        }
    }

    /** Сбрасывает состояние в [BranchSwitchState.Idle]. */
    fun reset() {
        state = BranchSwitchState.Idle
    }
}
