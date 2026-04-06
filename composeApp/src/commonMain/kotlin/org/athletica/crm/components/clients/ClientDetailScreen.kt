package org.athletica.crm.components.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_delete_client
import org.athletica.crm.generated.resources.action_edit
import org.athletica.crm.generated.resources.action_issue_subscription
import org.athletica.crm.generated.resources.action_more
import org.athletica.crm.generated.resources.action_upload_document
import org.athletica.crm.generated.resources.label_address
import org.athletica.crm.generated.resources.label_balance
import org.athletica.crm.generated.resources.label_balance_value
import org.athletica.crm.generated.resources.label_birthday
import org.athletica.crm.generated.resources.label_classes_start
import org.athletica.crm.generated.resources.label_contract_number
import org.athletica.crm.generated.resources.label_contract_type
import org.athletica.crm.generated.resources.label_discount
import org.athletica.crm.generated.resources.label_phone
import org.athletica.crm.generated.resources.label_sports_rank
import org.athletica.crm.generated.resources.placeholder_not_specified
import org.athletica.crm.generated.resources.section_basic_info
import org.athletica.crm.generated.resources.section_subscriptions
import org.athletica.crm.generated.resources.section_unpaid_lessons
import org.athletica.crm.generated.resources.subscription_status_active
import org.athletica.crm.generated.resources.subscription_status_expired
import org.athletica.crm.generated.resources.tab_documents
import org.athletica.crm.generated.resources.tab_history
import org.athletica.crm.generated.resources.tab_parents
import org.athletica.crm.generated.resources.tab_payments
import org.athletica.crm.generated.resources.visits_remaining
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

// ── TODO: заменить на реальные данные из API ───────────────────────────────

private data class FakeSubscription(
    val dateFrom: String,
    val dateTo: String,
    val remaining: Int,
    val total: Int,
)

private data class FakeUnpaidLesson(val date: String, val status: String, val group: String)

private data class FakePayment(val date: String, val amount: String, val description: String)

private data class FakeParent(val name: String, val phone: String, val relation: String)

private data class FakeDocument(val name: String, val uploadedAt: String)

private data class FakeVisit(val date: String, val status: String, val group: String)

private val fakeGroups = listOf("Боевое самбо", "Мужчины")

private val fakeSubscriptions =
    listOf(
        FakeSubscription("11.02.2020", "12.03.2020", 12, 12),
        FakeSubscription("11.01.2020", "11.02.2020", 0, 12),
    )

private val fakeUnpaidLessons =
    listOf(
        FakeUnpaidLesson("09.07.2021", "Был", "Боевое самбо"),
        FakeUnpaidLesson("07.07.2021", "Опоздал", "Боевое самбо"),
        FakeUnpaidLesson("05.07.2021", "Был", "Боевое самбо"),
        FakeUnpaidLesson("25.03.2020", "Опоздал", "Боевое самбо"),
        FakeUnpaidLesson("16.03.2020", "Был", "Боевое самбо"),
    )

private val fakePayments =
    listOf(
        FakePayment("21.03.2020", "+3 000 ₽", "Пополнение баланса"),
        FakePayment("11.02.2020", "+3 000 ₽", "Абонемент — Боевое самбо"),
        FakePayment("15.11.2019", "+5 000 ₽", "Первый платёж"),
    )

private val fakeParents =
    listOf(
        FakeParent("Иванова Мария Петровна", "+7 999 111-22-33", "Мать"),
        FakeParent("Иванов Сергей Николаевич", "+7 999 444-55-66", "Отец"),
    )

private val fakeDocuments =
    listOf(
        FakeDocument("Договор №А-1042.pdf", "15.11.2019"),
        FakeDocument("Паспорт (скан).jpg", "15.11.2019"),
    )

private val fakeHistory =
    listOf(
        FakeVisit("09.07.2021", "Был", "Боевое самбо"),
        FakeVisit("07.07.2021", "Опоздал", "Боевое самбо"),
        FakeVisit("05.07.2021", "Был", "Боевое самбо"),
        FakeVisit("02.07.2021", "Пропустил", "Боевое самбо"),
        FakeVisit("30.06.2021", "Был", "Боевое самбо"),
    )

private enum class ClientDetailTab {
    Payments,
    Parents,
    Documents,
    History,
}

@Composable
private fun ClientDetailTab.title(): String =
    when (this) {
        ClientDetailTab.Payments -> stringResource(Res.string.tab_payments)
        ClientDetailTab.Parents -> stringResource(Res.string.tab_parents)
        ClientDetailTab.Documents -> stringResource(Res.string.tab_documents)
        ClientDetailTab.History -> stringResource(Res.string.tab_history)
    }

// ── screen ────────────────────────────────────────────────────────────────

