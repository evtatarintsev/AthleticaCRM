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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleSessionSchema
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.mobile.api.ApiError
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.schedule_cancelNotice
import org.athletica.mobile.resources.schedule_cancelled
import org.athletica.mobile.resources.schedule_completed
import org.athletica.mobile.resources.schedule_empty
import org.athletica.mobile.resources.schedule_following
import org.athletica.mobile.resources.schedule_movedFrom
import org.athletica.mobile.resources.schedule_notified
import org.athletica.mobile.resources.schedule_notify
import org.athletica.mobile.resources.schedule_previous
import org.athletica.mobile.resources.schedule_week
import org.athletica.mobile.session.ProfileKey
import org.jetbrains.compose.resources.stringResource

/**
 * Расписание профиля [profile] через [api] по неделям: занятия по дням с переносом и отменой,
 * «Не придём» и его отмена до начала запланированного занятия.
 */
@Composable
fun ScheduleScreen(api: ClientApi, profile: ProfileKey) {
    val scope = rememberCoroutineScope()
    var weekOf by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val loader = rememberLoader("schedule/${profile.orgId.value}/${profile.clientId.value}/$weekOf") { api.schedule(profile, weekOf) }

    fun notice(session: ClientAppScheduleSessionSchema) {
        scope.launch {
            busy = true
            error = null
            api.absenceNotice(profile, session.sessionId, cancel = session.absenceNoticeAt != null).fold({ error = it }, { loader.reload() })
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Loaded(loader) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { weekOf = week.weekStart + DatePeriod(days = -7) }) { Text(stringResource(Res.string.schedule_previous)) }
                TextButton(onClick = { weekOf = week.weekStart + DatePeriod(days = 7) }) { Text(stringResource(Res.string.schedule_following)) }
            }
            Text(stringResource(Res.string.schedule_week, week.weekStart.shown), style = MaterialTheme.typography.titleMedium)
            error?.let { ErrorText(it) }
            if (week.sessions.isEmpty()) {
                Text(stringResource(Res.string.schedule_empty))
            }
            week.sessions.groupBy { it.date }.forEach { (date, sessions) ->
                Text(date.shown, style = MaterialTheme.typography.titleSmall)
                sessions.forEach { SessionCard(it, busy) { notice(it) } }
            }
        }
    }
}

/** Занятие [session] со временем, местом и отметками; [onNotice] — «Не придём» или его отмена, пока занятие не началось. */
@Composable
private fun SessionCard(session: ClientAppScheduleSessionSchema, busy: Boolean, onNotice: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${session.startTime.shown}–${session.endTime.shown}", style = MaterialTheme.typography.titleMedium)
            Place(session)
            session.movedFrom?.let { Text(stringResource(Res.string.schedule_movedFrom, "${it.date.shown} ${it.startTime.shown}")) }
            when (session.status) {
                SessionStatus.CANCELLED -> Text(stringResource(Res.string.schedule_cancelled), color = MaterialTheme.colorScheme.error)
                SessionStatus.COMPLETED -> Text(stringResource(Res.string.schedule_completed))
                SessionStatus.SCHEDULED -> {}
            }
            if (session.absenceNoticeAt != null) {
                Text(stringResource(Res.string.schedule_notified))
            }
            if (session.status == SessionStatus.SCHEDULED && !session.hasStarted) {
                if (session.absenceNoticeAt == null) {
                    OutlinedButton(onClick = onNotice, enabled = !busy) { Text(stringResource(Res.string.schedule_notify)) }
                } else {
                    TextButton(onClick = onNotice, enabled = !busy) { Text(stringResource(Res.string.schedule_cancelNotice)) }
                }
            }
        }
    }
}

/** Группа, зал и тренеры занятия [session]. */
@Composable
fun Place(session: ClientAppScheduleSessionSchema) {
    Text(listOfNotNull(session.groupName, session.hallName, session.coaches.joinToString().ifEmpty { null }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
}
