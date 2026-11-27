package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.athletica.crm.api.schemas.clientapp.ClientAppBalanceEntrySchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipState
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.formatted
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.balance_empty
import org.athletica.mobile.resources.balance_kind_credit
import org.athletica.mobile.resources.balance_kind_debit
import org.athletica.mobile.resources.balance_kind_expense
import org.athletica.mobile.resources.balance_kind_income
import org.athletica.mobile.resources.balance_kind_opening
import org.athletica.mobile.resources.balance_kind_payment
import org.athletica.mobile.resources.balance_kind_payout
import org.athletica.mobile.resources.balance_kind_reversal
import org.athletica.mobile.resources.balance_kind_sale
import org.athletica.mobile.resources.balance_kind_saleReturn
import org.athletica.mobile.resources.balance_kind_transfer
import org.athletica.mobile.resources.balance_title
import org.athletica.mobile.resources.balance_unpaid
import org.athletica.mobile.resources.balance_unpaidHint
import org.athletica.mobile.resources.memberships_closedOn
import org.athletica.mobile.resources.memberships_debits
import org.athletica.mobile.resources.memberships_ended
import org.athletica.mobile.resources.memberships_endingSoon
import org.athletica.mobile.resources.memberships_frozen
import org.athletica.mobile.resources.memberships_left
import org.athletica.mobile.resources.memberships_noDebits
import org.athletica.mobile.resources.memberships_none
import org.athletica.mobile.resources.memberships_revoked
import org.athletica.mobile.resources.memberships_startsOn
import org.athletica.mobile.resources.memberships_startsOnVisit
import org.athletica.mobile.resources.memberships_title
import org.athletica.mobile.resources.memberships_unlimited
import org.athletica.mobile.resources.memberships_until
import org.athletica.mobile.resources.visits_absent
import org.athletica.mobile.resources.visits_count
import org.athletica.mobile.resources.visits_empty
import org.athletica.mobile.resources.visits_following
import org.athletica.mobile.resources.visits_monthOf
import org.athletica.mobile.resources.visits_present
import org.athletica.mobile.resources.visits_previous
import org.athletica.mobile.session.ProfileKey
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Прокручиваемый экран раздела с заголовком [title]. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

