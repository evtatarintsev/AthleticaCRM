package org.athletica.crm.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_discipline
import org.athletica.crm.generated.resources.disciplines_empty
import org.athletica.crm.generated.resources.hint_search
import org.jetbrains.compose.resources.stringResource

/**
 * Шторка выбора дисциплины для привязки к группе.
 * Загружает список дисциплин организации через [api], исключает уже выбранные [existingDisciplineIds].
 * При выборе вызывает [onDisciplineSelected] и закрывается.
 *
 * [existingDisciplineIds] — дисциплины, уже привязанные к группе (исключаются из списка).
 * [api] — клиент API.
 * [onDismiss] — вызывается при закрытии шторки.
 * [onDisciplineSelected] — вызывается с выбранной дисциплиной.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDisciplineSheet(
    existingDisciplineIds: Set<DisciplineId> = emptySet(),
    api: ApiClient,
    onDismiss: () -> Unit,
    onDisciplineSelected: (DisciplineDetailResponse) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val viewModel = remember(existingDisciplineIds) { AddDisciplineViewModel(api, scope, existingDisciplineIds) }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.action_add_discipline),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(Res.string.hint_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            when (val s = viewModel.state) {
                is AddDisciplineState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AddDisciplineState.Error -> {
                    Text(
                        text = s.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }
                is AddDisciplineState.Loaded -> {
                    val filtered =
                        remember(s.disciplines, query) {
                            if (query.isBlank()) s.disciplines else s.disciplines.filter { it.name.contains(query, ignoreCase = true) }
                        }
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.disciplines_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(filtered, key = { it.id }) { discipline ->
                                ListItem(
                                    headlineContent = { Text(discipline.name) },
                                    modifier = Modifier.clickable { onDisciplineSelected(discipline) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
