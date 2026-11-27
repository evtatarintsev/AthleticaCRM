package org.athletica.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.mobile.api.ApiError
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.account_logout
import org.athletica.mobile.resources.app_back
import org.athletica.mobile.resources.invitations_clients
import org.athletica.mobile.resources.invitations_connect
import org.athletica.mobile.resources.invitations_hint
import org.athletica.mobile.resources.invitations_invitedAt
import org.athletica.mobile.resources.invitations_notMe
import org.athletica.mobile.resources.invitations_title
import org.athletica.mobile.resources.profiles_empty
import org.athletica.mobile.resources.profiles_isolation
import org.athletica.mobile.resources.profiles_newInvitations
import org.athletica.mobile.resources.profiles_title
import org.athletica.mobile.session.ProfileKey
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Ключ выбора профиля [this]. */
val ClientAppProfileSchema.key: ProfileKey get() = ProfileKey(orgId, clientId)

/**
 * Вошедший человек через [api]: приглашения, пока нет подключённых профилей, иначе разделы
 * выбранного профиля. Выбор профиля запоминается на устройстве. [onSignedOut] — выход с устройства.
 */
@Composable
fun SignedInScreen(api: ClientApi, links: AppLinks, onSignedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profiles = rememberLoader("profiles") { api.profiles() }
    var selection by remember { mutableStateOf<ProfileKey?>(null) }
    var selectionLoaded by remember { mutableStateOf(false) }
    var invitations by remember { mutableStateOf(false) }
    LaunchedEffect(api) {
        selection = api.storedProfile()
        selectionLoaded = true
    }
    if (!selectionLoaded) {
        return
    }
    Loaded(profiles) { response ->
        val profile = response.profiles.find { it.key == selection } ?: response.profiles.firstOrNull()
        when {
            invitations || (profile == null && response.newInvitations > 0) ->
                InvitationsScreen(
                    api,
                    onBack = profile?.let { { invitations = false } },
                    onChanged = {
                        invitations = false
                        profiles.reload()
                    },
                )
            profile == null ->
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(Res.string.profiles_empty))
                    OutlinedButton(onClick = {
                        scope.launch {
                            api.logout()
                            onSignedOut()
                        }
                    }) { Text(stringResource(Res.string.account_logout)) }
                }
            else ->
                MainScreen(
                    api = api,
                    links = links,
                    profiles = response,
                    profile = profile,
                    onSelect = {
                        selection = it
                        scope.launch { api.storeProfile(it) }
                    },
                    onInvitations = { invitations = true },
                    onSignedOut = onSignedOut,
                )
        }
    }
}

/**
 * Неподключённые приглашения через [api]: «Подключить» и «Это не я» по каждой организации.
 * [onChanged] — приглашение принято или отклонено, [onBack] — вернуться к профилям (`null` — профилей ещё нет).
 */
@Composable
fun InvitationsScreen(api: ClientApi, onBack: (() -> Unit)?, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("invitations") { api.invitations() }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun answer(action: suspend () -> arrow.core.Either<ApiError, Unit>) {
        scope.launch {
            busy = true
            error = null
            action().fold({ error = it }, {
                loader.reload()
                onChanged()
            })
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        onBack?.let { TextButton(onClick = it) { Text(stringResource(Res.string.app_back)) } }
        Text(stringResource(Res.string.invitations_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.invitations_hint))
        error?.let { ErrorText(it) }
        Loaded(loader) { response ->
            response.invitations.forEach { invitation ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(invitation.orgName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.invitations_invitedAt, invitation.invitedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.shown))
                        Text(pluralStringResource(Res.plurals.invitations_clients, invitation.clientCount, invitation.clientCount))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val request = ClientAppInvitationRequest(invitation.orgId)
                            Button(onClick = { answer { api.connect(request) } }, enabled = !busy) { Text(stringResource(Res.string.invitations_connect)) }
                            OutlinedButton(onClick = { answer { api.notMe(request) } }, enabled = !busy) { Text(stringResource(Res.string.invitations_notMe)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Переключатель профилей [profiles] по организациям: текущий [current] отмечен, [onSelect] — выбор,
 * «Новые приглашения» со счётчиком открывают [onInvitations].
 */
@Composable
fun SwitcherScreen(
    profiles: ClientAppProfilesResponse,
    current: ProfileKey,
    onSelect: (ProfileKey) -> Unit,
    onInvitations: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.profiles_title), style = MaterialTheme.typography.headlineSmall)
        profiles.profiles.groupBy { it.orgName }.forEach { (orgName, members) ->
            Text(orgName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            members.forEach { profile ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(profile.key) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = profile.key == current, onClick = { onSelect(profile.key) })
                    Text(profile.clientName)
                }
            }
        }
        if (profiles.newInvitations > 0) {
            OutlinedButton(onClick = onInvitations) { Text(stringResource(Res.string.profiles_newInvitations, profiles.newInvitations)) }
        }
        Text(stringResource(Res.string.profiles_isolation), style = MaterialTheme.typography.bodySmall)
    }
}
