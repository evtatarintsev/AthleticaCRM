package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Экран «Виды спорта».
 * Управляет собственным состоянием списка и внутренней навигацией list → create.
 */
@Composable
fun SportsTypesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf<List<DirectoryItem>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        DirectoryItemCreateScreen(
            title = "Новый вид спорта",
            onBack = { showCreate = false },
            onSave = { newItem ->
                items = items + newItem
                showCreate = false
            },
            modifier = modifier,
        )
        return
    }

    DirectoryListScreen(
        title = "Виды спорта",
        items = items,
        onBack = onBack,
        onAdd = { showCreate = true },
        onDeleteSelected = { ids -> items = items.filterNot { it.id in ids } },
        modifier = modifier,
    )
}
