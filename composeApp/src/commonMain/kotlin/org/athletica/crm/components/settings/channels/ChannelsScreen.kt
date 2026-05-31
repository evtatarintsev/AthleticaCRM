package org.athletica.crm.components.settings.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.components.clients.message
import org.athletica.crm.components.messaging.label
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_delete
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.channel_enabled_label
import org.athletica.crm.generated.resources.channel_name_label
import org.athletica.crm.generated.resources.channel_type_label
import org.athletica.crm.generated.resources.channels_empty
import org.athletica.crm.generated.resources.screen_channel_create
import org.athletica.crm.generated.resources.screen_channel_edit
import org.athletica.crm.generated.resources.screen_channels
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Каналы связи»: список интеграций с возможностью создания, редактирования и удаления.
 * Загружает данные через [api]; [onBack] — возврат на экран настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ChannelsViewModel(api, scope) }

    when (val s = viewModel.state) {
        is ChannelsState.Loading -> {
            ScaffoldWithTitle(stringResource(Res.string.screen_channels), onBack, modifier) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }
        }

        is ChannelsState.Error -> {
            ScaffoldWithTitle(stringResource(Res.string.screen_channels), onBack, modifier) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text(s.error.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        }

        is ChannelsState.Loaded -> {
            val editor = s.editor
            if (editor == null) {
                ChannelsListView(s, viewModel, onBack, modifier)
            } else {
                ChannelEditorView(editor, s, viewModel, modifier)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelsListView(
    state: ChannelsState.Loaded,
    viewModel: ChannelsViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_channels)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openCreate() }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { padding ->
        if (state.channels.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    stringResource(Res.string.channels_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.channels, key = { it.id.toString() }) { channel ->
                    ListItem(
                        headlineContent = { Text(channel.name) },
                        supportingContent = {
                            val suffix = if (!channel.enabled) " · ✕" else ""
                            Text(channel.channelType.label() + suffix)
                        },
                        modifier = Modifier.clickable { viewModel.openEdit(channel) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelEditorView(
    editor: ChannelEditor,
    state: ChannelsState.Loaded,
    viewModel: ChannelsViewModel,
    modifier: Modifier,
) {
    val title =
        if (editor.id == null) {
            stringResource(Res.string.screen_channel_create)
        } else {
            stringResource(Res.string.screen_channel_edit)
        }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeEditor() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (editor.id != null) {
                        IconButton(onClick = { viewModel.delete(editor.id) }, enabled = !state.saving) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChannelTypeDropdown(
                selected = editor.channelType,
                enabled = editor.id == null,
                onSelect = { viewModel.setType(it) },
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = { viewModel.setName(it) },
                label = { Text(stringResource(Res.string.channel_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.channel_enabled_label))
                Spacer(Modifier.width(12.dp))
                Switch(checked = editor.enabled, onCheckedChange = { viewModel.setEnabled(it) })
            }
            if (state.saveError != null) {
                Text(state.saveError, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.save() },
                enabled = !state.saving && editor.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }
}

@Composable
private fun ChannelTypeDropdown(
    selected: ChannelType,
    enabled: Boolean,
    onSelect: (ChannelType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.channel_type_label) + ": " + selected.label())
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChannelType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldWithTitle(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}
