package org.athletica.crm.core.permissions

import androidx.compose.runtime.Composable
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.permission_can_manage_org_balance
import org.athletica.crm.generated.resources.permission_can_manage_org_balance_description
import org.athletica.crm.generated.resources.permission_can_manage_org_balance_name
import org.athletica.crm.generated.resources.permission_can_manage_tasks
import org.athletica.crm.generated.resources.permission_can_manage_tasks_description
import org.athletica.crm.generated.resources.permission_can_manage_tasks_name
import org.athletica.crm.generated.resources.permission_can_view_all_tasks
import org.athletica.crm.generated.resources.permission_can_view_all_tasks_description
import org.athletica.crm.generated.resources.permission_can_view_all_tasks_name
import org.athletica.crm.generated.resources.permission_can_view_client_balance
import org.athletica.crm.generated.resources.permission_can_view_client_balance_description
import org.athletica.crm.generated.resources.permission_can_view_client_balance_name
import org.jetbrains.compose.resources.stringResource

/**
 * Возвращает локализованное наименование прав.
 */
@Composable
fun UserPermission.displayName(): String =
    when (this) {
        UserPermission.CAN_MANAGE_ORG_BALANCE -> stringResource(Res.string.permission_can_manage_org_balance_name)
        UserPermission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance_name)
        UserPermission.CAN_VIEW_ALL_TASKS -> stringResource(Res.string.permission_can_view_all_tasks_name)
        UserPermission.CAN_MANAGE_TASKS -> stringResource(Res.string.permission_can_manage_tasks_name)
    }

/**
 * Возвращает локализованное описание прав.
 */
@Composable
fun UserPermission.displayDescription(): String =
    when (this) {
        UserPermission.CAN_MANAGE_ORG_BALANCE -> stringResource(Res.string.permission_can_manage_org_balance_description)
        UserPermission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance_description)
        UserPermission.CAN_VIEW_ALL_TASKS -> stringResource(Res.string.permission_can_view_all_tasks_description)
        UserPermission.CAN_MANAGE_TASKS -> stringResource(Res.string.permission_can_manage_tasks_description)
    }

/**
 * Возвращает локализованный ярлык прав (краткое наименование).
 */
@Composable
fun UserPermission.label(): String =
    when (this) {
        UserPermission.CAN_MANAGE_ORG_BALANCE -> stringResource(Res.string.permission_can_manage_org_balance)
        UserPermission.CAN_VIEW_CLIENT_BALANCE -> stringResource(Res.string.permission_can_view_client_balance)
        UserPermission.CAN_VIEW_ALL_TASKS -> stringResource(Res.string.permission_can_view_all_tasks)
        UserPermission.CAN_MANAGE_TASKS -> stringResource(Res.string.permission_can_manage_tasks)
    }
