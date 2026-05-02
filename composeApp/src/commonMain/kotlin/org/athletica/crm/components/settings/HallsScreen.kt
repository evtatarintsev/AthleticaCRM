package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.screen_hall_create
import org.athletica.crm.generated.resources.screen_hall_edit
import org.athletica.crm.generated.resources.screen_halls
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Залы».
 * Загружает список через [api], поддерживает создание, редактирование и удаление.
 */
@Composable
fun HallsScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { HallsViewModel(api, scope) }
    var showCreate by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DirectoryItem<HallId>?>(null) }

    val isSaving = viewModel.saveState is HallsSaveState.Saving
    val saveError = (viewModel.saveState as? HallsSaveState.Error)?.error

    editingItem?.let { item ->
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_hall_edit),
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
            newId = { HallId.new() },
        )
        return
    }

    if (showCreate) {
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_hall_create),
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
            newId = { HallId.new() },
        )
        return
    }

    val loadedItems = (viewModel.loadState as? HallsLoadState.Loaded)?.items ?: emptyList()
    val loadError = (viewModel.loadState as? HallsLoadState.Error)?.error

    DirectoryListScreen<HallId>(
        title = stringResource(Res.string.screen_halls),
        items = loadedItems,
        isLoading = viewModel.loadState is HallsLoadState.Loading,
        error = loadError?.message(),
        onBack = onBack,
        onAdd = { showCreate = true },
        onItemClick = { item -> editingItem = item },
        onDeleteSelected = { ids -> viewModel.onDelete(ids) },
        modifier = modifier,
    )
}
