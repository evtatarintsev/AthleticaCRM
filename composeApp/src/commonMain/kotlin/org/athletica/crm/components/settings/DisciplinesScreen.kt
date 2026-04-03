package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest

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
    var items by remember { mutableStateOf<List<DirectoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DirectoryItem?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        isLoading = true
        error = null
        api.disciplineList().fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                        ApiClientError.Unauthenticated -> "Необходима авторизация"
                    }
            },
            ifRight = { response ->
                items =
                    response.disciplines.map { discipline ->
                        DirectoryItem(id = discipline.id, name = discipline.name)
                    }
            },
        )
        isLoading = false
    }

    // Экран редактирования дисциплины
    editingItem?.let { item ->
        DirectoryItemCreateScreen(
            title = "Изменить дисциплину",
            initialItem = item,
            onBack = { editingItem = null },
            onSave = { updated ->
                scope.launch {
                    api.updateDiscipline(UpdateDisciplineRequest(id = updated.id, name = updated.name))
                        .onRight { refreshKey++ }
                }
                editingItem = null
            },
            modifier = modifier,
        )
        return
    }

    // Экран создания дисциплины
    if (showCreate) {
        DirectoryItemCreateScreen(
            title = "Новая дисциплина",
            onBack = { showCreate = false },
            onSave = { newItem ->
                scope.launch {
                    api.createDiscipline(CreateDisciplineRequest(id = newItem.id, name = newItem.name))
                        .onRight { refreshKey++ }
                }
                showCreate = false
            },
            modifier = modifier,
        )
        return
    }

    DirectoryListScreen(
        title = "Дисциплины",
        items = items,
        isLoading = isLoading,
        error = error,
        onBack = onBack,
        onAdd = { showCreate = true },
        onItemClick = { item -> editingItem = item },
        onDeleteSelected = { ids ->
            scope.launch {
                api.deleteDiscipline(DeleteDisciplineRequest(ids = ids.toList()))
                    .onRight { refreshKey++ }
            }
        },
        modifier = modifier,
    )
}
