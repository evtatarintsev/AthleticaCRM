package org.athletica.crm.components.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.cd_schedule_next_week
import org.athletica.crm.generated.resources.cd_schedule_prev_week
import org.athletica.crm.generated.resources.nav_schedule
import org.athletica.crm.generated.resources.schedule_duration_hours
import org.athletica.crm.generated.resources.schedule_duration_hours_minutes
import org.athletica.crm.generated.resources.schedule_duration_minutes
import org.athletica.crm.generated.resources.schedule_filter_coaches
import org.athletica.crm.generated.resources.schedule_filter_directions
import org.athletica.crm.generated.resources.schedule_filter_halls
import org.athletica.crm.generated.resources.schedule_filter_levels
import org.athletica.crm.generated.resources.schedule_filter_session_types
import org.athletica.crm.generated.resources.schedule_filter_sports
import org.athletica.crm.generated.resources.schedule_free_seats
import org.athletica.crm.generated.resources.schedule_tag_online
import org.athletica.crm.generated.resources.schedule_tag_open_air
import org.athletica.crm.generated.resources.schedule_tag_recruiting
import org.athletica.crm.generated.resources.schedule_week_range
import org.athletica.crm.generated.resources.weekday_friday
import org.athletica.crm.generated.resources.weekday_monday
import org.athletica.crm.generated.resources.weekday_saturday
import org.athletica.crm.generated.resources.weekday_sunday
import org.athletica.crm.generated.resources.weekday_thursday
import org.athletica.crm.generated.resources.weekday_tuesday
import org.athletica.crm.generated.resources.weekday_wednesday
import org.athletica.crm.ui.WindowSize
import org.athletica.crm.ui.list.LocalListPageTopBar
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val TIME_GUTTER_WIDTH = 64.dp
private val MIN_DAY_COLUMN_WIDTH = 150.dp
private val GRID_LINE_COLOR = Color(0x14000000)
private val CARD_TEXT_COLOR = Color(0xFF1F1F1F)

/**
 * Страница «Расписание» в недельном режиме.
 * Отображает сетку «часы × дни недели» с карточками занятий из [state].
 * [onPreviousWeek] и [onNextWeek] переключают отображаемую неделю.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBar = LocalListPageTopBar.current
    val title = stringResource(Res.string.nav_schedule)
    LaunchedEffect(title) { topBar.set(title) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            ScheduleToolbar(
                weekStart = state.weekStart,
                weekEnd = state.weekEnd,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
            )
            HorizontalDivider(color = GRID_LINE_COLOR)
            ScheduleWeekGrid(state = state, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Панель над сеткой: переключение недели и плашки будущих фильтров.
 * [weekStart] и [weekEnd] задают подписанный диапазон дат.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleToolbar(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    BoxWithConstraints {
        val wide = WindowSize.fromWidth(maxWidth) >= WindowSize.MEDIUM
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedIconButton(onClick = onPreviousWeek) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(Res.string.cd_schedule_prev_week),
                    )
                }
                ScheduleTogglePill(
                    label =
                        stringResource(
                            Res.string.schedule_week_range,
                            weekStart.formatted(),
                            weekEnd.formatted(),
                        ),
                )
                OutlinedIconButton(onClick = onNextWeek) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.cd_schedule_next_week),
                    )
                }
                // TODO: включить фильтры, когда появится API расписания.
                if (wide) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        scheduleFilterLabels().forEach { label -> ScheduleTogglePill(label) }
                    }
                }
            }
            if (!wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    scheduleFilterLabels().forEach { label -> ScheduleTogglePill(label) }
                }
            }
        }
    }
}

/** Подписи фильтров расписания в порядке отображения. */
@Composable
private fun scheduleFilterLabels(): List<String> =
    listOf(
        stringResource(Res.string.schedule_filter_directions),
        stringResource(Res.string.schedule_filter_session_types),
        stringResource(Res.string.schedule_filter_sports),
        stringResource(Res.string.schedule_filter_coaches),
        stringResource(Res.string.schedule_filter_halls),
        stringResource(Res.string.schedule_filter_levels),
    )

/**
 * Плашка панели расписания: диапазон дат и будущие фильтры.
 * Пока не интерактивна — обработчиков нажатия нет до появления API.
 */
@Composable
private fun ScheduleTogglePill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * Сетка недели: шапка с датами и строка на каждый час, в котором есть занятия.
 * Прокручивается по вертикали, а на узком экране — и по горизонтали.
 */
