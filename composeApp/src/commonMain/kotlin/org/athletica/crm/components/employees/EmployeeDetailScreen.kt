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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_edit
import org.athletica.crm.generated.resources.employee_status_active
import org.athletica.crm.generated.resources.employee_status_inactive
import org.athletica.crm.generated.resources.label_employee
import org.athletica.crm.generated.resources.label_employee_owner
import org.athletica.crm.generated.resources.label_employee_phone
import org.athletica.crm.generated.resources.label_no_contact_info
import org.athletica.crm.generated.resources.section_contact_info
import org.athletica.crm.generated.resources.section_roles
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
    val scope = rememberCoroutineScope()
    val viewModel = remember { EmployeeDetailViewModel(api, employeeId, scope) }

    val title =
        (viewModel.state as? EmployeeDetailState.Loaded)?.employee?.name
            ?: stringResource(Res.string.label_employee)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (viewModel.state is EmployeeDetailState.Loaded) {
                        val emp = (viewModel.state as EmployeeDetailState.Loaded).employee
                        IconButton(onClick = { onEdit(emp) }) {
                            Icon(Icons.Default.Edit, stringResource(Res.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = viewModel.state) {
                is EmployeeDetailState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is EmployeeDetailState.Error ->
                    Text(
                        text = s.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is EmployeeDetailState.Loaded ->
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            EmployeeDetailHeader(s.employee, api)
                            Spacer(Modifier.height(24.dp))
                        }

                        item {
                            EmployeeInfoSection(s.employee)
                            Spacer(Modifier.height(24.dp))
                        }

                        if (s.employee.roles.isNotEmpty()) {
                            item {
                                EmployeeRolesSection(s.employee)
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun EmployeeDetailHeader(
    employee: EmployeeDetailResponse,
    api: ApiClient,
) {
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
                    text = stringResource(Res.string.label_employee_owner),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val statusColor = if (employee.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Text(
            text = if (employee.isActive) stringResource(Res.string.employee_status_active) else stringResource(Res.string.employee_status_inactive),
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
                text = stringResource(Res.string.section_contact_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            employee.email?.let { email ->
                InfoRow("Email", email)
            }

            employee.phoneNo?.let { phone ->
                InfoRow(stringResource(Res.string.label_employee_phone), phone)
            }

            if (employee.email == null && employee.phoneNo == null) {
                Text(
                    text = stringResource(Res.string.label_no_contact_info),
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
                text = stringResource(Res.string.section_roles),
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
