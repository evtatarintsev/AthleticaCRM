package org.athletica.crm.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.label_org_name
import org.athletica.crm.generated.resources.label_timezone
import org.athletica.crm.generated.resources.settings_item_basic_settings
import org.athletica.crm.platformAvailableTimezones
import org.athletica.crm.platformCurrentTimezone
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Основные настройки» организации.
 * Загружает текущие название и часовой пояс через [api], позволяет их изменить и сохранить.
 *
 * [onBack] — переход назад (вызывается и после успешного сохранения).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgBasicSettingsScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { OrgBasicSettingsViewModel(api, scope) { onBack() } }

    var name by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf(platformCurrentTimezone()) }

    LaunchedEffect(viewModel.loadState) {
        val ls = viewModel.loadState as? OrgSettingsLoadState.Loaded ?: return@LaunchedEffect
        name = ls.name
        timezone = ls.timezone
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val isLoading = viewModel.loadState is OrgSettingsLoadState.Loading
    val isSaving = viewModel.saveState is OrgSettingsSaveState.Saving
    val saveError = (viewModel.saveState as? OrgSettingsSaveState.Error)?.error
    val saveErrorMessage = saveError?.message()

    val availableZones = remember { platformAvailableTimezones() }
    var timezoneExpanded by remember { mutableStateOf(false) }
    var timezoneQuery by remember { mutableStateOf("") }
    val filteredZones =
        remember(timezoneQuery) {
            if (timezoneQuery.isEmpty()) availableZones else availableZones.filter { it.contains(timezoneQuery, ignoreCase = true) }
        }

    LaunchedEffect(saveErrorMessage) {
        if (saveErrorMessage != null) {
            snackbarHostState.showSnackbar(saveErrorMessage)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_item_basic_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.onSave(name, timezone) },
                        enabled = name.isNotBlank() && !isLoading && !isSaving,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val ls = viewModel.loadState) {
            is OrgSettingsLoadState.Loading ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }

            is OrgSettingsLoadState.Error ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    Text(
                        text = ls.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

            is OrgSettingsLoadState.Loaded ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.label_org_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ExposedDropdownMenuBox(
                        expanded = timezoneExpanded,
                        onExpandedChange = { expanded ->
                            timezoneExpanded = expanded
                            if (!expanded) {
                                timezoneQuery = ""
                            }
                        },
                    ) {
                        OutlinedTextField(
                            value = if (timezoneExpanded) timezoneQuery else timezone,
                            onValueChange = { query ->
                                timezoneQuery = query
                                timezoneExpanded = true
                            },
                            label = { Text(stringResource(Res.string.label_timezone)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timezoneExpanded) },
                            singleLine = true,
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier =
                                Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = timezoneExpanded,
                            onDismissRequest = { timezoneExpanded = false },
                        ) {
                            filteredZones.take(100).forEach { zone ->
                                DropdownMenuItem(
                                    text = { Text(zone) },
                                    onClick = {
                                        timezone = zone
                                        timezoneExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }
}
