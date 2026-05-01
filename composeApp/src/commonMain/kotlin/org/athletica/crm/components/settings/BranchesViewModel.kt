package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.Branch
import org.athletica.crm.api.client.toBranchCreateRequest
import org.athletica.crm.api.client.toBranchUpdateRequest
import org.athletica.crm.api.schemas.branches.DeleteBranchRequest
import org.athletica.crm.core.entityids.BranchId

sealed interface BranchesLoadState {
    data object Loading : BranchesLoadState

    data class Error(val error: SettingsApiError) : BranchesLoadState

    data class Loaded(val items: List<DirectoryItem<BranchId>>) : BranchesLoadState
}

sealed interface BranchesSaveState {
    data object Idle : BranchesSaveState

    data object Saving : BranchesSaveState

    data class Error(val error: SettingsApiError) : BranchesSaveState
}

class BranchesViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<BranchesLoadState>(BranchesLoadState.Loading)
        private set

    var saveState by mutableStateOf<BranchesSaveState>(BranchesSaveState.Idle)
        private set

    fun load() {
        scope.launch {
            loadState = BranchesLoadState.Loading
            try {
                val result = api.branches.list()
                result.fold(
                    ifRight = { response ->
                        loadState =
                            BranchesLoadState.Loaded(
                                response.branches.map { DirectoryItem(id = it.id, name = it.name) },
                            )
                    },
                    ifLeft = { error ->
                        loadState = BranchesLoadState.Error(SettingsApiError.fromResponse(error))
                    },
                )
            } catch (e: Exception) {
                loadState = BranchesLoadState.Error(SettingsApiError.fromResponse(e))
            }
        }
    }

    fun onCreate(branch: DirectoryItem<BranchId>, onSaved: () -> Unit) {
        scope.launch {
            saveState = BranchesSaveState.Saving
            try {
                api.branches.create(Branch(branch.id, branch.name).toBranchCreateRequest())
                saveState = BranchesSaveState.Idle
                onSaved()
                load()
            } catch (e: Exception) {
                saveState = BranchesSaveState.Error(SettingsApiError.fromResponse(e))
            }
        }
    }

    fun onUpdate(branch: DirectoryItem<BranchId>, onSaved: () -> Unit) {
        scope.launch {
            saveState = BranchesSaveState.Saving
            try {
                api.branches.update(Branch(branch.id, branch.name).toBranchUpdateRequest())
                saveState = BranchesSaveState.Idle
                onSaved()
                load()
            } catch (e: Exception) {
                saveState = BranchesSaveState.Error(SettingsApiError.fromResponse(e))
            }
        }
    }

    fun onDelete(ids: Set<BranchId>) {
        scope.launch {
            saveState = BranchesSaveState.Saving
            try {
                api.branches.delete(DeleteBranchRequest(ids.toList()))
                saveState = BranchesSaveState.Idle
                load()
            } catch (e: Exception) {
                saveState = BranchesSaveState.Error(SettingsApiError.fromResponse(e))
            }
        }
    }

    fun onSaveErrorDismissed() {
        saveState = BranchesSaveState.Idle
    }
}
