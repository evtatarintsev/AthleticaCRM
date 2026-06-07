package org.athletica.crm.components.clients.subscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.components.clients.message
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.formatted
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_issue_subscription
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.issue_sub_amount_due
import org.athletica.crm.generated.resources.issue_sub_cash
import org.athletica.crm.generated.resources.issue_sub_change
import org.athletica.crm.generated.resources.issue_sub_change_all_balance
import org.athletica.crm.generated.resources.issue_sub_change_all_hand
import org.athletica.crm.generated.resources.issue_sub_change_to_balance
import org.athletica.crm.generated.resources.issue_sub_change_to_hand
import org.athletica.crm.generated.resources.issue_sub_covered
import org.athletica.crm.generated.resources.issue_sub_current_balance
import org.athletica.crm.generated.resources.issue_sub_debt
import org.athletica.crm.generated.resources.issue_sub_discount_fixed
import org.athletica.crm.generated.resources.issue_sub_discount_none
import org.athletica.crm.generated.resources.issue_sub_discount_percent
import org.athletica.crm.generated.resources.issue_sub_discount_value
import org.athletica.crm.generated.resources.issue_sub_duration
import org.athletica.crm.generated.resources.issue_sub_duration_days
import org.athletica.crm.generated.resources.issue_sub_duration_months
import org.athletica.crm.generated.resources.issue_sub_duration_unit_days
import org.athletica.crm.generated.resources.issue_sub_duration_unit_months
import org.athletica.crm.generated.resources.issue_sub_end_date
import org.athletica.crm.generated.resources.issue_sub_free_explain
import org.athletica.crm.generated.resources.issue_sub_from_balance
import org.athletica.crm.generated.resources.issue_sub_individual
import org.athletica.crm.generated.resources.issue_sub_new_balance
import org.athletica.crm.generated.resources.issue_sub_no_payment
import org.athletica.crm.generated.resources.issue_sub_plan_individual
import org.athletica.crm.generated.resources.issue_sub_plan_individual_desc
import org.athletica.crm.generated.resources.issue_sub_price
import org.athletica.crm.generated.resources.issue_sub_sessions
import org.athletica.crm.generated.resources.issue_sub_sessions_count
import org.athletica.crm.generated.resources.issue_sub_sessions_unlimited
import org.athletica.crm.generated.resources.issue_sub_start_date
import org.athletica.crm.generated.resources.issue_sub_status_debt
import org.athletica.crm.generated.resources.issue_sub_status_paid
import org.athletica.crm.generated.resources.issue_sub_title_choose_plan
import org.athletica.crm.generated.resources.issue_sub_title_free
import org.athletica.crm.generated.resources.issue_sub_title_params
import org.athletica.crm.generated.resources.issue_sub_title_payment
import org.athletica.crm.generated.resources.issue_sub_to_payment
import org.athletica.crm.generated.resources.issue_sub_total
import org.athletica.crm.generated.resources.issue_sub_unlimited
import org.athletica.crm.generated.resources.label_comment
import org.athletica.crm.generated.resources.label_discount
import org.athletica.crm.generated.resources.label_groups
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource

/**
 * Экран-мастер выдачи абонемента клиенту (только UI, без сохранения).
 *
 * Три шага: выбор тарифа → параметры → оплата (или выдача без оплаты).
 * По завершении вызывает [onIssued], по отмене/закрытию — [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueSubscriptionScreen(
    clientId: ClientId,
    api: ApiClient,
    onBack: () -> Unit,
    onIssued: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { IssueSubscriptionViewModel(api, clientId, scope) { onIssued() } }
    val state = viewModel.state

    val step = (state as? IssueScreenState.Ready)?.step ?: IssueStep.CHOOSE_PLAN

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(stepTitle(step))) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            IssueStep.CHOOSE_PLAN -> onBack()
                            IssueStep.PARAMS -> viewModel.backToPlanChoice()
                            IssueStep.PAYMENT, IssueStep.FREE_OF_CHARGE -> viewModel.backToParams()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                is IssueScreenState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is IssueScreenState.Error -> {
                    Text(
                        text = state.error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }

                is IssueScreenState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LinearProgressIndicator(
                            progress = { stepProgress(state.step) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        when (state.step) {
                            IssueStep.CHOOSE_PLAN -> ChoosePlanStep(state, viewModel)
                            IssueStep.PARAMS -> ParamsStep(state, viewModel)
                            IssueStep.PAYMENT -> PaymentStep(state, viewModel)
                            IssueStep.FREE_OF_CHARGE -> FreeOfChargeStep(state, viewModel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Контейнер шага мастера: вертикальная прокрутка, отступы и единый отклик на
 * ширину экрана — на больших экранах (≥ MEDIUM) содержимое ограничивается
 * половиной ширины и прижимается к левому краю. [spacing] — расстояние между
 * элементами шага.
 */
