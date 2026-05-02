package org.athletica.crm.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.api.schemas.customfields.CustomFieldTypeConfig
import org.athletica.crm.api.schemas.customfields.toJson
import org.athletica.crm.api.schemas.customfields.toTypeConfig
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.custom_field_config_section
import org.athletica.crm.generated.resources.custom_field_error_key_format
import org.athletica.crm.generated.resources.custom_field_error_key_required
import org.athletica.crm.generated.resources.custom_field_error_label_required
import org.athletica.crm.generated.resources.custom_field_error_max_must_be_greater
import org.athletica.crm.generated.resources.custom_field_error_min_must_be_less
import org.athletica.crm.generated.resources.custom_field_error_numeric_value
import org.athletica.crm.generated.resources.custom_field_error_options_required
import org.athletica.crm.generated.resources.custom_field_hint_options
import org.athletica.crm.generated.resources.custom_field_hint_regex
import org.athletica.crm.generated.resources.label_custom_field_key
import org.athletica.crm.generated.resources.label_custom_field_label
import org.athletica.crm.generated.resources.label_custom_field_max_length
import org.athletica.crm.generated.resources.label_custom_field_max_value
import org.athletica.crm.generated.resources.label_custom_field_min_length
import org.athletica.crm.generated.resources.label_custom_field_min_value
import org.athletica.crm.generated.resources.label_custom_field_options
import org.athletica.crm.generated.resources.label_custom_field_type
import org.athletica.crm.generated.resources.label_required
import org.athletica.crm.generated.resources.label_searchable
import org.athletica.crm.generated.resources.label_sortable
import org.athletica.crm.generated.resources.screen_custom_attribute_create
import org.athletica.crm.generated.resources.screen_custom_attribute_edit
import org.athletica.crm.generated.resources.type_boolean
import org.athletica.crm.generated.resources.type_date
import org.athletica.crm.generated.resources.type_email
import org.athletica.crm.generated.resources.type_number
import org.athletica.crm.generated.resources.type_phone
import org.athletica.crm.generated.resources.type_select
import org.athletica.crm.generated.resources.type_string
import org.athletica.crm.generated.resources.type_url
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val fieldKeyRegex = Regex("[a-z_]+")

private data class CustomFieldTypeItem(
    val value: String,
    val labelResource: StringResource,
)

