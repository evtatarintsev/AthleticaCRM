package org.athletica.crm.demo

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.athletica.crm.api.schemas.finance.CashPaymentInput
import org.athletica.crm.api.schemas.finance.PaymentInput
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.PositiveInt
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.toFieldKey
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.attendance.AttendanceLabels
import org.athletica.crm.domain.attendance.AttendanceMark
import org.athletica.crm.domain.attendance.AuditAttendanceLabels
import org.athletica.crm.domain.attendance.DbAttendanceLabels
import org.athletica.crm.domain.attendance.DbSessionJournals
import org.athletica.crm.domain.attendance.SessionJournals
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.branch.AuditBranches
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.clientnotes.ClientNotes
import org.athletica.crm.domain.clientnotes.DbClientNotes
import org.athletica.crm.domain.clients.AuditClients
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.customfields.DbCustomFieldDefinitions
import org.athletica.crm.domain.discipline.AuditDisciplines
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.domain.discipline.Disciplines
import org.athletica.crm.domain.employees.AuditEmployees
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.domain.employees.EmployeeRole
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.employees.Roles
import org.athletica.crm.domain.enrollments.AuditEnrollments
import org.athletica.crm.domain.enrollments.DbEnrollments
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.events.DomainEvents
import org.athletica.crm.domain.groups.AuditGroups
import org.athletica.crm.domain.groups.DbGroupSchedule
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.NewSlot
import org.athletica.crm.domain.hall.AuditHalls
import org.athletica.crm.domain.hall.DbHalls
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.domain.leadSource.AuditLeadSources
import org.athletica.crm.domain.leadSource.DbLeadSources
import org.athletica.crm.domain.leadSource.LeadSourceDescription
import org.athletica.crm.domain.leadSource.LeadSources
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.AuditMemberships
import org.athletica.crm.domain.memberships.DbMemberships
import org.athletica.crm.domain.memberships.MembershipDuration
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.domain.sales.Sales
import org.athletica.crm.domain.sessions.AuditSessions
import org.athletica.crm.domain.sessions.DbScheduleSync
import org.athletica.crm.domain.sessions.DbSessions
import org.athletica.crm.domain.sessions.Session
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.domain.tariffs.AuditTariffPlans
import org.athletica.crm.domain.tariffs.DbTariffPlans
import org.athletica.crm.domain.tariffs.TariffPlans
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.usecases.finance.ClientPayments
import org.athletica.crm.usecases.groups.setGroupSchedule
import org.athletica.crm.usecases.memberships.freezeMembership
import org.athletica.crm.usecases.memberships.issueMembership
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * Наполнитель пустой организации историей работы за несколько месяцев.
 *
 * Симуляция идёт по дням от `today - история` до `today`: клиенты приходят, группы открываются,
 * абонементы продаются и оплачиваются, занятия проводятся с отметками. Решения принимаются по
 * модели [DemoWorld], а записываются доменными операциями с деловой датой текущего дня.
 * Списания по посещениям применит обработчик событий после коммита, закрытие истёкших абонементов —
 * планировщик абонементов; модель предсказывает их итог, поэтому у клиента в каждый момент не больше
 * одного абонемента в сроке.
 *
 * Зависимости — доменные репозитории и команды; по умолчанию те же реализации, что и в `Di`.
 */
