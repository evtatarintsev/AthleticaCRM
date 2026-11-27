package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipState
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileSchema
import org.athletica.crm.core.money.formatted
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.balance_history
import org.athletica.mobile.resources.balance_title
import org.athletica.mobile.resources.balance_unpaid
import org.athletica.mobile.resources.home_cabinetOf
import org.athletica.mobile.resources.home_greeting
import org.athletica.mobile.resources.memberships_all
import org.athletica.mobile.resources.memberships_current
import org.athletica.mobile.resources.nav_schedule
import org.athletica.mobile.resources.profiles_switch
import org.athletica.mobile.resources.schedule_next
import org.athletica.mobile.resources.visits_count
import org.athletica.mobile.resources.visits_month
import org.athletica.mobile.resources.visits_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Главная профиля [profile] через [api]: приветствие, ближайшее занятие, текущий абонемент,
 * баланс и посещения месяца. Блок без данных скрывается. [onSwitch] — переключатель профилей
 * (со счётчиком [newInvitations]), [onOpen] — подробный раздел.
 */
@Composable
fun HomeScreen(
    api: ClientApi,
    profile: ClientAppProfileSchema,
    newInvitations: Int,
    onSwitch: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val key = profile.key
    val id = "${key.orgId.value}/${key.clientId.value}"
    val home = rememberLoader("home/$id") { api.home(key) }
    val schedule = rememberLoader("home-schedule/$id") { api.schedule(key, null) }
    val memberships = rememberLoader("home-memberships/$id") { api.memberships(key) }
    val balance = rememberLoader("home-balance/$id") { api.balance(key) }
    val visits = rememberLoader("home-visits/$id") { api.visits(key, null) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(profile.clientName, style = MaterialTheme.typography.titleMedium)
                Text(profile.orgName, style = MaterialTheme.typography.bodySmall)
            }
            BadgedBox(badge = { if (newInvitations > 0) Badge { Text("$newInvitations") } }) {
                OutlinedButton(onClick = onSwitch) { Text(stringResource(Res.string.profiles_switch)) }
            }
        }
        Loaded(home) {
            Block(stringResource(Res.string.home_greeting)) { Text(stringResource(Res.string.home_cabinetOf, it.clientName)) }
        }
        Loaded(schedule) { response ->
            response.next?.let { next ->
                Block(stringResource(Res.string.schedule_next)) {
                    Text("${next.date.shown}, ${next.startTime.shown}", style = MaterialTheme.typography.titleMedium)
                    Place(next)
                    TextButton(onClick = { onOpen(Route.Schedule) }) { Text(stringResource(Res.string.nav_schedule)) }
                }
            }
        }
        Loaded(memberships) { response ->
            val current =
                response.memberships.find { it.state == ClientAppMembershipState.ACTIVE && !it.ended }
                    ?: response.memberships.find { it.state == ClientAppMembershipState.INACTIVE }
            current?.let {
                Block(stringResource(Res.string.memberships_current)) {
                    Text(it.name, style = MaterialTheme.typography.titleMedium)
                    MembershipDetails(it)
                    TextButton(onClick = { onOpen(Route.Memberships) }) { Text(stringResource(Res.string.memberships_all)) }
                }
            }
        }
        Loaded(balance) { response ->
            if (response.balance.minorUnits != 0L || response.unpaidSessions > 0 || response.entries.isNotEmpty()) {
                Block(stringResource(Res.string.balance_title)) {
                    Text(response.balance.formatted, style = MaterialTheme.typography.titleMedium)
                    if (response.unpaidSessions > 0) {
                        Text(stringResource(Res.string.balance_unpaid, response.unpaidSessions))
                    }
                    TextButton(onClick = { onOpen(Route.Balance) }) { Text(stringResource(Res.string.balance_history)) }
                }
            }
        }
        Loaded(visits) { response ->
            if (response.visits.isNotEmpty()) {
                Block(stringResource(Res.string.visits_month)) {
                    Text(pluralStringResource(Res.plurals.visits_count, response.presentCount, response.presentCount), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onOpen(Route.Visits) }) { Text(stringResource(Res.string.visits_title)) }
                }
            }
        }
    }
}

/** Карточка блока с заголовком [title] и содержимым [content]. */
@Composable
fun Block(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}
