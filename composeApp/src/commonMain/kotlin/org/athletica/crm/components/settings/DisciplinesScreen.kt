package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.screen_discipline_create
import org.athletica.crm.generated.resources.screen_discipline_edit
import org.athletica.crm.generated.resources.screen_disciplines
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Дисциплины».
 * Загружает список через [api], поддерживает создание, редактирование и удаление.
 */
@Composable
fun DisciplinesScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { DisciplinesViewModel(api, scope) }
    var showCreate by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DirectoryItem<DisciplineId>?>(null) }

    val isSaving = viewModel.saveState is DisciplinesSaveState.Saving
    val saveError = (viewModel.saveState as? DisciplinesSaveState.Error)?.error

    editingItem?.let { item ->
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_discipline_edit),
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
            newId = { DisciplineId.new() },
        )
        return
    }

    if (showCreate) {
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_discipline_create),
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
            newId = { DisciplineId.new() },
        )
        return
    }

    val loadedItems = (viewModel.loadState as? DisciplinesLoadState.Loaded)?.items ?: emptyList()
    val loadError = (viewModel.loadState as? DisciplinesLoadState.Error)?.error

    DirectoryListScreen<DisciplineId>(
        title = stringResource(Res.string.screen_disciplines),
        items = loadedItems,
        isLoading = viewModel.loadState is DisciplinesLoadState.Loading,
        error = loadError?.message(),
        onBack = onBack,
        onAdd = { showCreate = true },
        onItemClick = { item -> editingItem = item },
        onDeleteSelected = { ids -> viewModel.onDelete(ids) },
        modifier = modifier,
    )
}
