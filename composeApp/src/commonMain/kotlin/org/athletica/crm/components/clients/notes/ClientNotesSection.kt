package org.athletica.crm.components.clients.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.clients.ClientNoteSchema
import org.athletica.crm.components.clients.message
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_delete
import org.athletica.crm.generated.resources.client_notes_add_action
import org.athletica.crm.generated.resources.client_notes_cancel_edit
import org.athletica.crm.generated.resources.client_notes_delete_confirm_message
import org.athletica.crm.generated.resources.client_notes_delete_confirm_title
import org.athletica.crm.generated.resources.client_notes_edited_badge
import org.athletica.crm.generated.resources.client_notes_empty
import org.athletica.crm.generated.resources.client_notes_save_action
import org.athletica.crm.generated.resources.client_notes_text_placeholder
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Stateless секция вкладки «Заметки» карточки клиента.
 * Принимает текущее состояние и колбэки; не вызывает API напрямую.
 *
 * [currentEmployeeId] — текущий сотрудник для скрытия редактирования у чужих заметок;
 * null означает, что профиль ещё загружается — кнопки редактирования не отображаются.
 */
@Composable
fun ClientNotesSection(
    state: ClientNotesState,
    currentEmployeeId: EmployeeId?,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStartEdit: (ClientNoteSchema) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (ClientNoteSchema) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ClientNotesState.Loading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxWidth().padding(24.dp),
            ) {
                CircularProgressIndicator()
            }
        }

        is ClientNotesState.Error -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxWidth().padding(24.dp),
            ) {
                Text(
                    text = state.error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        is ClientNotesState.Loaded -> {
            ClientNotesLoaded(
                state = state,
                currentEmployeeId = currentEmployeeId,
                onDraftChange = onDraftChange,
                onSubmit = onSubmit,
                onStartEdit = onStartEdit,
                onCancelEdit = onCancelEdit,
                onDelete = onDelete,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ClientNotesLoaded(
    state: ClientNotesState.Loaded,
    currentEmployeeId: EmployeeId?,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStartEdit: (ClientNoteSchema) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (ClientNoteSchema) -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteToDelete by remember { mutableStateOf<ClientNoteSchema?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        ClientNotesInput(
            draft = state.draft,
            submitting = state.submitting,
            isEditing = state.editingId != null,
            draftError = state.draftError,
            onDraftChange = onDraftChange,
            onSubmit = onSubmit,
            onCancelEdit = onCancelEdit,
        )

        Spacer(Modifier.height(12.dp))

        if (state.notes.isEmpty()) {
            Text(
                text = stringResource(Res.string.client_notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            state.notes.forEachIndexed { index, note ->
                if (index > 0) {
                    HorizontalDivider()
                }
                ClientNoteRow(
                    note = note,
                    isOwn = currentEmployeeId != null && note.isAuthoredBy(currentEmployeeId),
                    onStartEdit = { onStartEdit(note) },
                    onAskDelete = { noteToDelete = note },
                )
            }
        }
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(stringResource(Res.string.client_notes_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.client_notes_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(note)
                        noteToDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ClientNotesInput(
    draft: String,
    submitting: Boolean,
    isEditing: Boolean,
    draftError: String?,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(stringResource(Res.string.client_notes_text_placeholder)) },
            isError = draftError != null,
            supportingText =
                draftError?.let { msg ->
                    { Text(msg, color = MaterialTheme.colorScheme.error) }
                },
            enabled = !submitting,
            minLines = 2,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSubmit,
                enabled = !submitting && draft.isNotBlank(),
            ) {
                Text(
                    text =
                        if (isEditing) {
                            stringResource(Res.string.client_notes_save_action)
                        } else {
                            stringResource(Res.string.client_notes_add_action)
                        },
                )
            }
            if (isEditing) {
                TextButton(onClick = onCancelEdit, enabled = !submitting) {
                    Text(stringResource(Res.string.client_notes_cancel_edit))
                }
            }
            if (submitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun ClientNoteRow(
    note: ClientNoteSchema,
    isOwn: Boolean,
    onStartEdit: () -> Unit,
    onAskDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = note.text.value,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.author.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = note.createdAt.formatDateTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (note.updatedAt != null) {
                Text(
                    text = stringResource(Res.string.client_notes_edited_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (isOwn) {
                IconButton(onClick = onStartEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onAskDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun Instant.formatDateTime(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.day.toString().padStart(2, '0')
    val month = local.month.number.toString().padStart(2, '0')
    val year = local.year
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day.$month.$year $hour:$minute"
}