/**
 * Карточка клиента — основная информация, абонементы, неоплаченные занятия
 * и дополнительные вкладки (платежи, родители, документы, история).
 * Загружает данные клиента через [api] по [clientId].
 * TODO: заменить заглушки на реальные данные.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    clientId: Uuid,
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var client by remember { mutableStateOf<ClientDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflow by remember { mutableStateOf(false) }
    val tabs = ClientDetailTab.entries

    LaunchedEffect(clientId) {
        isLoading = true
        error = null
        api.clientDetail(clientId).fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.Unauthenticated -> "Сессия истекла"
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                    }
                isLoading = false
            },
            ifRight = { detail ->
                client = detail
                isLoading = false
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(client?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (client != null) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_upload_document)) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = { showOverflow = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_delete_client)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { showOverflow = false },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (client != null) {
                ExtendedFloatingActionButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_issue_subscription)) },
                )
            }
        },
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                ) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            client != null -> {
                val loadedClient = client!!
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    val windowSize = WindowSize.fromWidth(maxWidth)

                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item { ClientDetailHeader(loadedClient, api) }

                        if (windowSize >= WindowSize.MEDIUM) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    Box(Modifier.weight(1f)) { BasicInfoSection(loadedClient) }
                                    Column(Modifier.weight(1f)) {
                                        SubscriptionsSection()
                                        UnpaidLessonsSection()
                                    }
                                }
                            }
                        } else {
                            item { BasicInfoSection(loadedClient) }
                            item { SubscriptionsSection() }
                            item { UnpaidLessonsSection() }
                        }

                        stickyHeader {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = selectedTab,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(tab.title()) },
                                    )
                                }
                            }
                        }

                        when (tabs[selectedTab]) {
                            ClientDetailTab.Payments ->
                                items(fakePayments) { PaymentRow(it) }

                            ClientDetailTab.Parents ->
                                items(fakeParents) { ParentRow(it) }

                            ClientDetailTab.Documents -> {
                                items(fakeDocuments) { DocumentRow(it) }
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(stringResource(Res.string.action_upload_document)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            ClientDetailTab.History ->
                                items(fakeHistory) { HistoryRow(it) }
                        }
                    }
                }
            }
        }
    }
}

// ── header ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientDetailHeader(client: ClientDetailResponse, api: ApiClient) {
    val initials =
        client.name
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")

    var avatarUrl by remember(client.avatarId) { mutableStateOf<String?>(null) }
    LaunchedEffect(client.avatarId) {
        val id = client.avatarId
        if (id != null) {
            api.uploadInfo(id).onRight { avatarUrl = it.url }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(client.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(Res.string.label_balance_value, "3 056,00 ₽"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fakeGroups.forEach { group ->
                    SuggestionChip(onClick = {}, label = { Text(group, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
    }
}

// ── sections ──────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value ?: stringResource(Res.string.placeholder_not_specified),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Composable
private fun BasicInfoSection(client: ClientDetailResponse) {
    SectionCard(stringResource(Res.string.section_basic_info)) {
        InfoRow(stringResource(Res.string.label_balance), "3 056,00 ₽")
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        InfoRow(stringResource(Res.string.label_phone), null)
        InfoRow("Группы", fakeGroups.joinToString(", "))
        InfoRow(stringResource(Res.string.label_contract_number), null)
        InfoRow(stringResource(Res.string.label_contract_type), null)
        InfoRow(stringResource(Res.string.label_sports_rank), null)
        InfoRow(stringResource(Res.string.label_discount), "0 ₽")
        InfoRow(stringResource(Res.string.label_birthday), client.birthday?.formatRu())
        InfoRow(stringResource(Res.string.label_address), null)
        InfoRow(stringResource(Res.string.label_classes_start), "15.11.2019")
    }
}

@Composable
private fun SubscriptionsSection() {
    SectionCard(stringResource(Res.string.section_subscriptions)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fakeSubscriptions.forEach { sub ->
                SubscriptionItem(sub)
            }
        }
    }
}

@Composable
private fun SubscriptionItem(sub: FakeSubscription) {
    val progress = sub.remaining.toFloat() / sub.total.coerceAtLeast(1)
    val isActive = sub.remaining > 0
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${sub.dateFrom} — ${sub.dateTo}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (isActive) stringResource(Res.string.subscription_status_active) else stringResource(Res.string.subscription_status_expired), style = MaterialTheme.typography.labelSmall) },
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            )
            Text(
                text = stringResource(Res.string.visits_remaining, sub.remaining, sub.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnpaidLessonsSection() {
    SectionCard(stringResource(Res.string.section_unpaid_lessons)) {
        fakeUnpaidLessons.forEachIndexed { index, lesson ->
            if (index > 0) {
                HorizontalDivider()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
            ) {
                Text(
                    text = lesson.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    text = lesson.group,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusText(lesson.status)
            }
        }
    }
}

// ── tab content ───────────────────────────────────────────────────────────

@Composable
private fun PaymentRow(payment: FakePayment) {
    ListItem(
        headlineContent = { Text(payment.description) },
        supportingContent = { Text(payment.date) },
        trailingContent = {
            Text(
                text = payment.amount,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        },
    )
    HorizontalDivider()
}

@Composable
private fun ParentRow(parent: FakeParent) {
    ListItem(
        headlineContent = { Text(parent.name) },
        supportingContent = { Text("${parent.phone} · ${parent.relation}") },
    )
    HorizontalDivider()
}

@Composable
private fun DocumentRow(doc: FakeDocument) {
    ListItem(
        headlineContent = { Text(doc.name) },
        supportingContent = { Text(doc.uploadedAt) },
    )
    HorizontalDivider()
}

@Composable
private fun HistoryRow(visit: FakeVisit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = visit.date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = visit.group,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        StatusText(visit.status)
    }
    HorizontalDivider()
}

// ── helpers ───────────────────────────────────────────────────────────────

private fun LocalDate.formatRu(): String {
    val month =
        when (month) {
            Month.JANUARY -> "января"
            Month.FEBRUARY -> "февраля"
            Month.MARCH -> "марта"
            Month.APRIL -> "апреля"
            Month.MAY -> "мая"
            Month.JUNE -> "июня"
            Month.JULY -> "июля"
            Month.AUGUST -> "августа"
            Month.SEPTEMBER -> "сентября"
            Month.OCTOBER -> "октября"
            Month.NOVEMBER -> "ноября"
            Month.DECEMBER -> "декабря"
        }
    return "$dayOfMonth $month $year"
}

@Composable
private fun StatusText(status: String) {
    val color =
        when (status) {
            "Был" -> Color(0xFF2E7D32)
            "Опоздал" -> Color(0xFFE65100)
            else -> MaterialTheme.colorScheme.error
        }
    Text(
        text = status,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}