@Composable
private fun StepContainer(spacing: Dp, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = WindowSize.fromWidth(maxWidth) >= WindowSize.MEDIUM
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = if (wide) Modifier.fillMaxWidth(0.5f) else Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

// ── Шаг 1: выбор тарифа ─────────────────────────────────────────────────────

@Composable
private fun ChoosePlanStep(state: IssueScreenState.Ready, viewModel: IssueSubscriptionViewModel) {
    StepContainer(spacing = 8.dp) {
        state.planOptions.forEach { plan ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.onPlanChosen(plan) },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${sessionsLabel(plan.sessions)} · ${durationLabel(plan.durationValue, plan.durationUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(plan.price.formatted, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        ListItem(
            headlineContent = { Text(stringResource(Res.string.issue_sub_plan_individual)) },
            supportingContent = { Text(stringResource(Res.string.issue_sub_plan_individual_desc)) },
            modifier = Modifier.clickable { viewModel.onPlanChosen(null) },
        )
    }
}

// ── Шаг 2: параметры ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParamsStep(state: IssueScreenState.Ready, viewModel: IssueSubscriptionViewModel) {
    val form = state.form ?: return
    val currency = state.currency
    var showDatePicker by remember { mutableStateOf(false) }

    StepContainer(spacing = 20.dp) {
        ParamsDetailsColumn(
            state = state,
            form = form,
            viewModel = viewModel,
            onPickDate = { showDatePicker = true },
        )
        ParamsPricingColumn(
            form = form,
            currency = currency,
            viewModel = viewModel,
        )
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = form.startDate.toEpochDays().toLong() * MILLIS_PER_DAY,
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.onStartDateChanged(LocalDate.fromEpochDays((millis / MILLIS_PER_DAY).toInt()))
                    }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Левая колонка параметров: тариф, группы, количество занятий, срок действия и даты.
 * На широком экране занимает левую половину, на узком — верхнюю часть формы.
 * [onPickDate] открывает выбор даты начала.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParamsDetailsColumn(
    state: IssueScreenState.Ready,
    form: SubscriptionForm,
    viewModel: IssueSubscriptionViewModel,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = form.plan?.name ?: stringResource(Res.string.issue_sub_individual),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        // Группы
        FieldLabel(stringResource(Res.string.label_groups))
        when (val groups = state.groups) {
            is GroupsData.Loading -> CircularProgressIndicator(modifier = Modifier.height(24.dp))
            is GroupsData.Error ->
                Text(
                    text = groups.error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )

            is GroupsData.Loaded ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.groups.forEach { group ->
                        FilterChip(
                            selected = group.id in form.groupIds,
                            onClick = { viewModel.onGroupToggled(group.id) },
                            label = { Text(group.name) },
                        )
                    }
                }
        }

        // Количество занятий
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntField(
                label = stringResource(Res.string.issue_sub_sessions),
                initial = form.sessions,
                enabled = !form.unlimited,
                onChange = { viewModel.onSessionsChanged(it) },
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(checked = form.unlimited, onCheckedChange = { viewModel.onUnlimitedToggled(it) })
                Text(stringResource(Res.string.issue_sub_unlimited), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Срок действия
        FieldLabel(stringResource(Res.string.issue_sub_duration))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntField(
                label = stringResource(Res.string.issue_sub_duration),
                initial = form.durationValue,
                enabled = true,
                onChange = { viewModel.onDurationValueChanged(it ?: 0) },
                modifier = Modifier.width(120.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                DurationUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = form.durationUnit == unit,
                        onClick = { viewModel.onDurationUnitChanged(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = DurationUnit.entries.size),
                        label = { Text(stringResource(durationUnitLabel(unit))) },
                    )
                }
            }
        }

        // Даты
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = form.startDate.toString(),
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.issue_sub_start_date)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.matchParentSize().clickable { onPickDate() })
            }
            OutlinedTextField(
                value = form.endDate.toString(),
                onValueChange = {},
                label = { Text(stringResource(Res.string.issue_sub_end_date)) },
                readOnly = true,
                enabled = false,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Правая колонка параметров: стоимость, скидка, итоговая сумма и кнопки перехода
 * к оплате / выдаче без оплаты. На широком экране — правая половина формы.
 */
@Composable
private fun ParamsPricingColumn(
    form: SubscriptionForm,
    currency: Currency,
    viewModel: IssueSubscriptionViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Стоимость
        MoneyTextField(
            label = stringResource(Res.string.issue_sub_price),
            initial = form.price,
            currency = currency,
            onChange = { viewModel.onPriceChanged(it) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Скидка
        DiscountField(form.discount, currency, viewModel)

        // Итого
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.issue_sub_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = form.total.formatted,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // Действия
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { viewModel.goToFreeOfCharge() },
                enabled = form.isValid,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.issue_sub_no_payment))
            }
            Button(
                onClick = { viewModel.goToPayment() },
                enabled = form.isValid,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.issue_sub_to_payment))
            }
        }
    }
}

