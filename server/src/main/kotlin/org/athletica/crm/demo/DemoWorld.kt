package org.athletica.crm.demo

import kotlinx.datetime.LocalDate
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.groups.NewSlot

/**
 * Модель организации в памяти, по которой симуляция принимает решения: кто пришёл, у кого какой
 * абонемент и остаток, кто кому должен. В БД решения записываются доменными операциями.
 */
internal class DemoWorld {
    /** Филиалы с их залами, тренерами и администраторами. */
    val branches = mutableListOf<DemoBranch>()

    /** Группы в порядке открытия. */
    val groups = mutableListOf<DemoGroup>()

    /** Клиенты в порядке появления. */
    val clients = mutableListOf<DemoClient>()

    /** Тарифы, из которых продаются абонементы. */
    val tariffs = mutableListOf<DemoTariff>()

    /** Кассы для приёма оплат. */
    val cashAccounts = mutableListOf<CashAccountId>()

    /** Метки «не пришёл» без метки заморозки. */
    val absentLabels = mutableListOf<AttendanceLabelId>()

    /** Метки «пришёл». */
    val presentLabels = mutableListOf<AttendanceLabelId>()

    /** Метка отсутствия на время заморозки. */
    var freezeLabel: AttendanceLabelId? = null

    /** Запланированные оплаты, ещё не принятые. */
    val payments = mutableListOf<DemoPayment>()

    /** Открытые задачи и день, в который их закроют. */
    val tasks = mutableListOf<DemoTask>()

    /** Сотрудники, которым назначаются задачи. */
    val taskAssignees = mutableListOf<Employee>()

    /** Число оформленных продаж. */
    var sales = 0

    /** Клиент по идентификатору. */
    fun client(id: ClientId): DemoClient? = clients.find { it.id == id }

    /** Группа по идентификатору. */
    fun group(id: GroupId): DemoGroup = groups.first { it.id == id }
}

/** Филиал: [id], его залы [halls], тренеры [trainers]. */
internal class DemoBranch(
    /** Идентификатор филиала. */
    val id: BranchId,
    /** Залы филиала. */
    val halls: List<HallId>,
    /** Тренеры с доступом к филиалу. */
    val trainers: List<Employee>,
)

/** Группа: открывается в день [opensOn] с расписанием [slots]. */
internal class DemoGroup(
    /** Идентификатор группы. */
    val id: GroupId,
    /** Уникальное название группы. */
    val name: String,
    /** Филиал группы. */
    val branchId: BranchId,
    /** День открытия группы. */
    val opensOn: LocalDate,
    /** Правила расписания. */
    val slots: List<NewSlot>,
    /** Тренер группы. */
    val trainer: Employee,
) {
    /** Открыта ли группа к этому дню симуляции. */
    var opened = false
}

/** Тариф: [sessions] занятий (`null` — безлимит) на [months] месяцев по цене [price]. */
internal class DemoTariff(
    /** Идентификатор тарифа. */
    val id: TariffPlanId,
    /** Название тарифа и абонемента. */
    val name: String,
    /** Количество занятий; `null` — безлимит. */
    val sessions: Int?,
    /** Длина срока. */
    val duration: Int,
    /** Единица срока. */
    val unit: DurationUnit,
    /** Цена. */
    val price: Money,
    /** Режим закрытия абонемента. */
    val closeMode: CloseMode,
)

/** Как клиент оплачивает продажи. */
internal enum class PaymentHabit {
    /** Сразу и полностью. */
    FULL,

    /** Половину сразу, остальное позже. */
    PARTS,

    /** Полностью, но через несколько дней. */
    LATE,

    /** Не платит: должник. */
    NEVER,

    /** Первую продажу оплачивает с запасом — остаётся аванс. */
    ADVANCE,
}

/**
 * Клиент модели. [attendance] — вероятность прийти на занятие, [habit] — привычка оплаты,
 * [churnOn] — день, с которого клиент перестаёт ходить и не продлевает абонемент.
 */
internal class DemoClient(
    /** Идентификатор клиента. */
    val id: ClientId,
    /** Порядковый номер появления; задаёт порядок обхода. */
    val index: Int,
    /** Филиал, в котором занимается клиент. */
    val branchId: BranchId,
    /** Вероятность прийти на занятие. */
    val attendance: Double,
    /** Привычка оплаты. */
    val habit: PaymentHabit,
    /** Предпочитаемый тариф после пробного. */
    val tariff: DemoTariff,
    /** День ухода; `null` — клиент не уходит. */
    val churnOn: LocalDate?,
) {
    /** Группы, в которых клиент сейчас состоит. */
    val groups = mutableListOf<GroupId>()

    /** Текущий абонемент; `null` — абонемента ещё не было. */
    var membership: DemoMembership? = null

    /** День следующей покупки; `null` — покупка не запланирована. */
    var buyOn: LocalDate? = null

    /** Число купленных абонементов. */
    var purchases = 0

    /** Ушёл ли клиент к дню [day]. */
    fun churnedBy(day: LocalDate): Boolean = churnOn != null && day >= churnOn
}

/**
 * Абонемент модели с остатком [remaining] (`null` — безлимит) на период [start]..[end];
 * [start] — `null`, пока абонемент не активирован.
 */
internal class DemoMembership(
    /** Идентификатор абонемента. */
    val id: MembershipId,
    /** Группы абонемента. */
    val groups: Set<GroupId>,
    /** Дата начала; `null` — не активирован. */
    val start: LocalDate?,
    /** Дата окончания; `null` — не активирован. */
    var end: LocalDate?,
    /** Остаток занятий; `null` — безлимит. */
    var remaining: Int?,
) {
    /** Период заморозки; `null` — заморозки не было. */
    var freeze: ClosedRange<LocalDate>? = null

    /** Можно ли списать с абонемента занятие группы [groupId] дня [day]. */
    fun usable(groupId: GroupId, day: LocalDate): Boolean {
        val from = start ?: return false
        val to = end ?: return false
        return groupId in groups && day in from..to && (remaining ?: 1) > 0 && freeze?.contains(day) != true
    }

    /** Заморожен ли абонемент в день [day]. */
    fun frozenOn(day: LocalDate): Boolean = freeze?.contains(day) == true

    /** Закончился ли абонемент к дню [day]: исчерпан или истёк. */
    fun overBy(day: LocalDate): Boolean = start != null && (remaining == 0 || day > checkNotNull(end))
}

/** Оплата продажи [saleId] клиента [clientId] на [amount], которую клиент внесёт в день [day]. */
internal data class DemoPayment(
    /** День оплаты. */
    val day: LocalDate,
    /** Оплачиваемая продажа. */
    val saleId: SaleId,
    /** Клиент. */
    val clientId: ClientId,
    /** Сумма оплаты. */
    val amount: Money,
)

/** Задача [id], которую закроют в день [closeOn]; `null` — останется открытой. */
internal data class DemoTask(
    /** Идентификатор задачи. */
    val id: TaskId,
    /** День закрытия; `null` — не закрывается. */
    val closeOn: LocalDate?,
)
