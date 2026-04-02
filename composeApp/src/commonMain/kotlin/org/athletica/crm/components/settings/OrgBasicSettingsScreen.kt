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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.platformAvailableTimezones
import org.athletica.crm.platformCurrentTimezone

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
    var name by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf(platformCurrentTimezone()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val availableZones = remember { platformAvailableTimezones() }
    var timezoneExpanded by remember { mutableStateOf(false) }
    var timezoneQuery by remember { mutableStateOf("") }
    val filteredZones =
        remember(timezoneQuery) {
            if (timezoneQuery.isEmpty()) {
                availableZones
            } else {
                availableZones.filter { it.contains(timezoneQuery, ignoreCase = true) }
            }
        }

    LaunchedEffect(Unit) {
        api.orgSettings().fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                        ApiClientError.Unauthenticated -> "Необходима авторизация"
                    }
            },
            ifRight = { settings ->
                name = settings.name
                timezone = settings.timezone
            },
        )
        isLoading = false
    }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            error = null
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Основные настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                api
                                    .updateOrgSettings(
                                        UpdateOrgSettingsRequest(
                                            name = name.trim(),
                                            timezone = timezone,
                                        ),
                                    ).fold(
                                        ifLeft = { err ->
                                            error =
                                                when (err) {
                                                    is ApiClientError.ValidationError -> err.message
                                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                                    ApiClientError.Unauthenticated -> "Необходима авторизация"
                                                }
                                        },
                                        ifRight = { onBack() },
                                    )
                                isSaving = false
                            }
                        },
                        enabled = name.isNotBlank() && !isLoading && !isSaving,
                    ) {
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

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
                label = { Text("Название организации") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = timezoneExpanded,
                onExpandedChange = { expanded ->
                    timezoneExpanded = expanded
                    if (!expanded) timezoneQuery = ""
                },
            ) {
                OutlinedTextField(
                    value = if (timezoneExpanded) timezoneQuery else timezone,
                    onValueChange = { query ->
                        timezoneQuery = query
                        timezoneExpanded = true
                    },
                    label = { Text("Часовой пояс") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timezoneExpanded) },
                    singleLine = true,
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier =
                        Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
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