@Composable
private fun DiscountField(discount: Discount, currency: Currency, viewModel: IssueSubscriptionViewModel) {
    FieldLabel(stringResource(Res.string.label_discount))
    val options =
        listOf(
            Res.string.issue_sub_discount_none,
            Res.string.issue_sub_discount_percent,
            Res.string.issue_sub_discount_fixed,
        )
    val selectedIndex =
        when (discount) {
            Discount.None -> 0
            is Discount.Percent -> 1
            is Discount.Fixed -> 2
        }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = {
                    viewModel.onDiscountChanged(
                        when (index) {
                            0 -> Discount.None
                            1 -> Discount.Percent(0)
                            else -> Discount.Fixed(Money.zero(currency))
                        },
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
    when (discount) {
        Discount.None -> Unit
        is Discount.Percent ->
            IntField(
                label = stringResource(Res.string.issue_sub_discount_value),
                initial = discount.value,
                enabled = true,
                onChange = { viewModel.onDiscountChanged(Discount.Percent((it ?: 0).coerceIn(0, 100))) },
                modifier = Modifier.fillMaxWidth(),
            )

        is Discount.Fixed ->
            MoneyTextField(
                label = stringResource(Res.string.issue_sub_discount_value),
                initial = discount.amount,
                currency = currency,
                onChange = { viewModel.onDiscountChanged(Discount.Fixed(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

// ── Шаг 3а: оплата ──────────────────────────────────────────────────────────

@Composable
private fun PaymentStep(state: IssueScreenState.Ready, viewModel: IssueSubscriptionViewModel) {
    val form = state.form ?: return
    val currency = state.currency
    val summary = paymentSummary(form.total, state.currentBalance, state.payment)

    StepContainer(spacing = 16.dp) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.issue_sub_amount_due, form.total.formatted),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.issue_sub_current_balance, state.currentBalance.formatted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MoneyTextField(
            label = stringResource(Res.string.issue_sub_cash),
            initial = state.payment.cashGiven,
            currency = currency,
            onChange = { viewModel.onCashChanged(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        MoneyTextField(
            label = stringResource(Res.string.issue_sub_from_balance),
            initial = state.payment.fromBalance,
            currency = currency,
            onChange = { viewModel.onFromBalanceChanged(it) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Разделение сдачи
        if (summary.change.isPositive) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SummaryRow(stringResource(Res.string.issue_sub_change), summary.change.formatted)
                Slider(
                    value = summary.changeToBalance.minorUnits.toFloat(),
                    onValueChange = { viewModel.onChangeToBalanceChanged(Money(it.toLong(), currency)) },
                    valueRange = 0f..summary.change.minorUnits.toFloat(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${stringResource(Res.string.issue_sub_change_to_hand)}: ${summary.changeToHand.formatted}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${stringResource(Res.string.issue_sub_change_to_balance)}: ${summary.changeToBalance.formatted}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.onChangeToBalanceChanged(Money.zero(currency)) }) {
                        Text(stringResource(Res.string.issue_sub_change_all_hand))
                    }
                    TextButton(onClick = { viewModel.onChangeToBalanceChanged(summary.change) }) {
                        Text(stringResource(Res.string.issue_sub_change_all_balance))
                    }
                }
            }
        }

        HorizontalDivider()

        // Сводка
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryRow(stringResource(Res.string.issue_sub_covered), summary.covered.formatted)
            if (summary.debt.isPositive) {
                SummaryRow(
                    label = stringResource(Res.string.issue_sub_debt),
                    value = summary.debt.formatted,
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
            SummaryRow(
                label = stringResource(Res.string.issue_sub_new_balance),
                value = summary.newBalance.formatted,
                valueColor = if (summary.newBalance.isNegative) MaterialTheme.colorScheme.error else null,
            )
            Text(
                text =
                    if (summary.debt.isPositive) {
                        stringResource(Res.string.issue_sub_status_debt)
                    } else {
                        stringResource(Res.string.issue_sub_status_paid)
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (summary.debt.isPositive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }

        Button(onClick = { viewModel.onConfirm() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_issue_subscription))
        }
    }
}

// ── Шаг 3б: без оплаты ──────────────────────────────────────────────────────

@Composable
private fun FreeOfChargeStep(state: IssueScreenState.Ready, viewModel: IssueSubscriptionViewModel) {
    StepContainer(spacing = 16.dp) {
        Text(
            text = stringResource(Res.string.issue_sub_free_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.freeComment,
            onValueChange = { viewModel.onFreeCommentChanged(it) },
            label = { Text(stringResource(Res.string.label_comment)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.onConfirm() },
            enabled = state.freeComment.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_issue_subscription))
        }
    }
}

// ── Переиспользуемые поля ──────────────────────────────────────────────────

/**
 * Поле ввода денежной суммы: держит собственный текстовый буфер, отдаёт наверх
 * валидное [Money]. Сидируется значением [initial] при первом размещении.
 */
@Composable
private fun MoneyTextField(
    label: String,
    initial: Money,
    currency: Currency,
    onChange: (Money) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf(initial.editText) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parseMoney(it, currency)?.let(onChange)
        },
        label = { Text(label) },
        suffix = { Text(currency.symbol) },
        singleLine = true,
        enabled = enabled,
        isError = parseMoney(text, currency) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * Поле ввода целого числа: держит собственный текстовый буфер, отдаёт наверх
 * `Int?` (null — пусто). Сидируется значением [initial] при первом размещении.
 */
@Composable
private fun IntField(
    label: String,
    initial: Int?,
    enabled: Boolean,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            if (value.all { it.isDigit() }) {
                text = value
                onChange(value.toIntOrNull())
            }
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(2.dp))
}

// ── helpers ─────────────────────────────────────────────────────────────────

private const val MILLIS_PER_DAY = 86_400_000L

private fun stepTitle(step: IssueStep) =
    when (step) {
        IssueStep.CHOOSE_PLAN -> Res.string.issue_sub_title_choose_plan
        IssueStep.PARAMS -> Res.string.issue_sub_title_params
        IssueStep.PAYMENT -> Res.string.issue_sub_title_payment
        IssueStep.FREE_OF_CHARGE -> Res.string.issue_sub_title_free
    }

private fun stepProgress(step: IssueStep) =
    when (step) {
        IssueStep.CHOOSE_PLAN -> 0.33f
        IssueStep.PARAMS -> 0.66f
        IssueStep.PAYMENT, IssueStep.FREE_OF_CHARGE -> 1f
    }

private fun durationUnitLabel(unit: DurationUnit) =
    when (unit) {
        DurationUnit.DAYS -> Res.string.issue_sub_duration_unit_days
        DurationUnit.MONTHS -> Res.string.issue_sub_duration_unit_months
    }

@Composable
private fun sessionsLabel(sessions: Int?): String =
    if (sessions == null) {
        stringResource(Res.string.issue_sub_sessions_unlimited)
    } else {
        stringResource(Res.string.issue_sub_sessions_count, sessions)
    }

@Composable
private fun durationLabel(value: Int, unit: DurationUnit): String =
    when (unit) {
        DurationUnit.DAYS -> stringResource(Res.string.issue_sub_duration_days, value)
        DurationUnit.MONTHS -> stringResource(Res.string.issue_sub_duration_months, value)
    }
