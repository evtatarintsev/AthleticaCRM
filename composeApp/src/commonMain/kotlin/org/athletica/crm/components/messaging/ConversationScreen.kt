package org.athletica.crm.components.messaging

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.messaging.InboundMessageSchema
import org.athletica.crm.api.schemas.messaging.MessageSchema
import org.athletica.crm.api.schemas.messaging.OutboundMessageSchema
import org.athletica.crm.components.clients.message
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_send
import org.athletica.crm.generated.resources.contact_add
import org.athletica.crm.generated.resources.contact_address_label
import org.athletica.crm.generated.resources.contacts_empty
import org.athletica.crm.generated.resources.contacts_title
import org.athletica.crm.generated.resources.conversation_channel_label
import org.athletica.crm.generated.resources.conversation_empty
import org.athletica.crm.generated.resources.conversation_message_hint
import org.athletica.crm.generated.resources.conversation_no_channels
import org.athletica.crm.generated.resources.conversation_no_contact
import org.athletica.crm.generated.resources.screen_conversation
import org.jetbrains.compose.resources.stringResource

/**
 * Экран диалога с клиентом: лента сообщений, выбор канала, поле ввода и панель контактов.
 * Загружает данные через [api]; [clientId] — клиент диалога; [onBack] — возврат назад.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    clientId: ClientId,
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ConversationViewModel(api, clientId, scope) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_conversation)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    val loaded = viewModel.state as? ConversationState.Loaded
                    if (loaded != null) {
                        IconButton(onClick = { viewModel.toggleContacts() }) {
                            Icon(Icons.Default.Contacts, contentDescription = stringResource(Res.string.contacts_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = viewModel.state) {
            is ConversationState.Loading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator()
                }
            }

            is ConversationState.Error -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text(s.error.message(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }

            is ConversationState.Loaded -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (s.showContacts) {
                        ContactsPanel(s, viewModel)
                        HorizontalDivider()
                    }
                    MessageList(s.messages, modifier = Modifier.weight(1f))
                    Composer(s, viewModel)
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MessageSchema>,
    modifier: Modifier,
) {
    if (messages.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text(stringResource(Res.string.conversation_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id.toString() }) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: MessageSchema) {
    val outbound = message is OutboundMessageSchema
    val alignment = if (outbound) Alignment.End else Alignment.Start
    val bubbleColor =
        if (outbound) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(color = bubbleColor, shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(4.dp))
                when (message) {
                    is OutboundMessageSchema -> OutboundStatus(message)
                    is InboundMessageSchema -> Unit
                }
            }
        }
    }
}

/** Состояние доставок исходящего сообщения по каналам: статус и текст ошибки при сбое. */
@Composable
private fun OutboundStatus(message: OutboundMessageSchema) {
    message.deliveries.forEach { delivery ->
        Text(
            text = delivery.state.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val error = delivery.errorMessage
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Composer(
    state: ConversationState.Loaded,
    viewModel: ConversationViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        if (state.channels.isEmpty()) {
            Text(
                stringResource(Res.string.conversation_no_channels),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            return
        }
        ChannelPicker(state, viewModel)
        val selected = state.selectedChannel
        if (selected != null && !state.isAvailable(selected)) {
            Text(
                stringResource(Res.string.conversation_no_contact),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (state.sendError != null) {
            Text(state.sendError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = { viewModel.setDraft(it) },
                placeholder = { Text(stringResource(Res.string.conversation_message_hint)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.send() }, enabled = state.canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.action_send))
            }
        }
    }
}

@Composable
private fun ChannelPicker(
    state: ConversationState.Loaded,
    viewModel: ConversationViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedChannel
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                stringResource(Res.string.conversation_channel_label) + ": " +
                    (selected?.let { it.name + " (" + it.channelType.label() + ")" } ?: "—"),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.channels.forEach { channel ->
                val available = state.isAvailable(channel)
                DropdownMenuItem(
                    enabled = available,
                    text = { Text(channel.name + " (" + channel.channelType.label() + ")") },
                    onClick = {
                        viewModel.selectChannel(channel.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ContactsPanel(
    state: ConversationState.Loaded,
    viewModel: ConversationViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(stringResource(Res.string.contacts_title), style = MaterialTheme.typography.titleSmall)
        if (state.contacts.isEmpty()) {
            Text(
                stringResource(Res.string.contacts_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            state.contacts.forEach { contact -> ContactRow(contact, viewModel) }
        }
        Spacer(Modifier.width(8.dp))
        ContactTypePicker(state.newContactType) { viewModel.setNewContactType(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.newContactAddress,
                onValueChange = { viewModel.setNewContactAddress(it) },
                label = { Text(stringResource(Res.string.contact_address_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { viewModel.addContact() }, enabled = state.newContactAddress.isNotBlank()) {
                Text(stringResource(Res.string.contact_add))
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ClientContactSchema,
    viewModel: ConversationViewModel,
) {
    ListItem(
        headlineContent = { Text(contact.address) },
        supportingContent = { Text(contact.channelType.label()) },
        trailingContent = {
            IconButton(onClick = { viewModel.deleteContact(contact.id) }) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        },
    )
}

@Composable
private fun ContactTypePicker(
    selected: ChannelType,
    onSelect: (ChannelType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.label())
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChannelType.entries.filter { it != ChannelType.IN_APP }.forEach { type ->
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
