package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EntityId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.screen_client_source_create
import org.athletica.crm.generated.resources.screen_client_sources
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Источники клиентов».
 * Управляет собственным состоянием списка и внутренней навигацией list → create.
 */
@Composable
fun ClientSourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf<List<DirectoryItem<EntityId>>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        DirectoryItemCreateScreen(
            title = stringResource(Res.string.screen_client_source_create),
            onBack = { showCreate = false },
            onSave = { newItem ->
                items = items + newItem
                showCreate = false
            },
            modifier = modifier,
            newId = { DisciplineId.new() as EntityId },
        )
        return
    }

    DirectoryListScreen(
        title = stringResource(Res.string.screen_client_sources),
        items = items,
        onBack = onBack,
        onAdd = { showCreate = true },
        onDeleteSelected = { ids -> items = items.filterNot { it.id in ids } },
        modifier = modifier,
    )
}
