package org.athletica.crm.components.clients

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
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_client_group
import org.athletica.crm.generated.resources.groups_empty
import org.athletica.crm.generated.resources.hint_search
import org.jetbrains.compose.resources.stringResource

/**
 * Шторка выбора группы для добавления одного или нескольких клиентов.
 * [clientIds] — идентификаторы клиентов, добавляемых в группу.
 * [existingGroupIds] — группы, исключаемые из списка (уже добавленные).
 * [onDismiss] — вызывается при закрытии шторки.
 * [onGroupAdded] — вызывается после успешного добавления клиентов в группу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGroupSheet(
    clientIds: List<ClientId>,
    existingGroupIds: Set<GroupId> = emptySet(),
    api: ApiClient,
    onDismiss: () -> Unit,
    onGroupAdded: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val viewModel = remember { AddToGroupViewModel(api, clientIds, existingGroupIds, scope) { onGroupAdded() } }

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
                    text = stringResource(Res.string.action_add_client_group),
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
                is AddToGroupState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is AddToGroupState.Error -> {
                    Text(
                        text = s.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }

                is AddToGroupState.Loaded -> {
                    val filtered =
                        remember(s.groups, query) {
                            s.groups.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                        }

                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.groups_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(filtered, key = { it.id }) { group ->
                                ListItem(
                                    headlineContent = { Text(group.name) },
                                    modifier =
                                        Modifier.clickable(enabled = !s.isAdding) {
                                            viewModel.onGroupSelected(group.id)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
