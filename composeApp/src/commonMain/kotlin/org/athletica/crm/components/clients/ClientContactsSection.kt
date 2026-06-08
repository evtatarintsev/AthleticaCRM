package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_contact
import org.athletica.crm.generated.resources.action_remove_contact
import org.athletica.crm.generated.resources.contact_type_email
import org.athletica.crm.generated.resources.contact_type_facebook
import org.athletica.crm.generated.resources.contact_type_phone
import org.athletica.crm.generated.resources.contact_type_telegram
import org.athletica.crm.generated.resources.contact_type_vk
import org.athletica.crm.generated.resources.label_contacts
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Блок управления контактами клиента на форме создания/редактирования.
 * Stateless: получает [contacts] и сообщает об изменениях через [onContactsChange].
 */
@Composable
fun ClientContactsSection(
    contacts: List<ContactFormEntry>,
    onContactsChange: (List<ContactFormEntry>) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.label_contacts),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        contacts.forEachIndexed { index, entry ->
            ContactRow(
                entry = entry,
                enabled = enabled,
                onTypeChange = { type ->
                    onContactsChange(contacts.replacedAt(index, entry.copy(type = type)))
                },
                onValueChange = { value ->
                    onContactsChange(contacts.replacedAt(index, entry.copy(value = value)))
                },
                onRemove = { onContactsChange(contacts.removedAt(index)) },
            )
        }
        TextButton(
            onClick = { onContactsChange(contacts + ContactFormEntry()) },
            enabled = enabled,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(Res.string.action_add_contact))
        }
    }
}

/** Одна строка контакта: тип (дропдаун) + значение + удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactRow(
    entry: ContactFormEntry,
    enabled: Boolean,
    onTypeChange: (ContactType) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
            modifier = Modifier.width(140.dp),
        ) {
            OutlinedTextField(
                value = stringResource(entry.type.labelRes()),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier =
                    Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false },
            ) {
                ContactType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.labelRes())) },
                        onClick = {
                            onTypeChange(type)
                            typeExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = entry.value,
            onValueChange = onValueChange,
            label = { Text(stringResource(entry.type.labelRes())) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.action_remove_contact),
            )
        }
    }
}

/** Возвращает строковый ресурс с названием типа контакта. */
fun ContactType.labelRes(): StringResource =
    when (this) {
        ContactType.PHONE -> Res.string.contact_type_phone
        ContactType.EMAIL -> Res.string.contact_type_email
        ContactType.TELEGRAM -> Res.string.contact_type_telegram
        ContactType.VK -> Res.string.contact_type_vk
        ContactType.FACEBOOK -> Res.string.contact_type_facebook
    }

/** Возвращает копию списка с заменённым элементом по индексу [index]. */
private fun List<ContactFormEntry>.replacedAt(index: Int, value: ContactFormEntry): List<ContactFormEntry> = mapIndexed { i, item -> if (i == index) value else item }

/** Возвращает копию списка без элемента по индексу [index]. */
private fun List<ContactFormEntry>.removedAt(index: Int): List<ContactFormEntry> = filterIndexed { i, _ -> i != index }
