package org.athletica.crm.components.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.core.money.formatted
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_logout
import org.athletica.crm.generated.resources.branch_switch_current
import org.athletica.crm.generated.resources.label_balance_value
import org.athletica.crm.generated.resources.settings_item_edit_profile
import org.athletica.crm.generated.resources.settings_item_org_balance
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource

/** Точка назначения, выбранная пользователем в [AccountMenu]. */
sealed class AccountMenuLink {
    /** Открыть экран редактирования профиля. */
    object EditProfile : AccountMenuLink()

    /** Открыть экран смены текущего филиала. */
    object SwitchBranch : AccountMenuLink()

    /** Открыть экран баланса организации. */
    object OrgBalance : AccountMenuLink()
}

/**
 * Аватар-кнопка в [androidx.compose.material3.TopAppBar], открывающая меню с информацией об аккаунте:
 * имя пользователя, организация, текущий филиал, баланс, переходы в настройки и выход.
 *
 * На COMPACT открывает [ModalBottomSheet], на MEDIUM/EXPANDED — [DropdownMenu]-панель.
 *
 * [me] — данные о текущем пользователе; пока `null`, показывается только аватар-плейсхолдер и выход.
 * [windowSize] — текущий breakpoint для адаптивного поведения.
 * [onNavigate] — вызывается при выборе пункта меню, ведущего на отдельный экран.
 * [onLogout] — вызывается при нажатии «Выйти».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMenu(
    api: ApiClient,
    me: AuthMeResponse?,
    windowSize: WindowSize,
    onNavigate: (AccountMenuLink) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPanel by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = modifier) {
        IconButton(onClick = { showPanel = true }) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Avatar(me?.avatarId, me?.name.orEmpty(), api)
            }
        }

        if (windowSize != WindowSize.COMPACT) {
            DropdownMenu(
                expanded = showPanel,
                onDismissRequest = { showPanel = false },
            ) {
                AccountMenuPanel(
                    api = api,
                    me = me,
                    onNavigate = { link ->
                        showPanel = false
                        onNavigate(link)
                    },
                    onLogout = {
                        showPanel = false
                        onLogout()
                    },
                    modifier = Modifier.width(320.dp),
                )
            }
        }
    }

    if (windowSize == WindowSize.COMPACT && showPanel) {
        ModalBottomSheet(
            onDismissRequest = { showPanel = false },
            sheetState = sheetState,
        ) {
            AccountMenuPanel(
                api = api,
                me = me,
                onNavigate = { link ->
                    showPanel = false
                    onNavigate(link)
                },
                onLogout = {
                    showPanel = false
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Содержимое панели аккаунта — шапка профиля, организация, филиал, выход.
 * Используется и в [DropdownMenu], и в [ModalBottomSheet].
 */
@Composable
private fun AccountMenuPanel(
    api: ApiClient,
    me: AuthMeResponse?,
    onNavigate: (AccountMenuLink) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AccountHeader(
            api = api,
            me = me,
            onClick = { onNavigate(AccountMenuLink.EditProfile) },
        )

        HorizontalDivider()

        if (me != null) {
            OrgSection(
                orgName = me.orgInfo.name,
                balanceFormatted = me.orgInfo.balance?.formatted,
                onClick = { onNavigate(AccountMenuLink.OrgBalance) },
            )

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                    )
                },
                overlineContent = {
                    Text(
                        text = stringResource(Res.string.branch_switch_current),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                headlineContent = {
                    Text(
                        text = me.currentBranch.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable { onNavigate(AccountMenuLink.SwitchBranch) },
            )

            HorizontalDivider()
        }

        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                )
            },
            headlineContent = { Text(stringResource(Res.string.action_logout)) },
            modifier = Modifier.clickable { onLogout() },
        )

        Spacer(Modifier.height(8.dp))
    }
}

/** Шапка панели — крупный аватар, имя и логин. По клику переходит на редактирование профиля. */
@Composable
private fun AccountHeader(
    api: ApiClient,
    me: AuthMeResponse?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = me != null) { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Avatar(me?.avatarId, me?.name.orEmpty(), api)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = me?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = me?.username.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (me != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.settings_item_edit_profile),
            )
        }
    }
}

/** Блок с названием организации и (опционально) её балансом. По клику открывает баланс. */
@Composable
private fun OrgSection(
    orgName: String,
    balanceFormatted: String?,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Business,
                contentDescription = null,
            )
        },
        headlineContent = {
            Text(
                text = orgName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent =
            balanceFormatted?.let { value ->
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.label_balance_value, value),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        trailingContent =
            if (balanceFormatted != null) {
                {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.settings_item_org_balance),
                    )
                }
            } else {
                null
            },
        modifier = Modifier.clickable(enabled = balanceFormatted != null) { onClick() },
    )
}