/** Экран создания и редактирования дополнительного атрибута клиента. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldAttributeCreateScreen(
    onBack: () -> Unit,
    onSave: (updated: CustomFieldDefinitionSchema, isNew: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialAttribute: CustomFieldDefinitionSchema? = null,
    error: String? = null,
    isLoading: Boolean = false,
) {
    val isEditMode = initialAttribute != null
    val initialConfig = initialAttribute?.config?.toTypeConfig(initialAttribute.fieldType) ?: CustomFieldTypeConfig.DefaultConfig

    var fieldKey by remember(initialAttribute?.fieldKey) { mutableStateOf(initialAttribute?.fieldKey ?: "") }
    var label by remember(initialAttribute?.fieldKey) { mutableStateOf(initialAttribute?.label ?: "") }
    var fieldType by remember(initialAttribute?.fieldKey) { mutableStateOf(initialAttribute?.fieldType ?: "text") }
    var isRequired by remember(initialAttribute?.fieldKey) { mutableStateOf(initialAttribute?.isRequired ?: false) }
    var isSearchable by remember(initialAttribute?.fieldKey) { mutableStateOf(initialAttribute?.isSearchable ?: false) }
    var isSortable by remember(initialAttribute?.fieldKey) { mutableStateOf(initialAttribute?.isSortable ?: false) }

    var selectOptions by remember(initialAttribute?.fieldKey) {
        mutableStateOf((initialConfig as? CustomFieldTypeConfig.SelectConfig)?.options?.joinToString("\n") ?: "")
    }
    var minValueInput by remember(initialAttribute?.fieldKey) {
        mutableStateOf((initialConfig as? CustomFieldTypeConfig.NumberConfig)?.minValue?.toString() ?: "")
    }
    var maxValueInput by remember(initialAttribute?.fieldKey) {
        mutableStateOf((initialConfig as? CustomFieldTypeConfig.NumberConfig)?.maxValue?.toString() ?: "")
    }
    var minLengthInput by remember(initialAttribute?.fieldKey) {
        mutableStateOf((initialConfig as? CustomFieldTypeConfig.TextConfig)?.minLength?.toString() ?: "")
    }
    var maxLengthInput by remember(initialAttribute?.fieldKey) {
        mutableStateOf((initialConfig as? CustomFieldTypeConfig.TextConfig)?.maxLength?.toString() ?: "")
    }

    val trimmedFieldKey = fieldKey.trim()
    val trimmedLabel = label.trim()
    val parsedOptions = parseOptions(selectOptions)
    val parsedMinValue = minValueInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
    val parsedMaxValue = maxValueInput.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
    val parsedMinLength = minLengthInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedMaxLength = maxLengthInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    val isFieldKeyInvalid = trimmedFieldKey.isNotEmpty() && !trimmedFieldKey.matches(fieldKeyRegex)
    val isMinValueInvalid = minValueInput.trim().isNotEmpty() && parsedMinValue == null
    val isMaxValueInvalid = maxValueInput.trim().isNotEmpty() && parsedMaxValue == null
    val isMinLengthInvalid = minLengthInput.trim().isNotEmpty() && parsedMinLength == null
    val isMaxLengthInvalid = maxLengthInput.trim().isNotEmpty() && parsedMaxLength == null

    val numberRangeInvalid =
        fieldType == "number" &&
            parsedMinValue != null &&
            parsedMaxValue != null &&
            parsedMinValue >= parsedMaxValue
    val textRangeInvalid =
        fieldType == "text" &&
            parsedMinLength != null &&
            parsedMaxLength != null &&
            parsedMinLength >= parsedMaxLength

    val fieldKeyError =
        when {
            trimmedFieldKey.isBlank() -> stringResource(Res.string.custom_field_error_key_required)
            isFieldKeyInvalid -> stringResource(Res.string.custom_field_error_key_format)
            else -> null
        }
    val labelError = if (trimmedLabel.isBlank()) stringResource(Res.string.custom_field_error_label_required) else null
    val optionsError =
        if (fieldType == "select" && parsedOptions.isEmpty()) {
            stringResource(Res.string.custom_field_error_options_required)
        } else {
            null
        }

    val canSave =
        fieldKeyError == null &&
            labelError == null &&
            optionsError == null &&
            !isMinValueInvalid &&
            !isMaxValueInvalid &&
            !isMinLengthInvalid &&
            !isMaxLengthInvalid &&
            !numberRangeInvalid &&
            !textRangeInvalid &&
            !isLoading

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) {
                            stringResource(Res.string.screen_custom_attribute_edit)
                        } else {
                            stringResource(Res.string.screen_custom_attribute_create)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                CustomFieldDefinitionSchema(
                                    fieldKey = trimmedFieldKey,
                                    label = trimmedLabel,
                                    fieldType = fieldType,
                                    config =
                                        configForType(
                                            fieldType = fieldType,
                                            options = parsedOptions,
                                            minValue = parsedMinValue,
                                            maxValue = parsedMaxValue,
                                            minLength = parsedMinLength,
                                            maxLength = parsedMaxLength,
                                        ).toJson(),
                                    isRequired = isRequired,
                                    isSearchable = isSearchable,
                                    isSortable = isSortable,
                                ),
                                !isEditMode,
                            )
                        },
                        enabled = canSave,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = fieldKey,
                onValueChange = { fieldKey = it },
                label = { Text(stringResource(Res.string.label_custom_field_key)) },
                singleLine = true,
                enabled = !isEditMode,
                isError = fieldKeyError != null,
                supportingText = {
                    Text(fieldKeyError ?: stringResource(Res.string.custom_field_hint_regex))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(Res.string.label_custom_field_label)) },
                singleLine = true,
                isError = labelError != null,
                supportingText = {
                    labelError?.let {
                        Text(it)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldTypeSelector(
                value = fieldType,
                onValueChange = { newType ->
                    if (newType != fieldType) {
                        fieldType = newType
                        selectOptions = ""
                        minValueInput = ""
                        maxValueInput = ""
                        minLengthInput = ""
                        maxLengthInput = ""
                    }
                },
            )

            ToggleField(
                title = stringResource(Res.string.label_required),
                checked = isRequired,
                onCheckedChange = { isRequired = it },
            )
            ToggleField(
                title = stringResource(Res.string.label_searchable),
                checked = isSearchable,
                onCheckedChange = { isSearchable = it },
            )
            ToggleField(
                title = stringResource(Res.string.label_sortable),
                checked = isSortable,
                onCheckedChange = { isSortable = it },
            )

            if (fieldType in setOf("select", "number", "text")) {
                Text(
                    text = stringResource(Res.string.custom_field_config_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (fieldType == "select") {
                OutlinedTextField(
                    value = selectOptions,
                    onValueChange = { selectOptions = it },
                    label = { Text(stringResource(Res.string.label_custom_field_options)) },
                    placeholder = { Text(stringResource(Res.string.custom_field_hint_options)) },
                    minLines = 4,
                    isError = optionsError != null,
                    supportingText = {
                        optionsError?.let {
                            Text(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (fieldType == "number") {
                OutlinedTextField(
                    value = minValueInput,
                    onValueChange = { minValueInput = it },
                    label = { Text(stringResource(Res.string.label_custom_field_min_value)) },
                    singleLine = true,
                    isError = isMinValueInvalid || numberRangeInvalid,
                    supportingText = {
                        when {
                            isMinValueInvalid -> Text(stringResource(Res.string.custom_field_error_numeric_value))
                            numberRangeInvalid -> Text(stringResource(Res.string.custom_field_error_min_must_be_less))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxValueInput,
                    onValueChange = { maxValueInput = it },
                    label = { Text(stringResource(Res.string.label_custom_field_max_value)) },
                    singleLine = true,
                    isError = isMaxValueInvalid || numberRangeInvalid,
                    supportingText = {
                        when {
                            isMaxValueInvalid -> Text(stringResource(Res.string.custom_field_error_numeric_value))
                            numberRangeInvalid -> Text(stringResource(Res.string.custom_field_error_max_must_be_greater))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (fieldType == "text") {
                OutlinedTextField(
                    value = minLengthInput,
                    onValueChange = { minLengthInput = it },
                    label = { Text(stringResource(Res.string.label_custom_field_min_length)) },
                    singleLine = true,
                    isError = isMinLengthInvalid || textRangeInvalid,
                    supportingText = {
                        when {
                            isMinLengthInvalid -> Text(stringResource(Res.string.custom_field_error_numeric_value))
                            textRangeInvalid -> Text(stringResource(Res.string.custom_field_error_min_must_be_less))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxLengthInput,
                    onValueChange = { maxLengthInput = it },
                    label = { Text(stringResource(Res.string.label_custom_field_max_length)) },
                    singleLine = true,
                    isError = isMaxLengthInvalid || textRangeInvalid,
                    supportingText = {
                        when {
                            isMaxLengthInvalid -> Text(stringResource(Res.string.custom_field_error_numeric_value))
                            textRangeInvalid -> Text(stringResource(Res.string.custom_field_error_max_must_be_greater))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Выпадающий список выбора типа дополнительного атрибута. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldTypeSelector(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = fieldTypeItems()
    val selectedText = items.firstOrNull { it.value == value }?.let { stringResource(it.labelResource) } ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.label_custom_field_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(item.labelResource)) },
                    onClick = {
                        onValueChange(item.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Элемент списка с переключателем булевого признака атрибута. */