@Composable
private fun ScheduleWeekGrid(
    state: ScheduleState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val available = maxWidth - TIME_GUTTER_WIDTH
        val columnWidth = maxOf(MIN_DAY_COLUMN_WIDTH, available / state.days.size)
        val horizontalScroll = rememberScrollState()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.horizontalScroll(horizontalScroll)) {
                ScheduleGridHeader(days = state.days, columnWidth = columnWidth)
            }
            HorizontalDivider(color = GRID_LINE_COLOR)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(horizontalScroll),
            ) {
                state.hours.forEach { hour ->
                    ScheduleHourRow(hour = hour, days = state.days, columnWidth = columnWidth)
                    HorizontalDivider(color = GRID_LINE_COLOR)
                }
            }
        }
    }
}

/** Шапка сетки: иконка часов в левой колонке и даты дней недели. */
@Composable
private fun ScheduleGridHeader(
    days: List<ScheduleDay>,
    columnWidth: Dp,
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier.width(TIME_GUTTER_WIDTH).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        days.forEach { day ->
            VerticalDivider(color = GRID_LINE_COLOR)
            Column(
                modifier = Modifier.width(columnWidth).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.date.day.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(day.date.weekdayLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Строка сетки для часа [hour]: номер часа в левой колонке и карточки занятий по дням.
 */
@Composable
private fun ScheduleHourRow(
    hour: Int,
    days: List<ScheduleDay>,
    columnWidth: Dp,
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier.width(TIME_GUTTER_WIDTH).fillMaxHeight().padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = hour.toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        days.forEach { day ->
            VerticalDivider(color = GRID_LINE_COLOR)
            Column(
                modifier = Modifier.width(columnWidth).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                day.events
                    .filter { it.startAt.hour == hour }
                    .sortedBy { it.startAt.minute }
                    .forEach { event -> ScheduleEventCard(event) }
            }
        }
    }
}

/** Карточка занятия: свободные места, метки, время, название, тренер и зал. */
@Composable
private fun ScheduleEventCard(event: ScheduleEvent) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(event.color.container)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        event.freeSeats?.let { seats ->
            ScheduleCardBadge(
                text = pluralStringResource(Res.plurals.schedule_free_seats, seats, seats),
                background = event.color.badge,
            )
        }
        event.tags.forEach { tag ->
            ScheduleCardBadge(text = stringResource(tag.label()), background = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${event.startAt.formatted()} - ${event.endAt.formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = CARD_TEXT_COLOR,
            )
            Text(
                text = event.durationLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = CARD_TEXT_COLOR.copy(alpha = 0.7f),
            )
        }
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = CARD_TEXT_COLOR,
        )
        listOfNotNull(event.coach, event.hall).forEach { line ->
            HorizontalDivider(color = CARD_TEXT_COLOR.copy(alpha = 0.15f))
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = CARD_TEXT_COLOR.copy(alpha = 0.8f),
            )
        }
    }
}

/** Плашка на карточке занятия во всю её ширину. */
@Composable
private fun ScheduleCardBadge(
    text: String,
    background: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = CARD_TEXT_COLOR,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Подпись метки занятия. */
private fun ScheduleEventTag.label(): StringResource =
    when (this) {
        ScheduleEventTag.RECRUITING -> Res.string.schedule_tag_recruiting
        ScheduleEventTag.ONLINE -> Res.string.schedule_tag_online
        ScheduleEventTag.OPEN_AIR -> Res.string.schedule_tag_open_air
    }

/** Название дня недели для даты. */
private fun LocalDate.weekdayLabel(): StringResource =
    when (dayOfWeek.ordinal) {
        0 -> Res.string.weekday_monday
        1 -> Res.string.weekday_tuesday
        2 -> Res.string.weekday_wednesday
        3 -> Res.string.weekday_thursday
        4 -> Res.string.weekday_friday
        5 -> Res.string.weekday_saturday
        else -> Res.string.weekday_sunday
    }

/** Продолжительность занятия в виде «(1 ч 30 мин)». */
@Composable
private fun ScheduleEvent.durationLabel(): String {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return when {
        hours == 0 -> stringResource(Res.string.schedule_duration_minutes, minutes)
        minutes == 0 -> stringResource(Res.string.schedule_duration_hours, hours)
        else -> stringResource(Res.string.schedule_duration_hours_minutes, hours, minutes)
    }
}

/** Дата в формате `ДД.ММ.ГГГГ`. */
private fun LocalDate.formatted(): String = "${day.toString().padStart(2, '0')}.${month.number.toString().padStart(2, '0')}.$year"

/** Время в формате `ЧЧ:ММ`. */
private fun LocalTime.formatted(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
