package org.athletica.crm.components.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_create_task
import org.athletica.crm.generated.resources.label_task_description
import org.athletica.crm.generated.resources.label_task_title
import org.jetbrains.compose.resources.stringResource

/**
 * Экран создания новой задачи.
 * [onBack] — возврат без создания, [onCreated] — возврат после успешного создания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreateScreen(
    api: ApiClient,
    onBack: () -> Unit = {},
    onCreated: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { TaskCreateViewModel(api, scope) }

    LaunchedEffect(viewModel.state) {
        if (viewModel.state is TaskCreateState.Success) {
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.action_create_task)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = viewModel.form.title,
                onValueChange = { viewModel.updateForm { copy(title = it) } },
                label = { Text(stringResource(Res.string.label_task_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = viewModel.form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = { Text(stringResource(Res.string.label_task_description)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.submit() },
                enabled = viewModel.form.isValid && viewModel.state !is TaskCreateState.Submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (viewModel.state is TaskCreateState.Submitting) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Res.string.action_create_task))
                }
            }

            if (viewModel.state is TaskCreateState.Error) {
                Text(
                    text = (viewModel.state as TaskCreateState.Error).message,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