@Composable
private fun ToggleField(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/** Возвращает набор поддерживаемых типов пользовательского поля. */
private fun fieldTypeItems(): List<CustomFieldTypeItem> =
    listOf(
        CustomFieldTypeItem(value = "text", labelResource = Res.string.type_string),
        CustomFieldTypeItem(value = "number", labelResource = Res.string.type_number),
        CustomFieldTypeItem(value = "date", labelResource = Res.string.type_date),
        CustomFieldTypeItem(value = "select", labelResource = Res.string.type_select),
        CustomFieldTypeItem(value = "boolean", labelResource = Res.string.type_boolean),
        CustomFieldTypeItem(value = "phone", labelResource = Res.string.type_phone),
        CustomFieldTypeItem(value = "email", labelResource = Res.string.type_email),
        CustomFieldTypeItem(value = "url", labelResource = Res.string.type_url),
    )

/** Формирует список опций для select-поля из текста, разделённого переводами строк и запятыми. */
private fun parseOptions(value: String): List<String> =
    value
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** Возвращает конфигурацию, соответствующую выбранному [fieldType]. */
private fun configForType(
    fieldType: String,
    options: List<String>,
    minValue: Long?,
    maxValue: Long?,
    minLength: Int?,
    maxLength: Int?,
): CustomFieldTypeConfig =
    when (fieldType) {
        "select" -> CustomFieldTypeConfig.SelectConfig(options = options)
        "number" -> CustomFieldTypeConfig.NumberConfig(minValue = minValue, maxValue = maxValue)
        "text" -> CustomFieldTypeConfig.TextConfig(minLength = minLength, maxLength = maxLength)
        "date" -> CustomFieldTypeConfig.DateConfig
        else -> CustomFieldTypeConfig.DefaultConfig
    }
