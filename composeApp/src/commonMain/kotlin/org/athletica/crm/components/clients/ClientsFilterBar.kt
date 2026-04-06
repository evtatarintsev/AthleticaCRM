package org.athletica.crm.components.clients

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_clear_search
import org.athletica.crm.generated.resources.action_display_settings
import org.athletica.crm.generated.resources.action_remove_birth_year_filter
import org.athletica.crm.generated.resources.action_remove_gender_filter
import org.athletica.crm.generated.resources.filter_chip_has_debt
import org.athletica.crm.generated.resources.filter_gender_all
import org.athletica.crm.generated.resources.filter_gender_female
import org.athletica.crm.generated.resources.filter_gender_male
import org.athletica.crm.generated.resources.filter_no_group
import org.athletica.crm.generated.resources.filters_label
import org.athletica.crm.generated.resources.filters_with_count
import org.athletica.crm.generated.resources.hint_search_by_name
import org.jetbrains.compose.resources.stringResource

/**
 * Панель поиска и фильтров над таблицей клиентов.
 *
 * Состоит из двух строк:
 * 1. Поле поиска по имени + кнопка «Фильтры» (показывает счётчик активных).
 * 2. Горизонтальная прокрутка [FilterChip] для активных фильтров — видна только если [ClientFilterState.chipCount] > 0.
 *
 * [filter] — текущее состояние фильтров.
 * [onFilterChange] — вызывается при любом изменении фильтра.
 * [onOpenSheet] — вызывается при нажатии кнопки «Фильтры» для открытия панели фильтров.
 * [onOpenSettings] — вызывается при нажатии иконки настроек отображения.
 */
@Composable
fun ClientsFilterBar(
    filter: ClientFilterState,
    onFilterChange: (ClientFilterState) -> Unit,
    onOpenSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = filter.nameQuery,
                onValueChange = { onFilterChange(filter.copy(nameQuery = it)) },
                placeholder = { Text(stringResource(Res.string.hint_search_by_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (filter.nameQuery.isNotBlank()) {
                        IconButton(onClick = { onFilterChange(filter.copy(nameQuery = "")) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_clear_search),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            OutlinedButton(onClick = onOpenSheet) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (filter.chipCount > 0) stringResource(Res.string.filters_with_count, filter.chipCount) else stringResource(Res.string.filters_label),
                )
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.action_display_settings),
                )
            }
        }

        if (filter.chipCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                if (filter.gender != GenderFilter.All) {
                    FilterChip(
                        selected = true,
                        onClick = { onFilterChange(filter.copy(gender = GenderFilter.All)) },
                        label = {
                            Text(
                                when (filter.gender) {
                                    GenderFilter.All -> stringResource(Res.string.filter_gender_all)
                                    GenderFilter.Male -> stringResource(Res.string.filter_gender_male)
                                    GenderFilter.Female -> stringResource(Res.string.filter_gender_female)
                                },
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_remove_gender_filter),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                if (filter.birthYearFrom != null || filter.birthYearTo != null) {
                    val label =
                        when {
                            filter.birthYearFrom != null && filter.birthYearTo != null ->
                                "${filter.birthYearFrom}–${filter.birthYearTo}"
                            filter.birthYearFrom != null -> "с ${filter.birthYearFrom}"
                            else -> "до ${filter.birthYearTo}"
                        }
                    FilterChip(
                        selected = true,
                        onClick = { onFilterChange(filter.copy(birthYearFrom = null, birthYearTo = null)) },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_remove_birth_year_filter),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                if (filter.hasDebtOnly) {
                    FilterChip(
                        selected = true,
                        onClick = { onFilterChange(filter.copy(hasDebtOnly = false)) },
                        label = { Text(stringResource(Res.string.filter_chip_has_debt)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                if (filter.noGroupOnly) {
                    FilterChip(
                        selected = true,
                        onClick = { onFilterChange(filter.copy(noGroupOnly = false)) },
                        label = { Text(stringResource(Res.string.filter_no_group)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
