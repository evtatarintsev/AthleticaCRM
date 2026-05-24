package org.athletica.crm.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.components.auth.BranchSwitchState
import org.athletica.crm.components.auth.BranchSwitchViewModel
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.branch_switch_current
import org.athletica.crm.generated.resources.branch_switch_error
import org.athletica.crm.generated.resources.settings_item_switch_branch
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Сменить филиал».
 * Показывает список доступных пользователю филиалов; текущий помечен галочкой и некликабелен.
 * При выборе другого филиала переключает контекст пользователя через API и вызывает [onSwitched]
 * (для обновления данных профиля в шапке аккаунта в боковом меню), затем — [onBack].
 *
 * [currentBranchId] — id текущего активного филиала, нужен для пометки строки галочкой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchBranchScreen(
    api: ApiClient,
    currentBranchId: BranchId?,
    onBack: () -> Unit,
    onSwitched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            BranchSwitchViewModel(
                api = api,
                scope = scope,
                onSwitched = {
                    onSwitched()
                    onBack()
                },
            )
        }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_item_switch_branch)) },
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
    ) { innerPadding ->
        when (val state = viewModel.state) {
            is BranchSwitchState.Idle, is BranchSwitchState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }
            }

            is BranchSwitchState.Loaded -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    items(state.branches, key = { it.id.value }) { branch ->
                        val isCurrent = branch.id == currentBranchId
                        ListItem(
                            headlineContent = {
                                Text(branch.name, style = MaterialTheme.typography.bodyLarge)
                            },
                            trailingContent = {
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(Res.string.branch_switch_current),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier =
                                if (isCurrent) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth().clickable { viewModel.switchTo(branch.id) }
                                },
                        )
                        HorizontalDivider()
                    }
                }
            }

            is BranchSwitchState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.branch_switch_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
