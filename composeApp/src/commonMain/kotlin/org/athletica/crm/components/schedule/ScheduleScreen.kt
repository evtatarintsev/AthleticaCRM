package org.athletica.crm.components.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import org.athletica.crm.api.schemas.schedule.ScheduleSessionSchema
import org.athletica.crm.components.settings.message
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_retry
import org.athletica.crm.generated.resources.cd_schedule_next_week
import org.athletica.crm.generated.resources.cd_schedule_prev_week
import org.athletica.crm.generated.resources.nav_schedule
import org.athletica.crm.generated.resources.schedule_duration_hours
import org.athletica.crm.generated.resources.schedule_duration_hours_minutes
import org.athletica.crm.generated.resources.schedule_duration_minutes
import org.athletica.crm.generated.resources.schedule_empty_filtered
import org.athletica.crm.generated.resources.schedule_empty_week
import org.athletica.crm.generated.resources.schedule_filter_coaches
import org.athletica.crm.generated.resources.schedule_filter_disciplines
import org.athletica.crm.generated.resources.schedule_filter_halls
import org.athletica.crm.generated.resources.schedule_filter_reset
import org.athletica.crm.generated.resources.schedule_filter_selected
import org.athletica.crm.generated.resources.schedule_filters_reset_all
import org.athletica.crm.generated.resources.schedule_free_seats_stub
import org.athletica.crm.generated.resources.schedule_load_error
import org.athletica.crm.generated.resources.schedule_status_cancelled
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
import org.jetbrains.compose.resources.stringResource

private val TIME_GUTTER_WIDTH = 64.dp
private val MIN_DAY_COLUMN_WIDTH = 150.dp
private val GRID_LINE_COLOR = Color(0x14000000)
private val CARD_TEXT_COLOR = Color(0xFF1F1F1F)
private const val CANCELLED_CARD_ALPHA = 0.5f

/**
 * Страница «Расписание» в недельном режиме.
 * Отображает сетку «часы × дни недели» с карточками занятий из [state].
 * [onPreviousWeek] и [onNextWeek] переключают отображаемую неделю,
 * [onFiltersChange] применяет выбранные фильтры, [onRetry] повторяет загрузку после ошибки.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onFiltersChange: (ScheduleFilters) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBar = LocalListPageTopBar.current
    val title = stringResource(Res.string.nav_schedule)
    LaunchedEffect(title) { topBar.set(title) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            ScheduleToolbar(
                state = state,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onFiltersChange = onFiltersChange,
            )
            HorizontalDivider(color = GRID_LINE_COLOR)
            ScheduleBody(
                state = state,
                onRetry = onRetry,
                onResetFilters = { onFiltersChange(ScheduleFilters()) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Содержимое под панелью: индикатор загрузки, ошибка с повтором,
 * сообщение о пустой неделе или о пустом результате фильтрации либо сетка.
 */
@Composable
private fun ScheduleBody(
    state: ScheduleState,
    onRetry: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val data = state.data) {
        ScheduleLoadState.Loading ->
            Box(modifier) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

        is ScheduleLoadState.Error ->
            ScheduleMessage(
                text = stringResource(Res.string.schedule_load_error),
                details = data.error.message(),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = onRetry,
                modifier = modifier,
            )

        is ScheduleLoadState.Loaded ->
            when {
                data.sessions.isNotEmpty() -> ScheduleWeekGrid(state = state, modifier = modifier)
                state.filters.isEmpty ->
                    ScheduleMessage(text = stringResource(Res.string.schedule_empty_week), modifier = modifier)
                else ->
                    ScheduleMessage(
                        text = stringResource(Res.string.schedule_empty_filtered),
                        actionLabel = stringResource(Res.string.schedule_filters_reset_all),
                        onAction = onResetFilters,
                        modifier = modifier,
                    )
            }
    }
}

/** Сообщение по центру страницы с необязательными пояснением [details] и действием. */
@Composable
private fun ScheduleMessage(
    text: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            details?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            actionLabel?.let {
                OutlinedButton(onClick = onAction) { Text(it) }
            }
        }
    }
}

/**
 * Панель над сеткой: переключение недели и фильтры по дисциплинам, залам и тренерам.
 * На узком экране фильтры переносятся в отдельную прокручиваемую строку.
 */
@Composable
private fun ScheduleToolbar(
    state: ScheduleState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onFiltersChange: (ScheduleFilters) -> Unit,
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
                SchedulePill(
                    label =
                        stringResource(
                            Res.string.schedule_week_range,
                            state.weekStart.formatted(),
                            state.weekEnd.formatted(),
                        ),
                )
                OutlinedIconButton(onClick = onNextWeek) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.cd_schedule_next_week),
                    )
                }
                if (wide) {
                    ScheduleFilterBar(
                        state = state,
                        onFiltersChange = onFiltersChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!wide) {
                ScheduleFilterBar(
                    state = state,
                    onFiltersChange = onFiltersChange,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    wrap = false,
                )
            }
        }
    }
}

