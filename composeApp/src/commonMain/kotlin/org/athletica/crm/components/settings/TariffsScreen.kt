package org.athletica.crm.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tariffs.CreateTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanSchema
import org.athletica.crm.api.schemas.tariffs.UpdateTariffPlanRequest
import org.athletica.crm.components.clients.subscription.editText
import org.athletica.crm.components.clients.subscription.parseMoney
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.formatted
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_archive
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_edit
import org.athletica.crm.generated.resources.action_restore
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.issue_sub_duration
import org.athletica.crm.generated.resources.issue_sub_duration_days
import org.athletica.crm.generated.resources.issue_sub_duration_months
import org.athletica.crm.generated.resources.issue_sub_duration_unit_days
import org.athletica.crm.generated.resources.issue_sub_duration_unit_months
import org.athletica.crm.generated.resources.issue_sub_price
import org.athletica.crm.generated.resources.issue_sub_sessions
import org.athletica.crm.generated.resources.issue_sub_sessions_count
import org.athletica.crm.generated.resources.issue_sub_sessions_unlimited
import org.athletica.crm.generated.resources.issue_sub_unlimited
import org.athletica.crm.generated.resources.label_name
import org.athletica.crm.generated.resources.screen_tariff_create
import org.athletica.crm.generated.resources.screen_tariff_edit
import org.athletica.crm.generated.resources.screen_tariffs
import org.athletica.crm.generated.resources.tariff_archived_badge
import org.athletica.crm.ui.tariff.DashedTile
import org.athletica.crm.ui.tariff.TariffTile
import org.athletica.crm.ui.tariff.TariffTileMinWidth
import org.jetbrains.compose.resources.stringResource

/**
 * Экран «Тарифы абонементов».
 * Загружает список через [api], поддерживает создание, редактирование и архивирование.
 */
@Composable
fun TariffsScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { TariffsViewModel(api, scope) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TariffPlanSchema?>(null) }

    val state = viewModel.state
    val isSaving = state.save is TariffsSaveState.Saving
    val saveError = (state.save as? TariffsSaveState.Error)?.error

    val currency = (state.data as? TariffsData.Loaded)?.currency

    editing?.let { tariff ->
        TariffEditor(
            title = stringResource(Res.string.screen_tariff_edit),
            initial = tariff,
            currency = currency ?: tariff.price.currency,
            isSaving = isSaving,
            error = saveError?.message(),
            onBack = {
                editing = null
                viewModel.onSaveErrorDismissed()
            },
            onSave = { form ->
                viewModel.onUpdate(
                    UpdateTariffPlanRequest(
                        id = tariff.id,
                        name = form.name,
                        sessions = form.sessions,
                        durationValue = form.durationValue,
                        durationUnit = form.durationUnit,
                        price = form.price,
                    ),
                ) { editing = null }
            },
            modifier = modifier,
        )
        return
    }

    if (showCreate && currency != null) {
        TariffEditor(
            title = stringResource(Res.string.screen_tariff_create),
            initial = null,
            currency = currency,
            isSaving = isSaving,
            error = saveError?.message(),
            onBack = {
                showCreate = false
                viewModel.onSaveErrorDismissed()
            },
            onSave = { form ->
                viewModel.onCreate(
                    CreateTariffPlanRequest(
                        id = TariffPlanId.new(),
                        name = form.name,
                        sessions = form.sessions,
                        durationValue = form.durationValue,
                        durationUnit = form.durationUnit,
                        price = form.price,
                    ),
                ) { showCreate = false }
            },
            modifier = modifier,
        )
        return
    }

    TariffsListContent(
        state = state,
        onBack = onBack,
        onAdd = { showCreate = true },
        onItemClick = { editing = it },
        onArchiveToggle = { tariff -> viewModel.onArchive(tariff.id, !tariff.archived) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TariffsListContent(
    state: TariffsState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onItemClick: (TariffPlanSchema) -> Unit,
    onArchiveToggle: (TariffPlanSchema) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_tariffs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val data = state.data) {
            TariffsData.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is TariffsData.Error ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(data.error.message(), color = MaterialTheme.colorScheme.error)
                }

            is TariffsData.Loaded ->
                TariffsGrid(
                    tariffs = data.items,
                    onAdd = onAdd,
                    onItemClick = onItemClick,
                    onArchiveToggle = onArchiveToggle,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
        }
    }
}

/**
 * Сетка плиток тарифов.
 * Число колонок подбирается по ширине экрана; последняя плитка — создание тарифа.
 */