/** История посещений профиля [profile] через [api] по месяцам: проведённые занятия с отметкой «был» или «не был». */
@Composable
fun VisitsScreen(api: ClientApi, profile: ProfileKey) {
    var monthOf by remember { mutableStateOf<LocalDate?>(null) }
    val loader = rememberLoader("visits/${profile.orgId.value}/${profile.clientId.value}/$monthOf") { api.visits(profile, monthOf) }
    Loaded(loader) { month ->
        Section(stringResource(Res.string.visits_monthOf, month.monthStart.shown)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { monthOf = month.monthStart + DatePeriod(months = -1) }) { Text(stringResource(Res.string.visits_previous)) }
                TextButton(onClick = { monthOf = month.monthStart + DatePeriod(months = 1) }) { Text(stringResource(Res.string.visits_following)) }
            }
            Text(pluralStringResource(Res.plurals.visits_count, month.presentCount, month.presentCount))
            if (month.visits.isEmpty()) {
                Text(stringResource(Res.string.visits_empty))
            }
            month.visits.forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(listOfNotNull("${it.date.shown} ${it.startTime.shown}", it.groupName).joinToString(" · "), Modifier.weight(1f))
                    Text(
                        stringResource(
                            when (it.presence) {
                                AttendancePresence.PRESENT -> Res.string.visits_present
                                AttendancePresence.ABSENT -> Res.string.visits_absent
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** Абонементы профиля [profile] через [api]: текущие и история, у каждого — раскрываемые списания. */
@Composable
fun MembershipsScreen(api: ClientApi, profile: ProfileKey) {
    val loader = rememberLoader("memberships/${profile.orgId.value}/${profile.clientId.value}") { api.memberships(profile) }
    Section(stringResource(Res.string.memberships_title)) {
        Loaded(loader) { response ->
            if (response.memberships.none { it.state == ClientAppMembershipState.ACTIVE || it.state == ClientAppMembershipState.INACTIVE }) {
                Text(stringResource(Res.string.memberships_none))
            }
            response.memberships.forEach { MembershipCard(it) }
        }
    }
}

/** Абонемент [membership] с раскрываемой по нажатию историей списаний. */
@Composable
private fun MembershipCard(membership: ClientAppMembershipSchema) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(membership.name, style = MaterialTheme.typography.titleMedium)
            MembershipDetails(membership)
            TextButton(onClick = { open = !open }) {
                Text(
                    if (membership.debits.isEmpty()) {
                        stringResource(Res.string.memberships_noDebits)
                    } else {
                        stringResource(Res.string.memberships_debits, membership.debits.size)
                    },
                )
            }
            if (open) {
                membership.debits.forEach { Text(listOfNotNull(it.sessionDate.shown, it.groupName).joinToString(" · ")) }
            }
        }
    }
}

/** Остаток, срок, заморозка и состояние абонемента [membership]: «Осталось 3 из 8», «до 30.11.2026». */
@Composable
fun MembershipDetails(membership: ClientAppMembershipSchema) {
    val total = membership.sessionsTotal
    val remaining = membership.sessionsRemaining
    Text(
        listOfNotNull(
            if (total == null || remaining == null) stringResource(Res.string.memberships_unlimited) else stringResource(Res.string.memberships_left, remaining, total),
            membership.endDate?.let { stringResource(Res.string.memberships_until, it.shown) },
        ).joinToString(", "),
    )
    if (membership.endingSoon.isNotEmpty()) {
        Text(stringResource(Res.string.memberships_endingSoon), color = MaterialTheme.colorScheme.error)
    }
    membership.freeze?.let { Text(stringResource(Res.string.memberships_frozen, it.startDate.shown, it.endDate.shown)) }
    when (membership.state) {
        ClientAppMembershipState.INACTIVE ->
            Text(membership.activateOn?.let { stringResource(Res.string.memberships_startsOn, it.shown) } ?: stringResource(Res.string.memberships_startsOnVisit))
        ClientAppMembershipState.ACTIVE -> if (membership.ended) Text(stringResource(Res.string.memberships_ended))
        ClientAppMembershipState.CLOSED -> membership.closedOn?.let { Text(stringResource(Res.string.memberships_closedOn, it.shown)) }
        ClientAppMembershipState.REVOKED -> Text(stringResource(Res.string.memberships_revoked))
    }
}

/** Баланс профиля [profile] через [api]: сумма, неоплаченные занятия с подсказкой и история движений. */
@Composable
fun BalanceScreen(api: ClientApi, profile: ProfileKey) {
    val loader = rememberLoader("balance/${profile.orgId.value}/${profile.clientId.value}") { api.balance(profile) }
    Section(stringResource(Res.string.balance_title)) {
        Loaded(loader) { response ->
            Text(response.balance.formatted, style = MaterialTheme.typography.headlineMedium)
            if (response.unpaidSessions > 0) {
                Text(stringResource(Res.string.balance_unpaid, response.unpaidSessions))
                Text(stringResource(Res.string.balance_unpaidHint), style = MaterialTheme.typography.bodySmall)
            }
            if (response.entries.isEmpty()) {
                Text(stringResource(Res.string.balance_empty))
            }
            response.entries.forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${it.occurredOn.shown} ${stringResource(it.kindText())}", Modifier.weight(1f))
                    Text(if (it.amount.minorUnits > 0) "+${it.amount.formatted}" else it.amount.formatted)
                }
            }
        }
    }
}

/** Тип операции понятными словами; корректировка — «Начисление» или «Списание» по знаку суммы. */
private fun ClientAppBalanceEntrySchema.kindText(): StringResource =
    when (kind) {
        LedgerKind.SALE -> Res.string.balance_kind_sale
        LedgerKind.SALE_RETURN -> Res.string.balance_kind_saleReturn
        LedgerKind.PAYMENT -> Res.string.balance_kind_payment
        LedgerKind.PAYOUT -> Res.string.balance_kind_payout
        LedgerKind.ADJUSTMENT -> if (amount.minorUnits < 0) Res.string.balance_kind_debit else Res.string.balance_kind_credit
        LedgerKind.INCOME -> Res.string.balance_kind_income
        LedgerKind.EXPENSE -> Res.string.balance_kind_expense
        LedgerKind.TRANSFER -> Res.string.balance_kind_transfer
        LedgerKind.OPENING -> Res.string.balance_kind_opening
        LedgerKind.REVERSAL -> Res.string.balance_kind_reversal
    }
