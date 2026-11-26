package org.athletica.crm.read

import org.athletica.crm.read.attendance.ClientAttendanceView
import org.athletica.crm.read.attendance.DbClientAttendanceView
import org.athletica.crm.read.attendance.DbGroupAttendanceView
import org.athletica.crm.read.attendance.DbSessionJournalView
import org.athletica.crm.read.attendance.GroupAttendanceView
import org.athletica.crm.read.attendance.SessionJournalView
import org.athletica.crm.read.audit.ActivityLogView
import org.athletica.crm.read.audit.DbActivityLogView
import org.athletica.crm.read.auth.DbSessionListView
import org.athletica.crm.read.auth.SessionListView
import org.athletica.crm.read.channels.ChannelListView
import org.athletica.crm.read.channels.DbChannelListView
import org.athletica.crm.read.clientapp.ClientAppHomeView
import org.athletica.crm.read.clientapp.ClientAppInvitationsView
import org.athletica.crm.read.clientapp.ClientAppProfilesView
import org.athletica.crm.read.clientapp.ClientAppSessionsView
import org.athletica.crm.read.clientapp.DbClientAppHomeView
import org.athletica.crm.read.clientapp.DbClientAppInvitationsView
import org.athletica.crm.read.clientapp.DbClientAppProfilesView
import org.athletica.crm.read.clientapp.DbClientAppSessionsView
import org.athletica.crm.read.clientappadmin.ClientAppInvitePreviewView
import org.athletica.crm.read.clientappadmin.DbClientAppInvitePreviewView
import org.athletica.crm.read.clients.ClientListView
import org.athletica.crm.read.clients.DbClientListView
import org.athletica.crm.read.employees.DbEmployeeDetailView
import org.athletica.crm.read.employees.DbEmployeeListView
import org.athletica.crm.read.employees.EmployeeDetailView
import org.athletica.crm.read.employees.EmployeeListView
import org.athletica.crm.read.finance.CashAccountBalancesView
import org.athletica.crm.read.finance.CashArticlesView
import org.athletica.crm.read.finance.CashFlowReportView
import org.athletica.crm.read.finance.CashJournalView
import org.athletica.crm.read.finance.ClientBalanceView
import org.athletica.crm.read.finance.ClientLedgerView
import org.athletica.crm.read.finance.ClientPaymentOptionsView
import org.athletica.crm.read.finance.DbCashAccountBalancesView
import org.athletica.crm.read.finance.DbCashArticlesView
import org.athletica.crm.read.finance.DbCashFlowReportView
import org.athletica.crm.read.finance.DbCashJournalView
import org.athletica.crm.read.finance.DbClientBalanceView
import org.athletica.crm.read.finance.DbClientLedgerView
import org.athletica.crm.read.finance.DbClientPaymentOptionsView
import org.athletica.crm.read.finance.DbMembershipSalesView
import org.athletica.crm.read.finance.DbSaleDetailView
import org.athletica.crm.read.finance.MembershipSalesView
import org.athletica.crm.read.finance.SaleDetailView
import org.athletica.crm.read.groups.DbGroupDetailView
import org.athletica.crm.read.groups.DbGroupListView
import org.athletica.crm.read.groups.DbGroupRosterView
import org.athletica.crm.read.groups.DbGroupSessionsView
import org.athletica.crm.read.groups.GroupDetailView
import org.athletica.crm.read.groups.GroupListView
import org.athletica.crm.read.groups.GroupRosterView
import org.athletica.crm.read.groups.GroupSessionsView
import org.athletica.crm.read.home.DbTodayScheduleView
import org.athletica.crm.read.home.DbUnclosedSessionsView
import org.athletica.crm.read.home.TodayScheduleView
import org.athletica.crm.read.home.UnclosedSessionsView
import org.athletica.crm.read.memberships.DbMembershipHistoryView
import org.athletica.crm.read.memberships.DbUnpaidSessionsView
import org.athletica.crm.read.memberships.MembershipHistoryView
import org.athletica.crm.read.memberships.UnpaidSessionsView
import org.athletica.crm.read.messaging.ClientChannelsView
import org.athletica.crm.read.messaging.ConversationView
import org.athletica.crm.read.messaging.DbClientChannelsView
import org.athletica.crm.read.messaging.DbConversationView
import org.athletica.crm.read.messaging.DbInboxView
import org.athletica.crm.read.messaging.DbTriageChatView
import org.athletica.crm.read.messaging.InboxView
import org.athletica.crm.read.messaging.TriageChatView
import org.athletica.crm.read.schedule.DbScheduleView
import org.athletica.crm.read.schedule.ScheduleView
import org.athletica.crm.read.sessions.DbSessionDebitPreviewView
import org.athletica.crm.read.sessions.DbSessionDetailView
import org.athletica.crm.read.sessions.SessionDebitPreviewView
import org.athletica.crm.read.sessions.SessionDetailView
import org.athletica.crm.read.tasks.DbTaskDetailView
import org.athletica.crm.read.tasks.DbTaskListView
import org.athletica.crm.read.tasks.TaskDetailView
import org.athletica.crm.read.tasks.TaskListView
import org.athletica.crm.read.widgets.DbPublicScheduleView
import org.athletica.crm.read.widgets.DbWidgetListView
import org.athletica.crm.read.widgets.PublicScheduleView
import org.athletica.crm.read.widgets.WidgetListView

