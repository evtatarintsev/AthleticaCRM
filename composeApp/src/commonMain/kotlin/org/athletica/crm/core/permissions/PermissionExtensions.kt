package org.athletica.crm.core.permissions

import androidx.compose.runtime.Composable
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.permission_can_view_client_balance
import org.athletica.crm.generated.resources.permission_can_view_client_balance_description
import org.athletica.crm.generated.resources.permission_can_view_client_balance_name
import org.jetbrains.compose.resources.stringResource

/**
 * Возвращает локализованное наименование прав.
 */
@Composable
fun Permission.displayName(): String =
    when (this) {
        Permission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance_name)
    }

/**
 * Возвращает локализованное описание прав.
 */
@Composable
fun Permission.displayDescription(): String =
    when (this) {
        Permission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance_description)
    }

/**
 * Возвращает локализованный ярлык прав (краткое наименование).
 */
@Composable
fun Permission.label(): String =
    when (this) {
        Permission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance)
    }