class DemoData(
    private val audit: AuditLog = PostgresAuditLog(),
    private val branches: Branches = AuditBranches(DbBranches(), audit),
    private val halls: Halls = AuditHalls(DbHalls(), audit),
    private val roles: Roles = DbRoles(),
    private val employees: Employees = AuditEmployees(DbEmployees(DbUsers(PasswordHasher()), roles), audit),
    private val disciplines: Disciplines = AuditDisciplines(DbDisciplines(), audit),
    private val leadSources: LeadSources = AuditLeadSources(DbLeadSources(), audit),
    private val customFields: CustomFieldDefinitions = DbCustomFieldDefinitions(),
    private val tariffPlans: TariffPlans = AuditTariffPlans(DbTariffPlans(), audit),
    private val attendanceLabels: AttendanceLabels = AuditAttendanceLabels(DbAttendanceLabels(), audit),
    private val cashAccounts: CashAccounts = CashAccounts(),
    private val clients: Clients = AuditClients(DbClients(customFields), audit),
    private val clientContacts: ClientContacts = DbClientContacts(),
    private val clientNotes: ClientNotes = DbClientNotes(),
    private val groups: Groups = AuditGroups(DbGroups(), audit),
    private val enrollments: Enrollments = AuditEnrollments(DbEnrollments(), audit),
    private val events: DomainEvents = DomainEventBus(),
    private val sessions: Sessions = AuditSessions(DbSessions(events), audit),
    private val journals: SessionJournals = DbSessionJournals(attendanceLabels, events),
    private val memberships: Memberships = AuditMemberships(DbMemberships(), audit),
    private val sales: Sales = Sales(),
    private val clientPayments: ClientPayments = ClientPayments(audit = audit),
    private val tasks: Tasks = DbTasks(),
    private val notifications: Notifications = DbNotifications(),
) {
    /**
     * Наполняет пустую организацию контекста [ctx] (контекст её владельца) историей профиля [profile],
     * которая заканчивается днём [today]; вся случайность берётся из [random]. [history] — длина
     * периода истории, по умолчанию из профиля. Уже заполненная организация (см. [ensureEmpty])
     * отклоняется ошибкой `DEMO_ORG_NOT_EMPTY` до первой записи.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun fill(
        profile: DemoProfile,
        random: Random,
        today: LocalDate,
        history: DatePeriod = DatePeriod(months = profile.historyMonths),
    ): DemoSummary {
        ensureEmpty()
        val start = today.minus(history)
        val run = Run(profile, random, today, start, DemoWorld(), timezone())
        run.setUp()
        var day = start
        while (day <= today) {
            run.simulate(day)
            day = day.plus(1, DateTimeUnit.DAY)
        }
        return DemoSummary(
            clients = run.world.clients.size,
            groups = run.world.groups.count { it.opened },
            sessions = sessions.listAll(start, today.plus(1, DateTimeUnit.YEAR)).size,
            sales = run.world.sales,
        )
    }

    /**
     * Отклоняет организацию, в которой есть что-то сверх созданного регистрацией: клиенты, группы,
     * дисциплины, тарифы, кассы, кастомные поля, второй филиал или зал, источник лидов или метка
     * с названием из наполнения. Иначе наполнение упало бы посередине на уникальности названий
     * или перезаписало бы настройки организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun ensureEmpty() {
        val busy =
            tr.sql(
                """
                SELECT EXISTS (SELECT 1 FROM clients WHERE org_id = :org)
                    OR EXISTS (SELECT 1 FROM groups WHERE org_id = :org)
                    OR EXISTS (SELECT 1 FROM disciplines WHERE org_id = :org)
                    OR EXISTS (SELECT 1 FROM subscription_tariffs WHERE org_id = :org)
                    OR EXISTS (SELECT 1 FROM cash_accounts WHERE org_id = :org)
                    OR EXISTS (SELECT 1 FROM custom_field_definitions WHERE org_id = :org)
                    OR (SELECT count(*) FROM branches WHERE org_id = :org) > 1
                    OR (SELECT count(*) FROM halls WHERE org_id = :org) > 1
                    OR EXISTS (SELECT 1 FROM lead_sources WHERE org_id = :org AND name = :leadSource)
                    OR EXISTS (SELECT 1 FROM attendance_labels WHERE org_id = :org AND lower(name) IN (lower(:freezeLabel), lower(:absentLabel)))
                    AS busy
                """.trimIndent(),
            ).bind("org", ctx.orgId)
                .bind("leadSource", PARTNER_LEAD_SOURCE)
                .bind("freezeLabel", FREEZE_LABEL)
                .bind("absentLabel", ABSENT_LABEL)
                .firstOrNull { it.asBoolean("busy") } ?: false
        ensure(!busy) { CommonDomainError("DEMO_ORG_NOT_EMPTY", Messages.DemoOrgNotEmpty.localize()) }
    }

    /** Часовой пояс организации контекста. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun timezone(): TimeZone =
        TimeZone.of(
            tr.sql("SELECT timezone FROM organizations WHERE id = :org").bind("org", ctx.orgId).firstOrNull { it.asString("timezone") } ?: "UTC",
        )

    /** Один прогон наполнения: состояние модели и шаги одного дня. */
    private inner class Run(
        private val profile: DemoProfile,
        private val random: Random,
        private val today: LocalDate,
        private val start: LocalDate,
        val world: DemoWorld,
        private val timezone: TimeZone,
    ) {
        /** Дни появления клиентов по порядку; последний клиент приходит сегодня. */
        private val arrivals: List<LocalDate> =
            List(profile.clients) { index ->
                if (index < maxOf(FORCED_CLIENTS, profile.clients / 4)) {
                    start
                } else {
                    start.plus(random.nextInt(start.daysUntil(today) + 1), DateTimeUnit.DAY)
                }
            }.sorted().let { it.dropLast(1) + today }

        /** Источники лидов организации. */
        private var leadSourceIds: List<LeadSourceId> = emptyList()

        /**
         * Справочники и структура до цикла по дням: филиалы и залы, роли и сотрудники,
         * дисциплины, источники лидов, кастомные поля, тарифы, метки, кассы и план групп.
         */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        suspend fun setUp() {
            val trainerRole = role("Тренер", emptySet())
            val adminRole = role("Администратор", setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE, UserPermission.CAN_ACCEPT_PAYMENTS, UserPermission.CAN_MANAGE_TASKS))
            val managerRole =
                role(
                    "Менеджер",
                    setOf(
                        UserPermission.CAN_VIEW_CLIENT_BALANCE, UserPermission.CAN_ACCEPT_PAYMENTS, UserPermission.CAN_VIEW_FINANCE,
                        UserPermission.CAN_VIEW_ALL_TASKS, UserPermission.CAN_MANAGE_TASKS,
                    ),
                )
            val branchIds = listOf(ctx.branchId) + BRANCH_NAMES.take(profile.branches - 1).map { branches.new(BranchId.new(), NonBlankName.from(it)).also { branch -> branch.save() }.id }
            val trainersPerBranch = maxOf(2, profile.groups / profile.branches / 2 + 1)
            branchIds.forEach { branchId ->
                val hallIds =
                    context(ctx.copy(branchId = branchId)) {
                        val created = HALL_NAMES.map { halls.new(HallId.new(), NonBlankName.from(it)).also { hall -> hall.save() }.id }
                        halls.list().map { it.id }.ifEmpty { created }
                    }
                val trainers = List(trainersPerBranch) { employee(trainerRole, EmployeeBranchAccess.Selected(listOf(branchId))) }
                world.taskAssignees += employee(adminRole, EmployeeBranchAccess.Selected(listOf(branchId)))
                world.branches += DemoBranch(branchId, hallIds, trainers)
            }
            world.taskAssignees += employee(managerRole, EmployeeBranchAccess.All)

            val disciplineIds = DISCIPLINES.take(minOf(DISCIPLINES.size, profile.groups / 3 + 3)).map { disciplines.new(DisciplineId.new(), NonBlankName.from(it)).also { discipline -> discipline.save() }.id to it }
            leadSourceIds =
                leadSources.list().map { it.id } +
                leadSources.new(LeadSourceId.new(), NonBlankName.from(PARTNER_LEAD_SOURCE), LeadSourceDescription.EMPTY).also { it.save() }.id
            customFields.saveAll(
                CLIENT_FIELDS,
                listOf(
                    CustomFieldDefinition.Select(LEVEL_FIELD.toFieldKey(), "Уровень подготовки", options = LEVELS),
                    CustomFieldDefinition.Bool(MEDICAL_FIELD.toFieldKey(), "Есть медицинская справка"),
                    CustomFieldDefinition.Text(PARENT_FIELD.toFieldKey(), "Имя родителя"),
                ),
            )
            world.tariffs += tariff("Пробное занятие", 1, 7, DurationUnit.DAYS, money(500), CloseMode.ON_LAST_SESSION)
            world.tariffs += tariff("8 занятий", 8, 1, DurationUnit.MONTHS, money(4000), CloseMode.ON_LAST_SESSION)
            world.tariffs += tariff("12 занятий", 12, 1, DurationUnit.MONTHS, money(5400), CloseMode.ON_LAST_SESSION)
            world.tariffs += tariff("Безлимит на месяц", null, 1, DurationUnit.MONTHS, money(6500), CloseMode.ON_EXPIRY)

            world.freezeLabel = attendanceLabels.new(AttendanceLabelId.new(), FREEZE_LABEL, AttendanceLabelScope.ABSENT).also { it.save() }.id
            attendanceLabels.list(includeArchived = false).forEach {
                when (it.scope) {
                    AttendanceLabelScope.PRESENT -> world.presentLabels += it.id
                    AttendanceLabelScope.ABSENT ->
                        if (it.id != world.freezeLabel) {
                            world.absentLabels += it.id
                        }
                    AttendanceLabelScope.ANY -> Unit
                }
            }
            if (world.absentLabels.isEmpty()) {
                world.absentLabels += attendanceLabels.new(AttendanceLabelId.new(), ABSENT_LABEL, AttendanceLabelScope.ABSENT).also { it.save() }.id
            }
            world.cashAccounts += cashAccount("Касса", CashAccountKind.CASH)
            world.cashAccounts += cashAccount("Эквайринг", CashAccountKind.ACQUIRING)

            planGroups(disciplineIds.map { it.first to it.second })
        }

        /** Шаги одного дня [day] в порядке, в котором их выполнила бы организация. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        suspend fun simulate(day: LocalDate) {
            openGroups(day)
            DbScheduleSync(DemoCalendar(day)).sync(day)
            arrive(day)
            churn(day)
            DemoTimestamps.shift(day)
            planRenewals(day)
            freeze(day)
            sell(day)
            collect(day)
            hold(day)
            work(day)
            DemoTimestamps.shift(day)
        }

        /** Планирует группы: половина открыта с первого дня, остальные — в первые две трети истории. */
        context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
        private fun planGroups(disciplineIds: List<Pair<DisciplineId, String>>) {
            val names = disciplineIds.flatMap { (id, name) -> GROUP_LEVELS.map { level -> id to "$name · $level" } }.shuffled(random).take(profile.groups)
            val laterDays = maxOf(0, start.daysUntil(today) * 2 / 3 - 7)
            names.forEachIndexed { index, (disciplineId, name) ->
                val branch = world.branches[index % world.branches.size]
                val opensOn = if (index < (profile.groups + 1) / 2 || laterDays == 0) start else start.plus(7 + random.nextInt(laterDays), DateTimeUnit.DAY)
                val pattern = if (index == 0) todayPattern() else SLOT_PATTERNS.random(random)
                val startAt = LocalTime(START_HOURS.random(random), 0)
                val minutes = listOf(60, 90).random(random)
                val endAt = LocalTime((startAt.hour * 60 + minutes) / 60, minutes % 60)
                val hall = branch.halls.random(random)
                world.groups += DemoGroup(GroupId.new(), name, branch.id, opensOn, pattern.map { NewSlot(it, startAt, endAt, hall) }, branch.trainers.random(random))
                groupDisciplines[world.groups.last().id] = disciplineId
            }
        }

        /** Дисциплина каждой запланированной группы. */
        private val groupDisciplines = mutableMapOf<GroupId, DisciplineId>()

        /** Расписание первой группы: включает день недели [today], чтобы на сегодня были занятия. */
        private fun todayPattern(): List<DayOfWeek> {
            val todayDow = DayOfWeek.entries[today.dayOfWeek.ordinal]
            val other = DayOfWeek.entries[(today.dayOfWeek.ordinal + 2) % 7]
            return listOf(todayDow, other)
        }

        /** Открывает группы, запланированные на [day]: создание, расписание с этого дня и запись «клиента всех групп». */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun openGroups(day: LocalDate) {
            world.groups.filter { it.opensOn == day }.forEach { group ->
                context(ctx.copy(branchId = group.branchId)) {
                    groups.new(group.id, NonBlankName.from(group.name), listOf(groupDisciplines.getValue(group.id)), listOf(group.trainer))
                    val calendar = DemoCalendar(day)
                    setGroupSchedule(DbGroupSchedule(calendar, DbScheduleSync(calendar)), groups, employees, notifications, group.id, day, group.slots)
                }
                group.opened = true
                world.clients.firstOrNull { it.index == ALL_GROUPS_CLIENT && it.branchId == group.branchId }?.let { enroll(it, group.id) }
            }
        }

        /** Новые клиенты дня [day] с контактами, заметками и кастомными полями; часть записывается в группы. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun arrive(day: LocalDate) {
            arrivals.withIndex().filter { it.value == day }.forEach { (index, _) ->
                val openBranches = world.branches.filter { branch -> world.groups.any { it.opened && it.branchId == branch.id } }
                val branchId = if (index == ALL_GROUPS_CLIENT) world.branches.first().id else openBranches.random(random).id
                val person = random.person()
                val kid = random.nextBoolean()
                val fields =
                    buildList {
                        add(CustomFieldValue.Select(LEVEL_FIELD.toFieldKey(), LEVELS.random(random)))
                        add(CustomFieldValue.Bool(MEDICAL_FIELD.toFieldKey(), random.nextDouble() < 0.7))
                        if (kid) {
                            add(CustomFieldValue.Text(PARENT_FIELD.toFieldKey(), random.person().name))
                        }
                    }
                val birthday = if (kid) random.birthday(day, 6, 16) else random.birthday(day, 18, 55)
                val created = clients.new(ClientId.new(), NonBlankName.from(person.name), null, birthday, person.gender, leadSourceIds.random(random), fields)
                val contacts =
                    listOfNotNull(
                        ClientContact(ClientContactId.new(), created.id, ContactType.PHONE, random.phone()),
                        if (random.nextBoolean()) ClientContact(ClientContactId.new(), created.id, ContactType.EMAIL, random.email(person)) else null,
                    )
                clientContacts.replace(created, contacts)
                if (random.nextDouble() < 0.3) {
                    clientNotes.add(created.id, ClientNoteText.from(random.note()))
                }
                val client = DemoClient(created.id, index, branchId, 0.6 + random.nextDouble() * 0.35, habit(index), preferredTariff(), churnDay(index, day))
                world.clients += client
                if (index == LEAD_CLIENT || (index >= FORCED_CLIENTS && index != profile.clients - 1 && random.nextDouble() < 0.08)) {
                    return@forEach
                }
                val branchGroups = world.groups.filter { it.opened && it.branchId == branchId }
                val joined = if (index == ALL_GROUPS_CLIENT) branchGroups else branchGroups.shuffled(random).take(if (random.nextDouble() < 0.25) 2 else 1)
                joined.forEach { enroll(client, it.id) }
                client.buyOn = day
            }
        }

        /** Привычка оплаты клиента с номером [index]; первые клиенты гарантируют каждую привычку. */
        private fun habit(index: Int): PaymentHabit =
            when (index) {
                ADVANCE_CLIENT -> PaymentHabit.ADVANCE
                NEVER_CLIENT -> PaymentHabit.NEVER
                PARTS_CLIENT -> PaymentHabit.PARTS
                LATE_CLIENT -> PaymentHabit.LATE
                in 0 until FORCED_CLIENTS -> PaymentHabit.FULL
                else -> {
                    val roll = random.nextDouble()
                    when {
                        roll < 0.55 -> PaymentHabit.FULL
                        roll < 0.7 -> PaymentHabit.PARTS
                        roll < 0.85 -> PaymentHabit.LATE
                        roll < 0.95 -> PaymentHabit.NEVER
                        else -> PaymentHabit.ADVANCE
                    }
                }
            }

        /** Тариф, который клиент покупает после пробного. */
        private fun preferredTariff(): DemoTariff = world.tariffs.drop(1).random(random)

        /**
         * День ухода клиента с номером [index], пришедшего в день [arrival]: уходят клиенты из первой
         * трети истории, не раньше чем через пять недель после прихода и не позже чем за полтора
         * месяца до сегодняшнего дня.
         */
        private fun churnDay(index: Int, arrival: LocalDate): LocalDate? {
            val from = arrival.plus(35, DateTimeUnit.DAY)
            val to = today.minus(45, DateTimeUnit.DAY)
            val early = start.daysUntil(arrival) <= start.daysUntil(today) / 3
            val leaves = index == CHURN_CLIENT || (index >= FORCED_CLIENTS && early && random.nextDouble() < 0.15)
            return if (leaves && from < to) from.plus(random.nextInt(from.daysUntil(to)), DateTimeUnit.DAY) else null
        }

        /** Записывает клиента [client] в группу [groupId]. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun enroll(client: DemoClient, groupId: GroupId) {
            enrollments.add(groupId, listOf(client.id))
            client.groups += groupId
        }

        /** Уход клиентов в день [day]: выход из всех групп. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun churn(day: LocalDate) {
            world.clients.filter { it.churnOn == day }.forEach { client ->
                client.groups.forEach { enrollments.remove(it, listOf(client.id)) }
                client.groups.clear()
                client.buyOn = null
            }
        }

        /** Планирует продление тем, чей абонемент исчерпан или истёк, через 0–3 дня. */
        private fun planRenewals(day: LocalDate) {
            world.clients
                .filter { it.buyOn == null && it.groups.isNotEmpty() && !it.churnedBy(day) && it.membership?.overBy(day) == true }
                .forEach { it.buyOn = day.plus(random.nextInt(4), DateTimeUnit.DAY) }
        }

        /**
         * Заморозки дня [day]: изредка у действующих абонементов; за три дня до сегодняшнего —
         * обязательно, если иначе на сегодня не будет замороженного абонемента.
         */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun freeze(day: LocalDate) {
            val candidates =
                world.clients.filter { client ->
                    val membership = client.membership
                    client.churnOn == null && membership != null && membership.freeze == null && membership.usable(client.groups.firstOrNull() ?: return@filter false, day)
                }
            val forced = day == today.minus(3, DateTimeUnit.DAY) && world.clients.none { it.membership?.frozenOn(today) == true }
            candidates.forEachIndexed { position, client ->
                if ((forced && position == 0) || random.nextDouble() < 0.004) {
                    val membership = checkNotNull(client.membership)
                    val end = day.plus(7 + random.nextInt(8) - 1, DateTimeUnit.DAY)
                    val frozen = freezeMembership(memberships, DemoCalendar(day), membership.id, day, end, FREEZE_REASONS.random(random))
                    membership.freeze = day..end
                    membership.end = frozen.endDate
                }
            }
        }

        /** Продажи дня [day] тем, кому запланирована покупка; сегодняшние — с активацией по первому посещению. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun sell(day: LocalDate) {
            world.clients.filter { it.buyOn != null && checkNotNull(it.buyOn) <= day && it.groups.isNotEmpty() && !it.churnedBy(day) }.forEach { client ->
                client.buyOn = null
                val tariff = if (client.purchases == 0 && random.nextDouble() < 0.35) world.tariffs.first() else client.tariff
                val activation = if (day == today) Activation.OnFirstVisit(today.plus(14, DateTimeUnit.DAY)) else Activation.Immediate
                val saleId = SaleId.new()
                val groupIds = client.groups.toList()
                val issued =
                    issueMembership(
                        memberships, MembershipId.new(), client.id, tariff.id, tariff.name, tariff.sessions?.let { PositiveInt.from(it) }, groupIds,
                        MembershipDuration(PositiveInt.from(tariff.duration), tariff.unit), tariff.price, activation, tariff.closeMode, day, saleId, sales,
                    )
                client.membership =
                    when (issued) {
                        is ActiveMembership -> DemoMembership(issued.id, groupIds.toSet(), issued.startDate, issued.endDate, tariff.sessions)
                        else -> DemoMembership(issued.id, groupIds.toSet(), null, null, tariff.sessions)
                    }
                schedulePayments(client, saleId, tariff.price, day)
                client.purchases++
                world.sales++
            }
        }

        /** Планирует оплаты продажи [saleId] на [price] по привычке клиента [client]. */
        context(ctx: EmployeeRequestContext)
        private fun schedulePayments(
            client: DemoClient,
            saleId: SaleId,
            price: Money,
            day: LocalDate,
        ) {
            /** День оплаты через [from] (включительно) – [until] (не включительно) дней после продажи. */
            fun later(from: Int, until: Int) = day.plus(from + random.nextInt(until - from), DateTimeUnit.DAY)
            val half = Money(price.minorUnits / 2, price.currency)
            when (client.habit) {
                PaymentHabit.FULL -> world.payments += DemoPayment(day, saleId, client.id, price)
                PaymentHabit.ADVANCE -> world.payments += DemoPayment(day, saleId, client.id, if (client.purchases == 0) price + money(1000) else price)
                PaymentHabit.LATE -> world.payments += DemoPayment(later(3, 18), saleId, client.id, price)
                PaymentHabit.PARTS -> {
                    world.payments += DemoPayment(day, saleId, client.id, half)
                    world.payments += DemoPayment(later(5, 20), saleId, client.id, price - half)
                }
                PaymentHabit.NEVER -> Unit
            }
        }

        /** Принимает оплаты, запланированные на [day]. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun collect(day: LocalDate) {
            val due = world.payments.filter { it.day == day }
            world.payments.removeAll { it.day == day }
            due.forEach { payment ->
                val cash = CashPaymentInput(world.cashAccounts.random(random), payment.amount)
                clientPayments.pay(payment.saleId, payment.clientId, PaymentInput(listOf(cash)), day)
            }
        }

        /**
         * Занятия дня [day]: редкая отмена, отметки по вероятности посещения, изредка разовый участник,
         * закрытие журнала. В последние три дня до сегодняшнего часть журналов остаётся открытой;
         * сегодняшние занятия не трогаются.
         */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun hold(day: LocalDate) {
            if (day >= today) {
                return
            }
            val recent = day >= today.minus(3, DateTimeUnit.DAY)
            sessions.listAll(day, day)
                .filter { it.status == "scheduled" }
                .sortedWith(compareBy<Session> { it.startTime }.thenBy { world.group(it.groupId).name })
                .forEach { session ->
                    if (random.nextDouble() < 0.02) {
                        session.cancel()
                        return@forEach
                    }
                    val leaveOpen = recent && (!openJournalLeft || random.nextDouble() < 0.25)
                    openJournalLeft = openJournalLeft || leaveOpen
                    holdSession(session, day, leaveOpen)
                }
        }

        /** Оставлен ли уже хотя бы один открытый журнал среди последних дней. */
        private var openJournalLeft = false

        /** Проводит занятие [session] дня [day]; при [leaveOpen] отмечает часть участников и не закрывает журнал. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun holdSession(session: Session, day: LocalDate, leaveOpen: Boolean) {
            val group = world.group(session.groupId)
            val regular = journals.byId(session.id).participants.map { it.clientId }.toSet()
            val guest =
                if (!leaveOpen && random.nextDouble() < 0.06) {
                    world.clients
                        .filter { it.branchId == group.branchId && it.groups.isNotEmpty() && !it.churnedBy(day) && it.id !in regular }
                        .randomOrNull(random)
                        ?.also { journals.byId(session.id).addOneTime(it.id) }
                } else {
                    null
                }
            val journal = journals.byId(session.id)
            journal.participants
                .map { participant -> participant to checkNotNull(world.client(participant.clientId)) }
                .sortedBy { it.second.index }
                .forEach { (participant, client) ->
                    if (leaveOpen && random.nextDouble() < 0.5) {
                        return@forEach
                    }
                    val membership = client.membership
                    val present =
                        if (participant.kind == ParticipationKind.ONE_TIME) {
                            client == guest
                        } else {
                            !client.churnedBy(day) && membership?.usable(group.id, day) == true && random.nextDouble() < client.attendance
                        }
                    if (present) {
                        val labels = if (world.presentLabels.isNotEmpty() && random.nextDouble() < 0.05) setOf(world.presentLabels.random(random)) else emptySet()
                        journal.mark(client.id, AttendanceMark.Present(labels))
                        if (!leaveOpen && membership?.usable(group.id, day) == true) {
                            membership.remaining = membership.remaining?.minus(1)
                        }
                    } else {
                        val label = if (membership?.frozenOn(day) == true) checkNotNull(world.freezeLabel) else world.absentLabels.random(random)
                        journal.mark(client.id, AttendanceMark.Absent.from(setOf(label)))
                    }
                }
            if (!leaveOpen) {
                session.complete()
            }
        }

        /** Задачи дня [day]: новые с исполнителями и закрытие запланированных; последняя неделя остаётся открытой. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun work(day: LocalDate) {
            world.tasks.filter { it.closeOn == day }.forEach { tasks.byId(it.id).status(TaskStatus.COMPLETED).save() }
            val candidates = world.clients.filter { it.groups.isNotEmpty() }
            if (candidates.isEmpty() || world.taskAssignees.isEmpty() || (day != today && random.nextDouble() >= 0.3)) {
                return
            }
            val id = TaskId.new()
            val (title, description) = TASKS.random(random)
            val due = day.plus(2, DateTimeUnit.DAY).atTime(LocalTime(12, 0)).toInstant(timezone)
            tasks.new(id, NonBlankName.from(title), description, candidates.random(random).id, due, null)
                .assignTo(world.taskAssignees.random(random))
                .save()
            val closes = day <= today.minus(7, DateTimeUnit.DAY) && random.nextDouble() < 0.85
            world.tasks += DemoTask(id, if (closes) day.plus(1 + random.nextInt(6), DateTimeUnit.DAY) else null)
        }

        /** Создаёт роль [name] с правами [permissions]. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun role(name: String, permissions: Set<UserPermission>): EmployeeRole = EmployeeRole(Uuid.generateV7(), NonBlankName.from(name), permissions).also { roles.new(it) }

        /** Создаёт сотрудника без учётной записи с ролью [role] и доступом [access]. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun employee(role: EmployeeRole, access: EmployeeBranchAccess): Employee {
            val person = random.person()
            return employees.new(
                EmployeeId.new(), NonBlankName.from(person.name), random.phone(), EmailAddress(random.email(person)), null,
                EmployeePermissionSelection(roleIds = listOf(role.id)), access,
            )
        }

        /** Создаёт тариф и возвращает его вместе с режимом закрытия абонементов. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun tariff(
            name: String,
            sessionCount: Int?,
            duration: Int,
            unit: DurationUnit,
            price: Money,
            closeMode: CloseMode,
        ): DemoTariff {
            val id = TariffPlanId.new()
            tariffPlans.new(id, NonBlankName.from(name), sessionCount?.let { PositiveInt.from(it) }, PositiveInt.from(duration), unit, price).save()
            return DemoTariff(id, name, sessionCount, duration, unit, price, closeMode)
        }

        /** Создаёт кассу организации. */
        context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
        private suspend fun cashAccount(name: String, kind: CashAccountKind): CashAccountId = CashAccountId.new().also { cashAccounts.save(it, name, kind, null, null, null) }
    }

    /** Сумма [major] в основных единицах валюты организации. */
    context(ctx: EmployeeRequestContext)
    private fun money(major: Long): Money = Money(major * (1..ctx.currency.fractionDigits).fold(1L) { acc, _ -> acc * 10 }, ctx.currency)

    private companion object {
        /** Первые клиенты с заранее заданными ролями в истории. */
        const val FORCED_CLIENTS = 7

        /** Клиент, который состоит во всех группах своего филиала. */
        const val ALL_GROUPS_CLIENT = 0

        /** Клиент без групп. */
        const val LEAD_CLIENT = 1

        /** Клиент с авансом. */
        const val ADVANCE_CLIENT = 2

        /** Клиент, который не платит. */
        const val NEVER_CLIENT = 3

        /** Клиент, который платит частями. */
        const val PARTS_CLIENT = 4

        /** Клиент, который платит с задержкой. */
        const val LATE_CLIENT = 5

        /** Клиент, который уходит. */
        const val CHURN_CLIENT = 6

        /** Источник лидов, который создаёт наполнение. */
        const val PARTNER_LEAD_SOURCE = "Партнёрская акция"

        /** Метка отсутствия на время заморозки, которую создаёт наполнение. */
        const val FREEZE_LABEL = "Заморозка"

        /** Метка отсутствия, которую наполнение создаёт, если у организации нет ни одной действующей. */
        const val ABSENT_LABEL = "Не пришёл без причины"

        /** Тип сущности кастомных полей клиента. */
        const val CLIENT_FIELDS = "CLIENT"

        /** Ключ поля уровня подготовки. */
        const val LEVEL_FIELD = "level"

        /** Ключ поля медицинской справки. */
        const val MEDICAL_FIELD = "medical_certificate"

        /** Ключ поля имени родителя. */
        const val PARENT_FIELD = "parent_name"

        /** Варианты уровня подготовки. */
        val LEVELS = listOf("Новичок", "Средний", "Продвинутый")

        /** Названия дополнительных филиалов. */
        val BRANCH_NAMES = listOf("Филиал на Лесной", "Филиал на Садовой", "Филиал в Заречье")

        /** Названия дополнительных залов каждого филиала. */
        val HALL_NAMES = listOf("Большой зал", "Малый зал")

        /** Дисциплины организации. */
        val DISCIPLINES = listOf("Бокс", "Йога", "Танцы", "Карате", "Гимнастика", "Плавание", "Футбол", "Шахматы", "Акробатика", "Теннис", "Самбо", "Скалолазание", "Растяжка", "Капоэйра")

        /** Уровни групп внутри дисциплины. */
        val GROUP_LEVELS = listOf("начинающие", "продолжающие", "дети 6–9 лет", "подростки", "взрослые")

        /** Наборы дней недели расписания. */
        val SLOT_PATTERNS =
            listOf(
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            )

        /** Часы начала занятий. */
        val START_HOURS = listOf(9, 10, 11, 16, 17, 18, 19, 20)

        /** Причины заморозки. */
        val FREEZE_REASONS = listOf("Болезнь", "Отпуск", "Командировка", "Сессия в институте")

        /** Заголовки и описания задач администраторов. */
        val TASKS =
            listOf(
                "Позвонить по продлению" to "Абонемент заканчивается, предложить продление",
                "Напомнить об оплате" to "Есть неоплаченная продажа",
                "Уточнить расписание" to "Клиент просил подобрать удобное время",
                "Пригласить на открытую тренировку" to "Рассказать о мероприятии в субботу",
                "Собрать документы" to "Нужна медицинская справка",
            )
    }
}
