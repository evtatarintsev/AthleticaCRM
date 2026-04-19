package org.athletica.crm.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.athletica.crm.core.entityids.EntityId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_photo
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.action_select_photo
import org.athletica.crm.generated.resources.label_name
import org.jetbrains.compose.resources.stringResource

/**
 * Экран добавления или редактирования записи в справочнике.
 *
 * [title] — заголовок экрана (например, "Новый источник" или "Изменить дисциплину").
 * [initialItem] — если передан, экран работает в режиме редактирования с предзаполненными данными.
 * [onBack] — переход назад без сохранения.
 * [onSave] — сохранение: передаёт готовый [DirectoryItem].
 * [error] — текст ошибки от API, отображается под полем ввода.
 * [isLoading] — блокирует кнопку «Сохранить» на время запроса.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : EntityId> DirectoryItemCreateScreen(
    title: String,
    onBack: () -> Unit,
    onSave: (DirectoryItem<T>) -> Unit,
    modifier: Modifier = Modifier,
    initialItem: DirectoryItem<T>? = null,
    error: String? = null,
    isLoading: Boolean = false,
    newId: () -> T,
) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    // photoUrl будет заполнен после реализации загрузки файлов
    var photoSelected by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                DirectoryItem(
                                    id = initialItem?.id ?: newId(),
                                    name = name.trim(),
                                    photoUrl = initialItem?.photoUrl,
                                ),
                            )
                        },
                        enabled = name.isNotBlank() && !isLoading,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Фото
            PhotoPickerPlaceholder(
                selected = photoSelected,
                name = name,
                onClick = { photoSelected = !photoSelected },
            )

            // Название
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.label_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Заглушка выбора фото — круглая область с иконкой камеры.
 * При наличии [name] показывает инициал вместо иконки (имитация загруженного фото).
 * Реальная загрузка файла подключается отдельно через platform-specific file picker.
 */
@Composable
private fun PhotoPickerPlaceholder(
    selected: Boolean,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasInitial = name.isNotBlank()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected || hasInitial) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(onClick = onClick),
        ) {
            if (hasInitial) {
                Text(
                    text = name.first().uppercaseChar().toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(Res.string.action_select_photo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Text(
            text = stringResource(Res.string.action_add_photo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
