package org.athletica.crm.components.employees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.employee_status_active
import org.athletica.crm.generated.resources.employee_status_inactive
import org.jetbrains.compose.resources.stringResource

/**
 * Строка сотрудника в списке: аватар, имя, статус активности, чекбокс.
 * Аватар подгружается лениво через [api] только при появлении строки на экране.
 */
@Composable
fun EmployeeRow(
    employee: EmployeeListItem,
    api: ApiClient,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val statusLabel = stringResource(if (employee.isActive) Res.string.employee_status_active else Res.string.employee_status_inactive)
    val statusColor = if (employee.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // Аватар
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Avatar(employee.avatarId, employee.name, api)
        }

        Spacer(Modifier.width(12.dp))

        // Имя растягивается
        Text(
            text = employee.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Статус
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = statusColor.copy(alpha = 0.12f),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        // Чекбокс — trailing
        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )
    }
}
