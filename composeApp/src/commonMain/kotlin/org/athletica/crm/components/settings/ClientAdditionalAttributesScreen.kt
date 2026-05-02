package org.athletica.crm.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.components.avatar.TextAvatar
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_attribute
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_delete_selected
import org.athletica.crm.generated.resources.column_required
import org.athletica.crm.generated.resources.column_type
import org.athletica.crm.generated.resources.empty_list
import org.athletica.crm.generated.resources.label_name
import org.athletica.crm.generated.resources.label_no
import org.athletica.crm.generated.resources.label_selected_count
import org.athletica.crm.generated.resources.label_yes
import org.athletica.crm.generated.resources.screen_client_additional_attributes
import org.athletica.crm.generated.resources.type_boolean
import org.athletica.crm.generated.resources.type_date
import org.athletica.crm.generated.resources.type_email
import org.athletica.crm.generated.resources.type_number
import org.athletica.crm.generated.resources.type_phone
import org.athletica.crm.generated.resources.type_select
import org.athletica.crm.generated.resources.type_string
import org.athletica.crm.generated.resources.type_url
import org.jetbrains.compose.resources.stringResource

/** Экран настроек дополнительных атрибутов клиентов с созданием и редактированием полей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientAdditionalAttributesScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ClientAdditionalAttributesViewModel(api, scope) }

    var showCreate by remember { mutableStateOf(false) }
    var editingAttribute by remember { mutableStateOf<CustomFieldDefinitionSchema?>(null) }
    var selectedFieldKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val isSaving = viewModel.saveState is ClientAdditionalAttributesSaveState.Saving
    val saveError = (viewModel.saveState as? ClientAdditionalAttributesSaveState.Error)?.error

    editingAttribute?.let { attribute ->
        CustomFieldAttributeCreateScreen(
            onBack = {
                editingAttribute = null
                viewModel.onSaveErrorDismissed()
            },
            onSave = { updated, isNew ->
                viewModel.saveAttribute(updated = updated, isNew = isNew) {
                    editingAttribute = null
                    selectedFieldKeys = emptySet()
                }
            },
            initialAttribute = attribute,
            error = saveError?.message(),
            isLoading = isSaving,
            modifier = modifier,
        )
        return
    }

    if (showCreate) {
        CustomFieldAttributeCreateScreen(
            onBack = {
                showCreate = false
                viewModel.onSaveErrorDismissed()
            },
            onSave = { updated, isNew ->
                viewModel.saveAttribute(updated = updated, isNew = isNew) {
                    showCreate = false
                    selectedFieldKeys = emptySet()
                }
            },
            error = saveError?.message(),
            isLoading = isSaving,
            modifier = modifier,
        )
        return
    }

    val attributes = (viewModel.loadState as? ClientAdditionalAttributesLoadState.Loaded)?.attributes ?: emptyList()
    val loadError = (viewModel.loadState as? ClientAdditionalAttributesLoadState.Error)?.error

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_client_additional_attributes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedFieldKeys.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        showCreate = true
                        viewModel.onSaveErrorDismissed()
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_add_attribute)) },
                )
            }
        },
        bottomBar = {
            if (selectedFieldKeys.isNotEmpty()) {
                BottomAppBar {
                    Text(
                        text = stringResource(Res.string.label_selected_count, selectedFieldKeys.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp).weight(1f),
                    )
                    IconButton(
                        onClick = {
                            viewModel.deleteAttributes(selectedFieldKeys) {
                                selectedFieldKeys = emptySet()
                            }
                        },
                        enabled = !isSaving,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete_selected))
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            viewModel.loadState is ClientAdditionalAttributesLoadState.Loading && attributes.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }
            }

            loadError != null && attributes.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    Text(
                        text = loadError.message(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            attributes.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    Text(
                        text = stringResource(Res.string.empty_list),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    ClientAttributesTableHeader()
                    HorizontalDivider()

                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                top = 4.dp,
                                bottom = if (selectedFieldKeys.isNotEmpty()) 80.dp else 4.dp,
                            ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(attributes, key = { it.fieldKey }) { attribute ->
                            ClientAttributeRow(
                                attribute = attribute,
                                selected = attribute.fieldKey in selectedFieldKeys,
                                onCheckedChange = { checked ->
                                    selectedFieldKeys =
                                        if (checked) {
                                            selectedFieldKeys + attribute.fieldKey
                                        } else {
                                            selectedFieldKeys - attribute.fieldKey
                                        }
                                },
                                onClick = {
                                    editingAttribute = attribute
                                    viewModel.onSaveErrorDismissed()
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Заголовок таблицы атрибутов. */
@Composable
private fun ClientAttributesTableHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
    ) {
        Spacer(Modifier.size(40.dp))

        Text(
            text = stringResource(Res.string.label_name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        Text(
            text = stringResource(Res.string.column_type),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            text = stringResource(Res.string.column_required),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.5f),
        )
        Spacer(Modifier.weight(0.3f))
    }
}

/** Строка таблицы атрибута с выбором и переходом к редактированию. */
@Composable
private fun ClientAttributeRow(
    attribute: CustomFieldDefinitionSchema,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = attribute.label,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = attribute.fieldType.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.8f),
                )
                Text(
                    text = if (attribute.isRequired) stringResource(Res.string.label_yes) else stringResource(Res.string.label_no),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.5f),
                )
            }
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                TextAvatar(attribute.label)
            }
        },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Возвращает локализованное название типа пользовательского поля. */
@Composable
private fun String.displayName(): String =
    when (this) {
        "text" -> stringResource(Res.string.type_string)
        "number" -> stringResource(Res.string.type_number)
        "date" -> stringResource(Res.string.type_date)
        "select" -> stringResource(Res.string.type_select)
        "boolean" -> stringResource(Res.string.type_boolean)
        "phone" -> stringResource(Res.string.type_phone)
        "email" -> stringResource(Res.string.type_email)
        "url" -> stringResource(Res.string.type_url)
        else -> this
    }
