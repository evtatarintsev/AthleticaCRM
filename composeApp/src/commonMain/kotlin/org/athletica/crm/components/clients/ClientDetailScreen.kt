package org.athletica.crm.components.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.memberships.MembershipSchema
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.components.clients.notes.ClientNotesSection
import org.athletica.crm.components.clients.notes.ClientNotesViewModel
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.money.formatted
import org.athletica.crm.core.subscription.MembershipStatus
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_client_group
import org.athletica.crm.generated.resources.action_add_note
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_delete
import org.athletica.crm.generated.resources.action_delete_client
import org.athletica.crm.generated.resources.action_edit
import org.athletica.crm.generated.resources.action_issue_subscription
import org.athletica.crm.generated.resources.action_message
import org.athletica.crm.generated.resources.action_more
import org.athletica.crm.generated.resources.action_pay
import org.athletica.crm.generated.resources.action_remove
import org.athletica.crm.generated.resources.action_upload_document
import org.athletica.crm.generated.resources.cd_adjust_balance
import org.athletica.crm.generated.resources.cd_balance_history
import org.athletica.crm.generated.resources.dialog_delete_doc_message
import org.athletica.crm.generated.resources.dialog_delete_doc_title
import org.athletica.crm.generated.resources.dialog_remove_from_group_message
import org.athletica.crm.generated.resources.dialog_remove_from_group_title
import org.athletica.crm.generated.resources.label_address
import org.athletica.crm.generated.resources.label_balance
import org.athletica.crm.generated.resources.label_birthday
import org.athletica.crm.generated.resources.label_classes_start
import org.athletica.crm.generated.resources.label_contract_number
import org.athletica.crm.generated.resources.label_contract_type
import org.athletica.crm.generated.resources.label_discount
import org.athletica.crm.generated.resources.label_groups
import org.athletica.crm.generated.resources.label_phone
import org.athletica.crm.generated.resources.label_sports_rank
import org.athletica.crm.generated.resources.nav_payment_history
import org.athletica.crm.generated.resources.nav_subscription_history
import org.athletica.crm.generated.resources.nav_visit_history
import org.athletica.crm.generated.resources.placeholder_not_specified
import org.athletica.crm.generated.resources.section_basic_info
import org.athletica.crm.generated.resources.section_documents
import org.athletica.crm.generated.resources.section_history
import org.athletica.crm.generated.resources.section_notes
import org.athletica.crm.generated.resources.section_subscriptions
import org.athletica.crm.generated.resources.section_unpaid_lessons
import org.athletica.crm.generated.resources.subscription_sessions_unlimited
import org.athletica.crm.generated.resources.subscription_status_active
import org.athletica.crm.generated.resources.subscription_status_expired
import org.athletica.crm.generated.resources.subscriptions_empty
import org.athletica.crm.generated.resources.visits_remaining
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource

// ── TODO: заменить на реальные данные из API ───────────────────────────────

private data class FakeUnpaidLesson(val date: String, val status: String, val group: String)

private val fakeUnpaidLessons =
    listOf(
        FakeUnpaidLesson("09.07.2021", "Был", "Боевое самбо"),
        FakeUnpaidLesson("07.07.2021", "Опоздал", "Боевое самбо"),
        FakeUnpaidLesson("05.07.2021", "Был", "Боевое самбо"),
        FakeUnpaidLesson("25.03.2020", "Опоздал", "Боевое самбо"),
        FakeUnpaidLesson("16.03.2020", "Был", "Боевое самбо"),
    )

// ── screen ────────────────────────────────────────────────────────────────

