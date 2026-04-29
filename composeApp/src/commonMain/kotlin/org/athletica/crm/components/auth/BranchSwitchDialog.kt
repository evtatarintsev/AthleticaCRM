package org.athletica.crm.components.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.branch_switch_title
import org.jetbrains.compose.resources.stringResource

/**
 * Диалог выбора филиала для переключения.
 * Отображает список доступных филиалов из [state] и вызывает [onSelect] при выборе.
 * [onDismiss] вызывается при закрытии диалога без выбора.
 */
@Composable
fun BranchSwitchDialog(
    state: BranchSwitchState,
    onSelect: (branchId: org.athletica.crm.core.entityids.BranchId) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.branch_switch_title)) },
        text = {
            when (state) {
                is BranchSwitchState.Idle, is BranchSwitchState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                }
                is BranchSwitchState.Loaded -> {
                    Column {
                        state.branches.forEachIndexed { index, branch ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            ListItem(
                                headlineContent = { Text(branch.name, style = MaterialTheme.typography.bodyLarge) },
                                modifier = Modifier.clickable { onSelect(branch.id) },
                            )
                        }
                    }
                }
                is BranchSwitchState.Error -> {
                    Text(
                        text = "Не удалось загрузить филиалы",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
