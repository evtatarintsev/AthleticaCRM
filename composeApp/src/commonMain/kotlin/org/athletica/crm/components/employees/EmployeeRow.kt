package org.athletica.crm.components.employees

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Карточка сотрудника для COMPACT-вёрстки списка: аватар, имя, статус активности, чекбокс.
 * Аватар подгружается лениво через [api] только при появлении строки на экране.
 */
@Composable
fun EmployeeRow(
    employee: EmployeeListItem,
    api: ApiClient,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        EmployeeAvatar(employee = employee, api = api)
        Spacer(Modifier.width(12.dp))
        Text(
            text = employee.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        EmployeeStatusBadge(
            isActive = employee.isActive,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Круглый аватар сотрудника фиксированного размера 36dp. */
@Composable
fun EmployeeAvatar(
    employee: EmployeeListItem,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Avatar(employee.avatarId, employee.name, api)
    }
}

/** Бейдж со статусом активности сотрудника (активен / неактивен). */
@Composable
fun EmployeeStatusBadge(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (isActive) Res.string.employee_status_active else Res.string.employee_status_inactive)
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
