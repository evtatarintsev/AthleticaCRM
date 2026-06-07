package org.athletica.crm.components.clients.subscription

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.memberships.IssueMembershipRequest
import org.athletica.crm.components.clients.ClientsApiError
import org.athletica.crm.components.clients.toClientsApiError
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import kotlin.time.Clock

/** Шаг мастера выдачи абонемента. */
enum class IssueStep { CHOOSE_PLAN, PARAMS, PAYMENT, FREE_OF_CHARGE }

/** Состояние загрузки списка групп организации. */
sealed interface GroupsData {
    /** Группы загружаются. */
    data object Loading : GroupsData

    /** Ошибка загрузки групп. */
    data class Error(val error: ClientsApiError) : GroupsData

    /** Группы загружены. */
    data class Loaded(val groups: List<GroupSelectItem>) : GroupsData
}

/** Состояние экрана выдачи абонемента. */
sealed interface IssueScreenState {
    /** Загрузка данных клиента. */
    data object Loading : IssueScreenState

    /** Ошибка загрузки клиента. */
    data class Error(val error: ClientsApiError) : IssueScreenState

    /**
     * Данные загружены, мастер активен.
     */
    data class Ready(
        /** Имя клиента (для заголовка). */
        val clientName: String,
        /** Валюта организации (берётся из баланса клиента). */
        val currency: Currency,
        /** Текущий баланс клиента. */
        val currentBalance: Money,
        /** Текущий шаг мастера. */
        val step: IssueStep,
        /** Параметры абонемента; `null`, пока тариф не выбран. */
        val form: SubscriptionForm?,
        /** Параметры оплаты. */
        val payment: PaymentForm,
        /** Комментарий для шага «оплата не требуется». */
        val freeComment: String,
        /** Список групп для выбора. */
        val groups: GroupsData,
        /** Доступные тарифные планы. */
        val planOptions: List<TariffPlan>,
    ) : IssueScreenState
}

/**
 * ViewModel мастера выдачи абонемента.
 *
 * Держит ровно одну ячейку состояния [state]; все переходы — чистые функции над
 * [IssueScreenState.Ready], присваивающие новое значение. Загружает клиента (для
 * валюты и баланса) и список групп. Сохранения нет — [onIssued] вызывается как
 * заглушка завершения (бекенд появится позже).
 */
class IssueSubscriptionViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
    private val onIssued: () -> Unit,
) {
    var state by mutableStateOf<IssueScreenState>(IssueScreenState.Loading)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            api.clients.detail(clientId).fold(
                ifLeft = { state = IssueScreenState.Error(it.toClientsApiError()) },
                ifRight = { client ->
                    state =
                        IssueScreenState.Ready(
                            clientName = client.name,
                            currency = client.balance.currency,
                            currentBalance = client.balance,
                            step = IssueStep.CHOOSE_PLAN,
                            form = null,
                            payment =
                                PaymentForm(
                                    fromBalance = Money.zero(client.balance.currency),
                                    cashGiven = Money.zero(client.balance.currency),
                                    changeToBalance = Money.zero(client.balance.currency),
                                ),
                            freeComment = "",
                            groups = GroupsData.Loading,
                            planOptions = emptyList(),
                        )
                    loadGroups()
                    loadTariffs()
                },
            )
        }
    }

    private fun loadTariffs() {
        scope.launch {
            api.tariffs.list().onRight { response ->
                val plans =
                    response.tariffs.map { tariff ->
                        TariffPlan(
                            id = tariff.id,
                            name = tariff.name,
                            sessions = tariff.sessions,
                            durationValue = tariff.durationValue,
                            durationUnit = tariff.durationUnit,
                            price = tariff.price,
                        )
                    }
                updateReady { it.copy(planOptions = plans) }
            }
        }
    }

    private fun loadGroups() {
        scope.launch {
            api.groups.listForSelect().fold(
                ifLeft = { err -> updateReady { it.copy(groups = GroupsData.Error(err.toClientsApiError())) } },
                ifRight = { groups -> updateReady { it.copy(groups = GroupsData.Loaded(groups)) } },
            )
        }
    }

    /** Выбор тарифа: строит новую форму с префиллом и переходит к параметрам. */
    fun onPlanChosen(plan: TariffPlan?) =
        updateReady { ready ->
            val currency = ready.currency
            val form =
                if (plan != null) {
                    SubscriptionForm(
                        plan = plan,
                        groupIds = emptySet(),
                        unlimited = plan.sessions == null,
                        sessions = plan.sessions,
                        durationValue = plan.durationValue,
                        durationUnit = plan.durationUnit,
                        startDate = today(),
                        price = plan.price,
                        discount = Discount.None,
                    )
                } else {
                    SubscriptionForm(
                        plan = null,
                        groupIds = emptySet(),
                        unlimited = false,
                        sessions = null,
                        durationValue = 1,
                        durationUnit = DurationUnit.MONTHS,
                        startDate = today(),
                        price = Money.zero(currency),
                        discount = Discount.None,
                    )
                }
            ready.copy(step = IssueStep.PARAMS, form = form)
        }

    /** Возврат к выбору тарифа. */
    fun backToPlanChoice() = updateReady { it.copy(step = IssueStep.CHOOSE_PLAN) }

    /** Возврат к параметрам с любого шага оплаты. */
    fun backToParams() = updateReady { it.copy(step = IssueStep.PARAMS) }

    /** Переход к оплате деньгами/балансом. */
    fun goToPayment() =
        updateReady { ready ->
            val total = ready.form?.total ?: return@updateReady ready
            ready.copy(
                step = IssueStep.PAYMENT,
                payment =
                    ready.payment.copy(
                        cashGiven = total,
                        fromBalance = Money.zero(ready.currency),
                        changeToBalance = Money.zero(ready.currency),
                    ),
            )
        }

    /** Переход к выдаче без оплаты. */
    fun goToFreeOfCharge() = updateReady { it.copy(step = IssueStep.FREE_OF_CHARGE) }

    // ── правки параметров ───────────────────────────────────────────────────

    /** Переключает признак безлимита. */
    fun onUnlimitedToggled(unlimited: Boolean) = updateForm { it.copy(unlimited = unlimited, sessions = if (unlimited) null else it.sessions) }

    /** Меняет количество занятий. */
    fun onSessionsChanged(sessions: Int?) = updateForm { it.copy(sessions = sessions) }

    /** Меняет числовое значение срока действия. */
    fun onDurationValueChanged(value: Int) = updateForm { it.copy(durationValue = value) }

    /** Меняет единицу измерения срока действия. */
    fun onDurationUnitChanged(unit: DurationUnit) = updateForm { it.copy(durationUnit = unit) }

    /** Меняет дату начала. */
    fun onStartDateChanged(date: LocalDate) = updateForm { it.copy(startDate = date) }

    /** Меняет стоимость. */
    fun onPriceChanged(price: Money) = updateForm { it.copy(price = price) }

    /** Меняет скидку. */
    fun onDiscountChanged(discount: Discount) = updateForm { it.copy(discount = discount) }

    /** Добавляет/убирает группу действия абонемента. */
    fun onGroupToggled(groupId: GroupId) =
        updateForm { form ->
            val ids = if (groupId in form.groupIds) form.groupIds - groupId else form.groupIds + groupId
            form.copy(groupIds = ids)
        }

    // ── правки оплаты ─────────────────────────────────────────────────────────

    /** Меняет сумму списания с баланса. */
    fun onFromBalanceChanged(amount: Money) = updateReady { it.copy(payment = it.payment.copy(fromBalance = amount)) }

    /** Меняет внесённую деньгами сумму. */
    fun onCashChanged(amount: Money) = updateReady { it.copy(payment = it.payment.copy(cashGiven = amount)) }

    /** Меняет часть сдачи, зачисляемую на счёт. */
    fun onChangeToBalanceChanged(amount: Money) = updateReady { it.copy(payment = it.payment.copy(changeToBalance = amount)) }

    /** Меняет комментарий для выдачи без оплаты. */
    fun onFreeCommentChanged(comment: String) = updateReady { it.copy(freeComment = comment) }

    /**
     * Завершение выдачи: отправляет на бекенд параметры абонемента и при успехе вызывает [onIssued].
     * Для индивидуального абонемента (без тарифа) используется [individualName] —
     * локализованное название, переданное из UI. Оплата (наличные/баланс/сдача) на бекенд
     * пока не передаётся — это отдельная задача.
     */
    fun onConfirm(individualName: String) {
        val ready = state as? IssueScreenState.Ready ?: return
        val form = ready.form ?: return
        scope.launch {
            api.memberships
                .issue(
                    IssueMembershipRequest(
                        id = MembershipId.new(),
                        clientId = clientId,
                        tariffPlanId = form.plan?.id,
                        name = form.plan?.name ?: individualName,
                        sessions = if (form.unlimited) null else form.sessions,
                        durationValue = form.durationValue,
                        durationUnit = form.durationUnit,
                        startDate = form.startDate,
                        price = form.total,
                    ),
                ).onRight { onIssued() }
        }
    }

    private inline fun updateReady(block: (IssueScreenState.Ready) -> IssueScreenState.Ready) {
        val ready = state as? IssueScreenState.Ready ?: return
        state = block(ready)
    }

    private inline fun updateForm(block: (SubscriptionForm) -> SubscriptionForm) =
        updateReady { ready ->
            val form = ready.form ?: return@updateReady ready
            ready.copy(form = block(form))
        }

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
