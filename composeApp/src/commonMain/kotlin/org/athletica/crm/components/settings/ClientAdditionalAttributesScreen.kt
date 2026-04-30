package org.athletica.crm.components.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.components.avatar.TextAvatar
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_attribute
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.column_required
import org.athletica.crm.generated.resources.column_type
import org.athletica.crm.generated.resources.label_selected_count
import org.athletica.crm.generated.resources.screen_client_additional_attributes
import org.athletica.crm.generated.resources.type_number
import org.athletica.crm.generated.resources.type_select
import org.athletica.crm.generated.resources.type_string
import org.jetbrains.compose.resources.stringResource

enum class AttributeType {
    STRING,
    NUMBER,
    SELECT,
}

@Composable
private fun AttributeType.displayName(): String =
    when (this) {
        AttributeType.STRING -> stringResource(Res.string.type_string)
        AttributeType.NUMBER -> stringResource(Res.string.type_number)
        AttributeType.SELECT -> stringResource(Res.string.type_select)
    }

data class ClientAttribute(
    val id: Int,
    val name: String,
    val type: AttributeType,
    val required: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientAdditionalAttributesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hardcodedAttributes =
        remember {
            listOf(
                ClientAttribute(1, "адрес", AttributeType.STRING, false),
                ClientAttribute(2, "номер договора", AttributeType.NUMBER, false),
                ClientAttribute(3, "разряд", AttributeType.SELECT, true),
            )
        }

    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

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
            if (selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_add_attribute)) },
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                BottomAppBar {
                    Text(
                        text = stringResource(Res.string.label_selected_count, selectedIds.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp).weight(1f),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            ClientAttributesTableHeader()
            HorizontalDivider()

            LazyColumn(
                contentPadding =
                    PaddingValues(
                        top = 4.dp,
                        bottom = if (selectedIds.isNotEmpty()) 80.dp else 4.dp,
                    ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(hardcodedAttributes, key = { it.id }) { attribute ->
                    ClientAttributeRow(
                        attribute = attribute,
                        selected = attribute.id in selectedIds,
                        onCheckedChange = { checked ->
                            selectedIds =
                                if (checked) {
                                    selectedIds + attribute.id
                                } else {
                                    selectedIds - attribute.id
                                }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ClientAttributesTableHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
    ) {
        Spacer(Modifier.size(40.dp))

        Text(
            text = "Название",
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

@Composable
private fun ClientAttributeRow(
    attribute: ClientAttribute,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = attribute.name,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = attribute.type.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.8f),
                )
                Text(
                    text = if (attribute.required) "Да" else "Нет",
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
                TextAvatar(attribute.name)
            }
        },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
