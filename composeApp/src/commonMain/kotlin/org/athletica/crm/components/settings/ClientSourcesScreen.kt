package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.screen_client_source_create
import org.athletica.crm.generated.resources.screen_client_sources
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Источники клиентов».
 * Загружает список через API, поддерживает создание, редактирование и удаление.
 */
@Composable
fun ClientSourcesScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ClientSourcesViewModel(api, scope) }
    var showCreate by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DirectoryItem<LeadSourceId>?>(null) }

    val isSaving = viewModel.saveState is ClientSourcesSaveState.Saving
    val saveError = (viewModel.saveState as? ClientSourcesSaveState.Error)?.error

    editingItem?.let { item ->
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_client_source_create),
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
            newId = { LeadSourceId.new() },
        )
        return
    }

    if (showCreate) {
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_client_source_create),
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
            newId = { LeadSourceId.new() },
        )
        return
    }

    val loadedItems = (viewModel.loadState as? ClientSourcesLoadState.Loaded)?.items ?: emptyList()
    val loadError = (viewModel.loadState as? ClientSourcesLoadState.Error)?.error

    DirectoryListScreen<LeadSourceId>(
        title = stringResource(Res.string.screen_client_sources),
        items = loadedItems,
        isLoading = viewModel.loadState is ClientSourcesLoadState.Loading,
        error = loadError?.message(),
        onBack = onBack,
        onAdd = { showCreate = true },
        onItemClick = { item -> editingItem = item },
        onDeleteSelected = { ids -> viewModel.onDelete(ids) },
        modifier = modifier,
    )
}
