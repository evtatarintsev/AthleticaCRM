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
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.api.schemas.sports.UpdateSportRequest

/**
 * Экран «Виды спорта».
 * Загружает список через [api], поддерживает создание, редактирование и удаление.
 */
@Composable
fun SportsTypesScreen(
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
        api.sportList().fold(
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
                    response.sports.map { sport ->
                        DirectoryItem(id = sport.id, name = sport.name)
                    }
            },
        )
        isLoading = false
    }

    // Экран редактирования вида спорта
    editingItem?.let { item ->
        DirectoryItemCreateScreen(
            title = "Изменить вид спорта",
            initialItem = item,
            onBack = { editingItem = null },
            onSave = { updated ->
                scope.launch {
                    api.updateSport(UpdateSportRequest(id = updated.id, name = updated.name))
                        .onRight { refreshKey++ }
                }
                editingItem = null
            },
            modifier = modifier,
        )
        return
    }

    // Экран создания вида спорта
    if (showCreate) {
        DirectoryItemCreateScreen(
            title = "Новый вид спорта",
            onBack = { showCreate = false },
            onSave = { newItem ->
                scope.launch {
                    api.createSport(CreateSportRequest(id = newItem.id, name = newItem.name))
                        .onRight { refreshKey++ }
                }
                showCreate = false
            },
            modifier = modifier,
        )
        return
    }

    DirectoryListScreen(
        title = "Виды спорта",
        items = items,
        isLoading = isLoading,
        error = error,
        onBack = onBack,
        onAdd = { showCreate = true },
        onItemClick = { item -> editingItem = item },
        onDeleteSelected = { ids ->
            scope.launch {
                api.deleteSport(DeleteSportRequest(ids = ids.toList()))
                    .onRight { refreshKey++ }
            }
        },
        modifier = modifier,
    )
}