@Composable
private fun TariffsGrid(
    tariffs: List<TariffPlanSchema>,
    onAdd: () -> Unit,
    onItemClick: (TariffPlanSchema) -> Unit,
    onArchiveToggle: (TariffPlanSchema) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TariffTileMinWidth),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        items(tariffs, key = { it.id.toString() }) { tariff ->
            TariffCard(
                tariff = tariff,
                onClick = { onItemClick(tariff) },
                onArchiveToggle = { onArchiveToggle(tariff) },
            )
        }
        item(key = "add") {
            DashedTile(
                icon = Icons.Default.Add,
                label = stringResource(Res.string.screen_tariff_create),
                onClick = onAdd,
            )
        }
    }
}

/**
 * Плитка тарифа в настройках: добавляет к общей плитке кнопки архивирования
 * и редактирования и приглушает архивные тарифы.
 */
@Composable
private fun TariffCard(
    tariff: TariffPlanSchema,
    onClick: () -> Unit,
    onArchiveToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationText =
        when (tariff.durationUnit) {
            DurationUnit.DAYS -> stringResource(Res.string.issue_sub_duration_days, tariff.durationValue)
            DurationUnit.MONTHS -> stringResource(Res.string.issue_sub_duration_months, tariff.durationValue)
        }
    val sessionsCount = tariff.sessions
    val sessionsText =
        if (sessionsCount == null) {
            stringResource(Res.string.issue_sub_sessions_unlimited)
        } else {
            stringResource(Res.string.issue_sub_sessions_count, sessionsCount)
        }

    TariffTile(
        name = tariff.name,
        details = listOf(sessionsText, durationText),
        price = tariff.price.formatted,
        onClick = onClick,
        muted = tariff.archived,
        caption = if (tariff.archived) stringResource(Res.string.tariff_archived_badge) else null,
        actions = {
            IconButton(onClick = onArchiveToggle) {
                if (tariff.archived) {
                    Icon(Icons.Default.Unarchive, contentDescription = stringResource(Res.string.action_restore))
                } else {
                    Icon(Icons.Default.Archive, contentDescription = stringResource(Res.string.action_archive))
                }
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.action_edit))
            }
        },
        modifier = modifier,
    )
}

/** Заполненная и провалидированная форма тарифа. */
private data class TariffFormResult(
    val name: String,
    val sessions: Int?,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    val price: org.athletica.crm.core.money.Money,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TariffEditor(
    title: String,
    initial: TariffPlanSchema?,
    currency: Currency,
    isSaving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (TariffFormResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var unlimited by remember { mutableStateOf(initial?.sessions == null && initial != null) }
    var sessions by remember { mutableStateOf(initial?.sessions?.toString().orEmpty()) }
    var durationValue by remember { mutableStateOf(initial?.durationValue?.toString() ?: "1") }
    var durationUnit by remember { mutableStateOf(initial?.durationUnit ?: DurationUnit.MONTHS) }
    var priceText by remember { mutableStateOf(initial?.price?.editText.orEmpty()) }

    val sessionsCount = sessions.toIntOrNull()
    val durationCount = durationValue.toIntOrNull()
    val price = parseMoney(priceText, currency)
    val isValid =
        name.isNotBlank() &&
            (durationCount != null && durationCount > 0) &&
            (unlimited || (sessionsCount != null && sessionsCount > 0)) &&
            price != null

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = isValid && !isSaving,
                        onClick = {
                            onSave(
                                TariffFormResult(
                                    name = name.trim(),
                                    sessions = if (unlimited) null else sessionsCount,
                                    durationValue = durationCount!!,
                                    durationUnit = durationUnit,
                                    price = price!!,
                                ),
                            )
                        },
                    ) { Text(stringResource(Res.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.label_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.issue_sub_unlimited))
                Switch(checked = unlimited, onCheckedChange = { unlimited = it })
            }

            if (!unlimited) {
                OutlinedTextField(
                    value = sessions,
                    onValueChange = { sessions = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(Res.string.issue_sub_sessions)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = durationValue,
                onValueChange = { durationValue = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(Res.string.issue_sub_duration)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = durationUnit == DurationUnit.DAYS,
                    onClick = { durationUnit = DurationUnit.DAYS },
                    label = { Text(stringResource(Res.string.issue_sub_duration_unit_days)) },
                )
                FilterChip(
                    selected = durationUnit == DurationUnit.MONTHS,
                    onClick = { durationUnit = DurationUnit.MONTHS },
                    label = { Text(stringResource(Res.string.issue_sub_duration_unit_months)) },
                )
            }

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(stringResource(Res.string.issue_sub_price)) },
                suffix = { Text(currency.symbol) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
