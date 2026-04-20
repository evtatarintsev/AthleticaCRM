package org.athletica.crm.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.employees.CreateRoleRequest
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.api.schemas.employees.UpdateRoleRequest
import org.athletica.crm.core.permissions.label
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_role
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.roles_empty
import org.athletica.crm.generated.resources.screen_roles
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * Экран «Роли» — список ролей организации с набором прав для каждой.
 *
 * [api] — клиент API.
 * [onBack] — callback возврата на экран настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var roles by remember { mutableStateOf<List<RoleItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedRoleForEdit by remember { mutableStateOf<RoleItem?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    if (showCreate) {
        RoleCreateScreen(
            onBack = { showCreate = false },
            onSave = { name, permissions ->
                scope.launch {
                    isLoading = true
                    error = null
                    api.createRole(CreateRoleRequest(Uuid.random(), name, permissions)).fold(
                        ifLeft = { err: ApiClientError ->
                            error =
                                when (err) {
                                    is ApiClientError.ValidationError -> err.message
                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                    ApiClientError.Unauthenticated -> "Необходима авторизация"
                                }
                            isLoading = false
                        },
                        ifRight = {
                            showCreate = false
                            refreshKey++
                        },
                    )
                }
            },
            modifier = modifier,
        )
        return
    }

    if (selectedRoleForEdit != null) {
        RoleCreateScreen(
            onBack = { selectedRoleForEdit = null },
            onSave = { name, permissions ->
                scope.launch {
                    isLoading = true
                    error = null
                    api.updateRole(UpdateRoleRequest(selectedRoleForEdit!!.id, name, permissions)).fold(
                        ifLeft = { err: ApiClientError ->
                            error =
                                when (err) {
                                    is ApiClientError.ValidationError -> err.message
                                    is ApiClientError.Unavailable -> "Сервис недоступен"
                                    ApiClientError.Unauthenticated -> "Необходима авторизация"
                                }
                            isLoading = false
                        },
                        ifRight = {
                            selectedRoleForEdit = null
                            refreshKey++
                        },
                    )
                }
            },
            initialName = selectedRoleForEdit!!.name,
            initialPermissions = selectedRoleForEdit!!.permissions,
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(refreshKey) {
        isLoading = true
        error = null
        api.roles().fold(
            ifLeft = { err: ApiClientError ->
                error =
                    when (err) {
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                        ApiClientError.Unauthenticated -> "Необходима авторизация"
                    }
            },
            ifRight = { response -> roles = response.roles },
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_roles)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.action_add_role)) },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when {
            isLoading ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }

            error != null ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

            roles.isEmpty() ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    Text(
                        text = stringResource(Res.string.roles_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else ->
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    items(roles) { role ->
                        RoleCard(role, onEdit = { selectedRoleForEdit = role })
                    }
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleCard(role: RoleItem, onEdit: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = role.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (role.permissions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    role.permissions.forEach { permission ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(permission.label()) },
                        )
                    }
                }
            }
        }
    }
}
