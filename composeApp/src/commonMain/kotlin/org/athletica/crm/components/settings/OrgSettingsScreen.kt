package org.athletica.crm.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Цветовая палитра ──────────────────────────────────────────────────────

private object Ic {
    val PurpleContainer = Color(0xFFEEEDFE)
    val Purple = Color(0xFF534AB7)

    val TealContainer = Color(0xFFE1F5EE)
    val Teal = Color(0xFF0F6E56)

    val BlueContainer = Color(0xFFE6F1FB)
    val Blue = Color(0xFF185FA5)

    val GreenContainer = Color(0xFFEAF3DE)
    val Green = Color(0xFF3B6D11)

    val CoralContainer = Color(0xFFFAECE7)
    val Coral = Color(0xFF993C1D)
}

// ── Модель данных ─────────────────────────────────────────────────────────

private data class SettingItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val containerColor: Color,
    val iconColor: Color,
    val onClick: () -> Unit = {},
)

private data class SettingSection(
    val label: String,
    val items: List<SettingItem>,
)

private fun buildSections(
    onNavigateToBasicSettings: () -> Unit,
    onNavigateToSportsTypes: () -> Unit,
    onNavigateToClientSources: () -> Unit,
) = listOf(
    SettingSection(
        label = "Основное",
        items =
            listOf(
                SettingItem(
                    title = "Основные настройки",
                    subtitle = "Название, описание, контакты, часовой пояс",
                    icon = Icons.AutoMirrored.Filled.Article,
                    containerColor = Ic.PurpleContainer,
                    iconColor = Ic.Purple,
                    onClick = onNavigateToBasicSettings,
                ),
                SettingItem(
                    title = "Виды спорта",
                    subtitle = "Указываются для занятий и групп",
                    icon = Icons.AutoMirrored.Filled.Article,
                    containerColor = Ic.PurpleContainer,
                    iconColor = Ic.Purple,
                    onClick = onNavigateToSportsTypes,
                ),
                SettingItem(
                    title = "Разряды",
                    subtitle = "Указываются для клиентов",
                    icon = Icons.AutoMirrored.Filled.Article,
                    containerColor = Ic.PurpleContainer,
                    iconColor = Ic.Purple,
                ),
            ),
    ),
    SettingSection(
        label = "Клиенты",
        items =
            listOf(
                SettingItem(
                    title = "Отображение клиентов",
                    subtitle = "Столбцы и данные в списке клиентов",
                    icon = Icons.Default.PeopleAlt,
                    containerColor = Ic.TealContainer,
                    iconColor = Ic.Teal,
                ),
                SettingItem(
                    title = "Источники клиентов",
                    subtitle = "Откуда приходят клиенты, аналитика каналов",
                    icon = Icons.Default.Analytics,
                    containerColor = Ic.TealContainer,
                    iconColor = Ic.Teal,
                    onClick = onNavigateToClientSources,
                ),
            ),
    ),
    SettingSection(
        label = "Занятия и абонементы",
        items =
            listOf(
                SettingItem(
                    title = "Посещаемость",
                    subtitle = "Отметки, оплата, иконки и цвета статусов",
                    icon = Icons.Default.HowToReg,
                    containerColor = Ic.BlueContainer,
                    iconColor = Ic.Blue,
                ),
                SettingItem(
                    title = "Шаблоны абонементов",
                    subtitle = "Посещения, срок действия, стоимость",
                    icon = Icons.Default.CardMembership,
                    containerColor = Ic.BlueContainer,
                    iconColor = Ic.Blue,
                ),
            ),
    ),
    SettingSection(
        label = "Финансы",
        items =
            listOf(
                SettingItem(
                    title = "Кассы и статьи доходов",
                    subtitle = "Счета для оплат, категории доходов и расходов",
                    icon = Icons.Default.AccountBalance,
                    containerColor = Ic.GreenContainer,
                    iconColor = Ic.Green,
                ),
            ),
    ),
    SettingSection(
        label = "Интеграции",
        items =
            listOf(
                SettingItem(
                    title = "SMS-рассылка",
                    subtitle = "Провайдер, шаблоны сообщений, подпись",
                    icon = Icons.Default.Sms,
                    containerColor = Ic.CoralContainer,
                    iconColor = Ic.Coral,
                ),
            ),
    ),
)

// ── Экран ─────────────────────────────────────────────────────────────────

/**
 * Экран настроек организации — секции с пунктами в стиле M3 Settings pattern.
 * Каждый пункт: цветная иконка в круге → заголовок + описание → шеврон.
 *
 * [onNavigateToBasicSettings] — переход к основным настройкам организации.
 * [onNavigateToClientSources] — переход к справочнику источников клиентов.
 * [onNavigateToSportsTypes] — переход к справочнику видов спорта.
 */
@Composable
fun OrgSettingsScreen(
    onNavigateToBasicSettings: () -> Unit = {},
    onNavigateToClientSources: () -> Unit = {},
    onNavigateToSportsTypes: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sections =
        buildSections(
            onNavigateToBasicSettings = onNavigateToBasicSettings,
            onNavigateToSportsTypes = onNavigateToSportsTypes,
            onNavigateToClientSources = onNavigateToClientSources,
        )
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            item(key = "label_${section.label}") {
                SectionLabel(section.label)
            }
            item(key = "card_${section.label}") {
                SettingsSectionCard(section.items)
            }
        }
    }
}

// ── Компоненты ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSectionCard(items: List<SettingItem>) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        items.forEachIndexed { index, item ->
            SettingItemRow(item)
            if (index < items.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun SettingItemRow(item: SettingItem) {
    ListItem(
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(item.containerColor),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = item.onClick),
    )
}
