package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.mobile.api.ApiError
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.account_currentSession
import org.athletica.mobile.resources.account_delete
import org.athletica.mobile.resources.account_deleteCancel
import org.athletica.mobile.resources.account_deleteConfirm
import org.athletica.mobile.resources.account_deleteWarning
import org.athletica.mobile.resources.account_lastSeen
import org.athletica.mobile.resources.account_logout
import org.athletica.mobile.resources.account_privacy
import org.athletica.mobile.resources.account_sessions
import org.athletica.mobile.resources.account_terminate
import org.athletica.mobile.resources.account_terms
import org.athletica.mobile.resources.account_unknownDevice
import org.athletica.mobile.resources.balance_title
import org.athletica.mobile.resources.memberships_title
import org.athletica.mobile.resources.profiles_newInvitations
import org.athletica.mobile.resources.profiles_switch
import org.athletica.mobile.resources.visits_title
import org.jetbrains.compose.resources.stringResource

/**
 * «Ещё» через [api]: переключатель профилей (с числом [newInvitations]), посещения, абонементы,
 * баланс и устройства — [onOpen]; документы [links]; выход и удаление аккаунта стирают сеанс
 * и профиль на устройстве и ведут на вход [onSignedOut].
 */
@Composable
fun MoreScreen(
    api: ClientApi,
    links: AppLinks,
    newInvitations: Int,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val switch = stringResource(Res.string.profiles_switch)
        Item(if (newInvitations > 0) "$switch · ${stringResource(Res.string.profiles_newInvitations, newInvitations)}" else switch) { onOpen(Route.Switcher) }
        Item(stringResource(Res.string.visits_title)) { onOpen(Route.Visits) }
        Item(stringResource(Res.string.memberships_title)) { onOpen(Route.Memberships) }
        Item(stringResource(Res.string.balance_title)) { onOpen(Route.Balance) }
        Item(stringResource(Res.string.account_sessions)) { onOpen(Route.Devices) }
        Item(stringResource(Res.string.account_terms)) { uri.openUri(links.terms) }
        Item(stringResource(Res.string.account_privacy)) { uri.openUri(links.privacy) }
        error?.let { ErrorText(it) }
        Item(stringResource(Res.string.account_logout)) {
            scope.launch {
                api.logout()
                onSignedOut()
            }
        }
        Item(stringResource(Res.string.account_delete)) { confirmDelete = true }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(Res.string.account_deleteWarning)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        api.deleteAccount().fold({
                            error = it
                            confirmDelete = false
                        }, { onSignedOut() })
                        busy = false
                    }
                }) { Text(stringResource(Res.string.account_deleteConfirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.account_deleteCancel)) } },
        )
    }
}

/** Пункт списка «Ещё» с подписью [label]; нажатие — [onClick]. */
@Composable
private fun Item(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) }
    HorizontalDivider()
}

/** Устройства аккаунта через [api]: где выполнен вход, с завершением сеансов других устройств. */
@Composable
fun DevicesScreen(api: ClientApi) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("sessions") { api.sessions() }
    var error by remember { mutableStateOf<ApiError?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.account_sessions), style = MaterialTheme.typography.headlineSmall)
        error?.let { ErrorText(it) }
        Loaded(loader) { response ->
            response.sessions.forEach { session ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.name ?: stringResource(Res.string.account_unknownDevice), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (session.current) {
                                    stringResource(Res.string.account_currentSession)
                                } else {
                                    stringResource(Res.string.account_lastSeen, session.lastSeenAt.shown)
                                },
                            )
                        }
                        if (!session.current) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    error = null
                                    api.terminate(session.id).fold({ error = it }, { loader.reload() })
                                }
                            }) { Text(stringResource(Res.string.account_terminate)) }
                        }
                    }
                }
            }
        }
    }
}