/**
 * Карточка клиента — сводный дашборд (баланс, абонементы, неоплаченные занятия,
 * заметки, документы) и переходы в полноценные истории посещений / платежей /
 * абонементов. Действия (платёж, выдать абонемент, чат, заметка) — в M3 Toolbar:
 * `BottomAppBar` на узких экранах, горизонтальный ряд кнопок под TopAppBar на
 * широких.
 *
 * TODO: заменить заглушку [fakeUnpaidLessons] на реальные данные из API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    clientId: ClientId,
    api: ApiClient,
    currentEmployeeId: EmployeeId?,
    onBack: () -> Unit,
    onEdit: (ClientDetailResponse) -> Unit = {},
    onOpenVisitHistory: () -> Unit = {},
    onOpenPaymentHistory: () -> Unit = {},
    onOpenSubscriptionHistory: () -> Unit = {},
    onIssueSubscription: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ClientDetailViewModel(api, clientId, scope) }
    val notesViewModel = remember { ClientNotesViewModel(api, clientId, scope) }

    var showOverflow by remember { mutableStateOf(false) }
    var showAddToGroupSheet by remember { mutableStateOf(false) }
    var showAdjustBalanceDialog by remember { mutableStateOf(false) }
    var showBalanceHistorySheet by remember { mutableStateOf(false) }
    var groupToRemove by remember { mutableStateOf<ClientGroup?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val windowSize = WindowSize.fromWidth(maxWidth)
        val isLoaded = viewModel.state is ClientDetailState.Loaded
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = {
                        val loaded = viewModel.state as? ClientDetailState.Loaded
                        if (loaded != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Avatar(loaded.client.avatarId, loaded.client.name, api)
                                }
                                Text(loaded.client.name)
                            }
                        } else {
                            Text("")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.action_back),
                            )
                        }
                    },
                    actions = {
                        if (isLoaded) {
                            val loadedClient = (viewModel.state as ClientDetailState.Loaded).client
                            IconButton(onClick = { onEdit(loadedClient) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(Res.string.action_edit),
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(Res.string.action_more),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false },
                                ) {
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
            bottomBar = {
                if (isLoaded && windowSize == WindowSize.COMPACT) {
                    ClientActionsBottomBar(
                        onPay = {},
                        onIssueSubscription = onIssueSubscription,
                        onMessage = onOpenMessages,
                        onAddNote = {},
                    )
                }
            },
        ) { innerPadding ->
            when (val s = viewModel.state) {
                is ClientDetailState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ClientDetailState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    ) {
                        Text(
                            text = s.error.message(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                is ClientDetailState.Loaded -> {
                    val client = s.client
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    ) {
                        if (windowSize >= WindowSize.MEDIUM) {
                            item {
                                ClientActionsRow(
                                    onPay = {},
                                    onIssueSubscription = onIssueSubscription,
                                    onMessage = onOpenMessages,
                                    onAddNote = {},
                                )
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        Column {
                                            BasicInfoSection(
                                                client = client,
                                                onAdjustBalance = { showAdjustBalanceDialog = true },
                                                onBalanceHistory = { showBalanceHistorySheet = true },
                                                onAddToGroup = { showAddToGroupSheet = true },
                                                onRemoveFromGroup = { groupId ->
                                                    groupToRemove = client.groups.find { it.id == groupId }
                                                },
                                            )
                                            SectionCard(stringResource(Res.string.section_notes)) {
                                                ClientNotesSection(
                                                    state = notesViewModel.state,
                                                    currentEmployeeId = currentEmployeeId,
                                                    onDraftChange = notesViewModel::onDraftChange,
                                                    onSubmit = notesViewModel::onSubmit,
                                                    onStartEdit = notesViewModel::onStartEdit,
                                                    onCancelEdit = notesViewModel::onCancelEdit,
                                                    onDelete = { note -> notesViewModel.onDelete(note.id) },
                                                )
                                            }
                                            DocumentsSection(
                                                docs = client.docs,
                                                isUploading = viewModel.isUploadingDoc,
                                                onUpload = { viewModel.onUploadDoc() },
                                                onShare = { viewModel.onShareDoc(it) },
                                                onDelete = { viewModel.onDeleteDoc(it) },
                                            )
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        SubscriptionsSection(s.memberships)
                                        UnpaidLessonsSection()
                                    }
                                }
                            }
                        } else {
                            item {
                                BasicInfoSection(
                                    client = client,
                                    onAdjustBalance = { showAdjustBalanceDialog = true },
                                    onBalanceHistory = { showBalanceHistorySheet = true },
                                    onAddToGroup = { showAddToGroupSheet = true },
                                    onRemoveFromGroup = { groupId ->
                                        groupToRemove = client.groups.find { it.id == groupId }
                                    },
                                )
                            }
                            item { SubscriptionsSection(s.memberships) }
                            item { UnpaidLessonsSection() }
                            item {
                                SectionCard(stringResource(Res.string.section_notes)) {
                                    ClientNotesSection(
                                        state = notesViewModel.state,
                                        currentEmployeeId = currentEmployeeId,
                                        onDraftChange = notesViewModel::onDraftChange,
                                        onSubmit = notesViewModel::onSubmit,
                                        onStartEdit = notesViewModel::onStartEdit,
                                        onCancelEdit = notesViewModel::onCancelEdit,
                                        onDelete = { note -> notesViewModel.onDelete(note.id) },
                                    )
                                }
                            }
                            item {
                                DocumentsSection(
                                    docs = client.docs,
                                    isUploading = viewModel.isUploadingDoc,
                                    onUpload = { viewModel.onUploadDoc() },
                                    onShare = { viewModel.onShareDoc(it) },
                                    onDelete = { viewModel.onDeleteDoc(it) },
                                )
                            }
                        }

                        item {
                            HistoryLinksSection(
                                onOpenVisitHistory = onOpenVisitHistory,
                                onOpenPaymentHistory = onOpenPaymentHistory,
                                onOpenSubscriptionHistory = onOpenSubscriptionHistory,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddToGroupSheet && viewModel.state is ClientDetailState.Loaded) {
        val client = (viewModel.state as ClientDetailState.Loaded).client
        AddToGroupSheet(
            clientIds = listOf(clientId),
            existingGroupIds = client.groups.map { it.id }.toSet(),
            api = api,
            onDismiss = { showAddToGroupSheet = false },
            onGroupAdded = {
                showAddToGroupSheet = false
                viewModel.load()
            },
        )
    }

    groupToRemove?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToRemove = null },
            title = { Text(stringResource(Res.string.dialog_remove_from_group_title)) },
            text = { Text(stringResource(Res.string.dialog_remove_from_group_message, group.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        groupToRemove = null
                        viewModel.onRemoveFromGroup(group.id)
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.action_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToRemove = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (showBalanceHistorySheet) {
        BalanceHistorySheet(
            api = api,
            clientId = clientId,
            onDismiss = { showBalanceHistorySheet = false },
        )
    }

    if (showAdjustBalanceDialog) {
        val loadedCurrency = (viewModel.state as? ClientDetailState.Loaded)?.client?.balance?.currency
        if (loadedCurrency != null) {
            AdjustBalanceDialog(
                api = api,
                clientId = clientId,
                currency = loadedCurrency,
                onSuccess = { updated ->
                    viewModel.onClientUpdated(updated)
                    showAdjustBalanceDialog = false
                },
                onDismiss = { showAdjustBalanceDialog = false },
            )
        }
    }
}

// ── action toolbar ────────────────────────────────────────────────────────

/**
 * Док-панель действий для COMPACT (мобила). Четыре частых действия плюс
 * редактирование, которое остаётся в [TopAppBar.actions].
 */