/**
 * Три фильтра расписания и сброс всех фильтров. Значения берутся из справочников,
 * а не из занятий недели. [wrap] — переносить фильтры по строкам (широкий экран).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleFilterBar(
    state: ScheduleState,
    onFiltersChange: (ScheduleFilters) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
) {
    val filters = state.filters
    val dictionaries = state.dictionaries
    val content: @Composable () -> Unit = {
        ScheduleFilterMenu(
            label = stringResource(Res.string.schedule_filter_disciplines),
            options = dictionaries.disciplines.map { it.id to it.name },
            selected = filters.disciplineIds,
            onChange = { onFiltersChange(filters.copy(disciplineIds = it)) },
        )
        ScheduleFilterMenu(
            label = stringResource(Res.string.schedule_filter_halls),
            options = dictionaries.halls.map { it.id to it.name },
            selected = filters.hallIds,
            onChange = { onFiltersChange(filters.copy(hallIds = it)) },
        )
        ScheduleFilterMenu(
            label = stringResource(Res.string.schedule_filter_coaches),
            options = dictionaries.employees.map { it.id to it.name },
            selected = filters.employeeIds,
            onChange = { onFiltersChange(filters.copy(employeeIds = it)) },
        )
        if (!filters.isEmpty) {
            TextButton(onClick = { onFiltersChange(ScheduleFilters()) }) {
                Text(stringResource(Res.string.schedule_filters_reset_all))
            }
        }
    }
    if (wrap) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/**
 * Фильтр с множественным выбором: плашка [label] с числом выбранных значений,
 * по нажатию — меню с флажками [options] и сбросом. [onChange] получает новое множество.
 */
@Composable
private fun <T> ScheduleFilterMenu(
    label: String,
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onChange: (Set<T>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SchedulePill(
            label = if (selected.isEmpty()) label else stringResource(Res.string.schedule_filter_selected, label, selected.size),
            active = selected.isNotEmpty(),
            trailingIcon = true,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.schedule_filter_reset)) },
                enabled = selected.isNotEmpty(),
                onClick = { onChange(emptySet()) },
            )
            HorizontalDivider()
            options.forEach { (value, name) ->
                val checked = value in selected
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                    onClick = { onChange(if (checked) selected - value else selected + value) },
                )
            }
        }
    }
}

/**
 * Плашка панели расписания. [active] выделяет плашку фильтра с выбранными значениями,
 * [trailingIcon] добавляет стрелку раскрытия, [onClick] — `null` для неинтерактивной подписи.
 */
@Composable
private fun SchedulePill(
    label: String,
    active: Boolean = false,
    trailingIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                    .padding(start = 16.dp, end = if (trailingIcon) 8.dp else 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (trailingIcon) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    val days = state.days
    BoxWithConstraints(modifier) {
        val available = maxWidth - TIME_GUTTER_WIDTH
        val columnWidth = maxOf(MIN_DAY_COLUMN_WIDTH, available / days.size)
        val horizontalScroll = rememberScrollState()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.horizontalScroll(horizontalScroll)) {
                ScheduleGridHeader(days = days, columnWidth = columnWidth)
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
                    ScheduleHourRow(hour = hour, days = days, columnWidth = columnWidth)
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
                day.sessions
                    .filter { it.startTime.hour == hour }
                    .forEach { session -> ScheduleSessionCard(session) }
            }
        }
    }
}

/**
 * Карточка занятия: бейдж свободных мест (заглушка), время и продолжительность, название группы,
 * тренеры, зал и дисциплины. Строки без данных опускаются, отменённое занятие приглушено.
 */
@Composable
private fun ScheduleSessionCard(session: ScheduleSessionSchema) {
    val colors = session.colorKey.cardColors()
    val cancelled = session.status == SessionStatus.CANCELLED
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (cancelled) CANCELLED_CARD_ALPHA else 1f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.container)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ScheduleCardBadge(
            text = stringResource(Res.string.schedule_free_seats_stub),
            background = colors.badge,
        )
        if (cancelled) {
            ScheduleCardBadge(text = stringResource(Res.string.schedule_status_cancelled), background = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${session.startTime.formatted()} - ${session.endTime.formatted()}",
                style = MaterialTheme.typography.bodySmall,
                color = CARD_TEXT_COLOR,
            )
            Text(
                text = ScheduleDuration.between(session.startTime, session.endTime).label(),
                style = MaterialTheme.typography.bodySmall,
                color = CARD_TEXT_COLOR.copy(alpha = 0.7f),
            )
        }
        Text(
            text = session.group.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = CARD_TEXT_COLOR,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
        listOf(
            session.coaches.joinToString { it.name },
            session.hall.name,
            session.disciplines.joinToString { it.name },
        ).filter { it.isNotBlank() }
            .forEach { line ->
                HorizontalDivider(color = CARD_TEXT_COLOR.copy(alpha = 0.15f))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = CARD_TEXT_COLOR.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/** Подпись продолжительности в виде «(1 ч 30 мин)» без нулевой части. */
@Composable
private fun ScheduleDuration.label(): String =
    when (this) {
        is ScheduleDuration.Minutes -> stringResource(Res.string.schedule_duration_minutes, minutes)
        is ScheduleDuration.Hours -> stringResource(Res.string.schedule_duration_hours, hours)
        is ScheduleDuration.HoursMinutes -> stringResource(Res.string.schedule_duration_hours_minutes, hours, minutes)
    }

/** Дата в формате `ДД.ММ.ГГГГ`. */
private fun LocalDate.formatted(): String = "${day.toString().padStart(2, '0')}.${month.number.toString().padStart(2, '0')}.$year"

/** Время в формате `ЧЧ:ММ`. */
private fun LocalTime.formatted(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
