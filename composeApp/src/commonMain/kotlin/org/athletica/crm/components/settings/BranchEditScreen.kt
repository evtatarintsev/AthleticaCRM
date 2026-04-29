package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.screen_branch_create
import org.athletica.crm.generated.resources.screen_branch_edit
import org.jetbrains.compose.resources.stringResource

@Composable
fun BranchEditScreen(
    api: ApiClient,
    branchId: BranchId?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { BranchesViewModel(api, scope) }
    var showCreate by remember { mutableStateOf(branchId == null) }
    var editingItem by remember { mutableStateOf<DirectoryItem<BranchId>?>(null) }

    val isSaving = viewModel.saveState is BranchesSaveState.Saving
    val saveError = (viewModel.saveState as? BranchesSaveState.Error)?.error

    editingItem?.let { item ->
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_branch_edit),
            initialItem = item,
            onBack = {
                editingItem = null
                viewModel.onSaveErrorDismissed()
            },
            onSave = { updated ->
                viewModel.onUpdate(updated) { editingItem = null }
            },
            error = saveError?.message(),
            isLoading = isSaving,
            modifier = modifier,
            newId = { BranchId.new() },
        )
        return
    }

    if (showCreate) {
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_branch_create),
            onBack = {
                showCreate = false
                viewModel.onSaveErrorDismissed()
            },
            onSave = { newItem ->
                viewModel.onCreate(newItem) { showCreate = false }
            },
            error = saveError?.message(),
            isLoading = isSaving,
            modifier = modifier,
            newId = { BranchId.new() },
        )
        return
    }
}