@Composable
private fun ClientActionsBottomBar(
    onPay: () -> Unit,
    onIssueSubscription: () -> Unit,
    onMessage: () -> Unit,
    onAddNote: () -> Unit,
) {
    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPay) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = stringResource(Res.string.action_pay),
                )
            }
            IconButton(onClick = onIssueSubscription) {
                Icon(
                    imageVector = Icons.Default.CardMembership,
                    contentDescription = stringResource(Res.string.action_issue_subscription),
                )
            }
            IconButton(onClick = onMessage) {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = stringResource(Res.string.action_message),
                )
            }
            IconButton(onClick = onAddNote) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = stringResource(Res.string.action_add_note),
                )
            }
        }
    }
}

/**
 * Горизонтальный ряд кнопок-действий под шапкой для MEDIUM/EXPANDED.
 * На больших экранах удобнее видеть лейблы рядом с иконками.
 */
@Composable
private fun ClientActionsRow(
    onPay: () -> Unit,
    onIssueSubscription: () -> Unit,
    onMessage: () -> Unit,
    onAddNote: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onPay) {
                Icon(
                    Icons.Default.Payments,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_pay))
            }
            FilledTonalButton(onClick = onIssueSubscription) {
                Icon(
                    Icons.Default.CardMembership,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_issue_subscription))
            }
            FilledTonalButton(onClick = onMessage) {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_message))
            }
            FilledTonalButton(onClick = onAddNote) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_add_note))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicInfoSection(
    client: ClientDetailResponse,
    onAdjustBalance: () -> Unit,
    onBalanceHistory: () -> Unit,
    onAddToGroup: () -> Unit,
    onRemoveFromGroup: (GroupId) -> Unit,
) {
    SectionCard(stringResource(Res.string.section_basic_info)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        ) {
            Text(
                text = stringResource(Res.string.label_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.45f),
            )
            Text(
                text = client.balance.formatted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onBalanceHistory, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = stringResource(Res.string.cd_balance_history),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onAdjustBalance, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(Res.string.cd_adjust_balance),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        val phones = client.contacts.filter { it.type == ContactType.PHONE }
        if (phones.isEmpty()) {
            InfoRow(stringResource(Res.string.label_phone), null)
        } else {
            phones.forEach { InfoRow(stringResource(Res.string.label_phone), it.value) }
        }
        client.contacts
            .filter { it.type != ContactType.PHONE }
            .forEach { contact ->
                InfoRow(stringResource(contact.type.labelRes()), contact.value)
            }
        InfoRow(stringResource(Res.string.label_contract_number), null)
        InfoRow(stringResource(Res.string.label_contract_type), null)
        InfoRow(stringResource(Res.string.label_sports_rank), null)
        InfoRow(stringResource(Res.string.label_discount), "0 ₽")
        InfoRow(stringResource(Res.string.label_birthday), client.birthday?.formatRu())
        InfoRow(stringResource(Res.string.label_address), null)
        InfoRow(stringResource(Res.string.label_classes_start), "15.11.2019")
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        ) {
            Text(
                text = stringResource(Res.string.label_groups),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.45f).padding(top = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(0.55f),
            ) {
                client.groups.forEach { group ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(group.name, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).clickable { onRemoveFromGroup(group.id) },
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = onAddToGroup,
                    label = {
                        Text(
                            stringResource(Res.string.action_add_client_group),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionsSection(memberships: List<MembershipSchema>) {
    SectionCard(stringResource(Res.string.section_subscriptions)) {
        if (memberships.isEmpty()) {
            Text(
                text = stringResource(Res.string.subscriptions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                memberships.forEach { membership ->
                    SubscriptionItem(membership)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionItem(membership: MembershipSchema) {
    val isActive = membership.status == MembershipStatus.ACTIVE
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
                    text = "${membership.startDate.formatRu()} — ${membership.endDate.formatRu()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (isActive) stringResource(Res.string.subscription_status_active) else stringResource(Res.string.subscription_status_expired), style = MaterialTheme.typography.labelSmall) },
                )
            }
            val total = membership.sessionsTotal
            val remaining = membership.sessionsRemaining
            if (total != null && remaining != null) {
                LinearProgressIndicator(
                    progress = { remaining.toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(Res.string.visits_remaining, remaining, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(Res.string.subscription_sessions_unlimited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun DocumentsSection(
    docs: List<ClientDoc>,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onShare: (org.athletica.crm.core.entityids.UploadId) -> Unit,
    onDelete: (org.athletica.crm.core.entityids.ClientDocId) -> Unit,
) {
    var docToDelete by remember { mutableStateOf<ClientDoc?>(null) }

    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text(stringResource(Res.string.dialog_delete_doc_title)) },
            text = { Text(stringResource(Res.string.dialog_delete_doc_message, doc.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(doc.id)
                        docToDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    SectionCard(stringResource(Res.string.section_documents)) {
        docs.forEachIndexed { index, doc ->
            if (index > 0) HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
            ) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onShare(doc.uploadId) }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { docToDelete = doc }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
        ) {
            AssistChip(
                enabled = !isUploading,
                onClick = onUpload,
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

/**
 * Секция со ссылками на отдельные роуты с историями (посещения, платежи,
 * абонементы). На дашборде эти разделы не отображаются полностью — только
 * вход в полноценную страницу.
 */
@Composable
private fun HistoryLinksSection(
    onOpenVisitHistory: () -> Unit,
    onOpenPaymentHistory: () -> Unit,
    onOpenSubscriptionHistory: () -> Unit,
) {
    SectionCard(stringResource(Res.string.section_history)) {
        HistoryLinkRow(
            label = stringResource(Res.string.nav_visit_history),
            onClick = onOpenVisitHistory,
        )
        HorizontalDivider()
        HistoryLinkRow(
            label = stringResource(Res.string.nav_payment_history),
            onClick = onOpenPaymentHistory,
        )
        HorizontalDivider()
        HistoryLinkRow(
            label = stringResource(Res.string.nav_subscription_history),
            onClick = onOpenSubscriptionHistory,
        )
    }
}

@Composable
private fun HistoryLinkRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
    return "$day $month $year"
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
