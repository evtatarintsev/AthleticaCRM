package org.athletica.crm.components.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_notifications
import org.athletica.crm.generated.resources.cd_notification_mark_read
import org.athletica.crm.generated.resources.notifications_days_ago
import org.athletica.crm.generated.resources.notifications_empty
import org.athletica.crm.generated.resources.notifications_hours_ago
import org.athletica.crm.generated.resources.notifications_just_now
import org.athletica.crm.generated.resources.notifications_mark_all_read
import org.athletica.crm.generated.resources.notifications_minutes_ago
import org.athletica.crm.generated.resources.notifications_open_link
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Кнопка колокольчика с бейджем непрочитанных уведомлений.
 *
 * На COMPACT открывает [ModalBottomSheet], на MEDIUM/EXPANDED — [DropdownMenu]-панель.
 *
 * [notifications] — список всех уведомлений.
 * [windowSize] — текущий breakpoint для адаптивного поведения.
 * [onMarkRead] — вызывается при нажатии «Отметить прочитанным» для одного уведомления.
 * [onMarkAllRead] — вызывается при нажатии «Прочитать все».
 * [onNavigate] — вызывается при нажатии ссылки внутри уведомления.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBell(
    notifications: List<AppNotification>,
    windowSize: WindowSize,
    onMarkRead: (Uuid) -> Unit,
    onMarkAllRead: () -> Unit,
    onNavigate: (NotificationLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unreadCount = notifications.count { !it.isRead }
    var showPanel by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = modifier) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge {
                        Text(if (unreadCount > 99) "99+" else "$unreadCount")
                    }
                }
            },
        ) {
            IconButton(onClick = { showPanel = true }) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(Res.string.action_notifications),
                )
            }
        }

        // Панель-дропдаун для MEDIUM / EXPANDED
        if (windowSize != WindowSize.COMPACT) {
            DropdownMenu(
                expanded = showPanel,
                onDismissRequest = { showPanel = false },
            ) {
                NotificationPanelContent(
                    notifications = notifications,
                    unreadCount = unreadCount,
                    onMarkRead = onMarkRead,
                    onMarkAllRead = onMarkAllRead,
                    onNavigate = { link ->
                        showPanel = false
                        onNavigate(link)
                    },
                    modifier = Modifier.width(360.dp),
                )
            }
        }
    }

    // Шторка для COMPACT
    if (windowSize == WindowSize.COMPACT && showPanel) {
        ModalBottomSheet(
            onDismissRequest = { showPanel = false },
            sheetState = sheetState,
        ) {
            NotificationPanelContent(
                notifications = notifications,
                unreadCount = unreadCount,
                onMarkRead = onMarkRead,
                onMarkAllRead = onMarkAllRead,
                onNavigate = { link ->
                    showPanel = false
                    onNavigate(link)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Содержимое панели уведомлений — заголовок + список.
 * Используется и в [DropdownMenu], и в [ModalBottomSheet].
 */
@Composable
private fun NotificationPanelContent(
    notifications: List<AppNotification>,
    unreadCount: Int,
    onMarkRead: (Uuid) -> Unit,
    onMarkAllRead: () -> Unit,
    onNavigate: (NotificationLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { Clock.System.now() }
    Column(modifier = modifier) {
        // Заголовок панели
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.action_notifications),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (unreadCount > 0) {
                TextButton(onClick = onMarkAllRead) {
                    Text(stringResource(Res.string.notifications_mark_all_read))
                }
            }
        }
        HorizontalDivider()

        if (notifications.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.notifications_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // LazyColumn нельзя использовать внутри DropdownMenu — тот запрашивает intrinsic
            // measurements, которые SubcomposeLayout не поддерживает. Список ограничен 50
            // элементами на сервере, поэтому Column + verticalScroll достаточно.
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                notifications.forEach { notification ->
                    NotificationItemRow(
                        notification = notification,
                        now = now,
                        onMarkRead = { onMarkRead(notification.id) },
                        onNavigate = notification.link?.let { link -> { onNavigate(link) } },
                    )
                    HorizontalDivider()
                }
            }
        }
        // Нижний отступ для шторки
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Одна строка уведомления.
 *
 * Непрочитанные выделяются фоном [MaterialTheme.colorScheme.secondaryContainer] и жирным заголовком.
 * [onMarkRead] — callback кнопки «Отметить прочитанным» (null, если уже прочитано).
 * [onNavigate] — callback кнопки «Открыть» (null, если ссылки нет).
 */
@Composable
private fun NotificationItemRow(
    notification: AppNotification,
    now: Instant,
    onMarkRead: () -> Unit,
    onNavigate: (() -> Unit)?,
) {
    val containerColor =
        if (!notification.isRead) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        }

    Surface(color = containerColor) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                // Индикатор непрочитанного — цветная точка
                Box(
                    modifier = Modifier.size(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!notification.isRead) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (now - notification.createdAt).relativeTimeLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onNavigate != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = onNavigate,
                                modifier = Modifier.height(24.dp).padding(0.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.notifications_open_link),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = {
                if (!notification.isRead) {
                    IconButton(onClick = onMarkRead) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = stringResource(Res.string.cd_notification_mark_read),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )
    }
}

/**
 * Форматирует длительность как относительное время (только что / N мин / N ч / N дн).
 * Вызывается как `(now - notification.createdAt).relativeTimeLabel()`.
 */
@Composable
private fun Duration.relativeTimeLabel(): String {
    val diffSec = inWholeSeconds
    return when {
        diffSec < 60 -> stringResource(Res.string.notifications_just_now)
        diffSec < 3_600 -> stringResource(Res.string.notifications_minutes_ago, diffSec / 60)
        diffSec < 86_400 -> stringResource(Res.string.notifications_hours_ago, diffSec / 3_600)
        diffSec < 604_800 -> stringResource(Res.string.notifications_days_ago, diffSec / 86_400)
        else -> {
            val dt = (Clock.System.now() - this).toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.day}.${dt.month.number.toString().padStart(2, '0')}.${dt.year}"
        }
    }
}