/**
 * Реестр read-проекций приложения: единая точка, через которую маршруты получают
 * доступ к запросам на чтение. Собран отдельно от [org.athletica.crm.Di], чтобы
 * добавление новой проекции не увеличивало список зависимостей приложения,
 * а маршруты принимали одну зависимость вместо набора отдельных view.
 *
 * Значения по умолчанию — реализации поверх PostgreSQL; в тестах подменяются
 * на стабы через конструктор.
 */
data class ReadViews(
    /** Неподключённые приглашения аккаунта кабинета клиента. */
    val clientAppInvitations: ClientAppInvitationsView = DbClientAppInvitationsView(),
    /** Подключённые профили аккаунта кабинета клиента для переключателя. */
    val clientAppProfiles: ClientAppProfilesView = DbClientAppProfilesView(),
    /** Главная кабинета клиента по профилю. */
    val clientAppHome: ClientAppHomeView = DbClientAppHomeView(),
    /** Сеансы аккаунта кабинета клиента. */
    val clientAppSessions: ClientAppSessionsView = DbClientAppSessionsView(),
    /** Подготовка массового приглашения в кабинет клиента. */
    val clientAppInvitePreview: ClientAppInvitePreviewView = DbClientAppInvitePreviewView(),
    /** Список клиентов с фильтрами, сортировкой и пагинацией. */
    val clientList: ClientListView = DbClientListView(),
    /** Журнал операций по личному счёту клиента. */
    val clientBalance: ClientBalanceView = DbClientBalanceView(),
    /** Лицевой счёт клиента с историей операций. */
    val clientLedger: ClientLedgerView = DbClientLedgerView(),
    /** Аванс и долги клиента для блока оплаты. */
    val clientPaymentOptions: ClientPaymentOptionsView = DbClientPaymentOptionsView(),
    /** Остатки касс. */
    val cashAccountBalances: CashAccountBalancesView = DbCashAccountBalancesView(),
    /** Справочник статей ДДС. */
    val cashArticles: CashArticlesView = DbCashArticlesView(),
    /** Кассовый журнал. */
    val cashJournal: CashJournalView = DbCashJournalView(),
    /** Отчёт о движении денежных средств. */
    val cashFlowReport: CashFlowReportView = DbCashFlowReportView(),
    /** Документ продажи. */
    val saleDetail: SaleDetailView = DbSaleDetailView(),
    /** Состояние оплаты абонементов клиента. */
    val membershipSales: MembershipSalesView = DbMembershipSalesView(),
    /** Список групп с расписанием и тренерами. */
    val groupList: GroupListView = DbGroupListView(),
    /** Карточка группы с расписанием, дисциплинами, тренерами и участниками. */
    val groupDetail: GroupDetailView = DbGroupDetailView(),
    /** Занятия группы за период для блока «Занятия» карточки группы. */
    val groupSessions: GroupSessionsView = DbGroupSessionsView(),
    /** Состав группы с абонементом, долгом, балансом и причиной внимания. */
    val groupRoster: GroupRosterView = DbGroupRosterView(),
    /** Список задач с именами исполнителя и клиента. */
    val taskList: TaskListView = DbTaskListView(),
    /** Карточка задачи со связанными именами и вложениями. */
    val taskDetail: TaskDetailView = DbTaskDetailView(),
    /** Расписание занятий на день для главной страницы. */
    val todaySchedule: TodayScheduleView = DbTodayScheduleView(),
    /** Прошедшие занятия с незакрытым журналом для главной страницы. */
    val unclosedSessions: UnclosedSessionsView = DbUnclosedSessionsView(),
    /** Занятия текущего филиала за период для страницы «Расписание». */
    val schedule: ScheduleView = DbScheduleView(),
    /** Карточка занятия с журналом посещаемости. */
    val sessionJournal: SessionJournalView = DbSessionJournalView(),
    /** Сводка посещаемости группы за период. */
    val groupAttendance: GroupAttendanceView = DbGroupAttendanceView(),
    /** Посещаемость клиента за период по всем группам. */
    val clientAttendance: ClientAttendanceView = DbClientAttendanceView(),
    /** Список сотрудников организации с названиями ролей. */
    val employeeList: EmployeeListView = DbEmployeeListView(),
    /** Карточка сотрудника с ролями, персональными правами и филиалами. */
    val employeeDetail: EmployeeDetailView = DbEmployeeDetailView(),
    /** Интеграции каналов связи для раздела настроек «Каналы». */
    val channelList: ChannelListView = DbChannelListView(),
    /** Раздел «Входящие»: беседы клиентов и неразобранные чаты, счётчик непрочитанного. */
    val inbox: InboxView = DbInboxView(),
    /** Сообщения одного неразобранного чата для страницы «Входящие». */
    val triageChat: TriageChatView = DbTriageChatView(),
    /** Списания абонементов участников занятия для карточки занятия. */
    val sessionDebitPreview: SessionDebitPreviewView = DbSessionDebitPreviewView(),
    /** Неоплаченные занятия клиента в группах абонемента. */
    val unpaidSessions: UnpaidSessionsView = DbUnpaidSessionsView(),
    /** История абонемента для панели абонемента. */
    val membershipHistory: MembershipHistoryView = DbMembershipHistoryView(),
    /** Каналы, через которые можно написать клиенту, для страницы переписки. */
    val clientChannels: ClientChannelsView = DbClientChannelsView(),
    /** Журнал действий с названиями субъектов для страницы настроек. */
    val activityLog: ActivityLogView = DbActivityLogView(),
    /** Карточка занятия с названием группы и составом тренеров. */
    val sessionDetail: SessionDetailView = DbSessionDetailView(),
    /** Лента диалога с клиентом по всем каналам. */
    val conversation: ConversationView = DbConversationView(),
    /** Активные сеансы сотрудника для профиля и карточки сотрудника. */
    val sessionList: SessionListView = DbSessionListView(),
    /** Виджеты расписания текущего филиала для раздела настроек. */
    val widgetList: WidgetListView = DbWidgetListView(),
    /** Публичное расписание виджета для посетителей сайта и ВКонтакте. */
    val publicSchedule: PublicScheduleView = DbPublicScheduleView(),
)
