package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.filter_birthday
import org.athletica.crm.generated.resources.filter_birthday_all
import org.athletica.crm.generated.resources.filter_birthday_this_week
import org.athletica.crm.generated.resources.filter_birthday_today
import org.athletica.crm.generated.resources.filter_birthday_tomorrow
import org.athletica.crm.generated.resources.filter_gender_all
import org.athletica.crm.generated.resources.filter_gender_female
import org.athletica.crm.generated.resources.filter_gender_male
import org.athletica.crm.generated.resources.filter_has_debt
import org.athletica.crm.generated.resources.filter_no_group
import org.athletica.crm.generated.resources.label_gender
import org.jetbrains.compose.resources.stringResource

/**
 * Содержимое панели фильтров клиентов.
 * Используется как слот внутри [org.athletica.crm.ui.list.ListPageFilterPanel].
 * Контейнер (заголовок, Reset, Apply) предоставляется scaffold-ом.
 *
 * [draft] — черновик фильтра; изменяется без перезагрузки списка.
 * [onDraftChange] — обновить черновик.
 */
@Composable
fun ClientsFilterContent(
    draft: ClientFilterState,
    onDraftChange: (ClientFilterState) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.label_gender), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GenderFilter.entries.forEachIndexed { index, g ->
                    SegmentedButton(
                        selected = draft.gender == g,
                        onClick = { onDraftChange(draft.copy(gender = g)) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = GenderFilter.entries.size,
                            ),
                    ) {
                        Text(
                            when (g) {
                                GenderFilter.All -> stringResource(Res.string.filter_gender_all)
                                GenderFilter.Male -> stringResource(Res.string.filter_gender_male)
                                GenderFilter.Female -> stringResource(Res.string.filter_gender_female)
                            },
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.filter_no_group),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = draft.noGroupOnly,
                onCheckedChange = { onDraftChange(draft.copy(noGroupOnly = it)) },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.filter_has_debt),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = draft.hasDebtOnly,
                onCheckedChange = { onDraftChange(draft.copy(hasDebtOnly = it)) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.filter_birthday), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BirthdayFilter.entries.forEachIndexed { index, bf ->
                    SegmentedButton(
                        selected = draft.birthdayFilter == bf,
                        onClick = { onDraftChange(draft.copy(birthdayFilter = bf)) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = BirthdayFilter.entries.size,
                            ),
                    ) {
                        Text(
                            when (bf) {
                                BirthdayFilter.None -> stringResource(Res.string.filter_birthday_all)
                                BirthdayFilter.Today -> stringResource(Res.string.filter_birthday_today)
                                BirthdayFilter.Tomorrow -> stringResource(Res.string.filter_birthday_tomorrow)
                                BirthdayFilter.ThisWeek -> stringResource(Res.string.filter_birthday_this_week)
                            },
                        )
                    }
                }
            }
        }
    }
}
