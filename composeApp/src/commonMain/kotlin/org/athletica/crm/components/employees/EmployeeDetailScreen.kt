package org.athletica.crm.components.employees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.jetbrains.compose.resources.stringResource

/** Экран деталей сотрудника. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    employeeId: EmployeeId,
    api: ApiClient,
    onBack: () -> Unit,
    onEdit: (EmployeeDetailResponse) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var employee by remember { mutableStateOf<EmployeeDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(employeeId) {
        isLoading = true
        api.employeeDetail(employeeId).fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.Unauthenticated -> "Сессия истекла"
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                    }
            },
            ifRight = { employee = it },
        )
        isLoading = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(employee?.name ?: "Сотрудник") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    employee?.let { emp ->
                        IconButton(onClick = { onEdit(emp) }) {
                            Icon(Icons.Default.Edit, "Редактировать")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                error != null ->
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )

                employee != null ->
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            EmployeeDetailHeader(employee!!, api)
                            Spacer(Modifier.height(24.dp))
                        }

                        item {
                            EmployeeInfoSection(employee!!)
                            Spacer(Modifier.height(24.dp))
                        }

                        if (employee!!.roles.isNotEmpty()) {
                            item {
                                EmployeeRolesSection(employee!!)
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun EmployeeDetailHeader(employee: EmployeeDetailResponse, api: ApiClient) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (employee.avatarId != null) {
                Avatar(employee.avatarId, employee.name, api)
            } else {
                Text(
                    text = employee.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = employee.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (employee.isOwner) {
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "Владелец",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val statusColor = if (employee.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Text(
            text = if (employee.isActive) "Активен" else "Неактивен",
            color = statusColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EmployeeInfoSection(employee: EmployeeDetailResponse) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Контактная информация",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            employee.email?.let { email ->
                InfoRow("Email", email)
            }

            employee.phoneNo?.let { phone ->
                InfoRow("Телефон", phone)
            }

            if (employee.email == null && employee.phoneNo == null) {
                Text(
                    text = "Контактная информация не указана",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmployeeRolesSection(employee: EmployeeDetailResponse) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Роли",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            employee.roles.forEach { role ->
                Text(
                    text = "• ${role.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
