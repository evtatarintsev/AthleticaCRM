package org.athletica.crm.read

import org.athletica.crm.read.clients.ClientBalanceHistoryView
import org.athletica.crm.read.clients.ClientListView
import org.athletica.crm.read.clients.DbClientBalanceHistoryView
import org.athletica.crm.read.clients.DbClientListView
import org.athletica.crm.read.groups.DbGroupDetailView
import org.athletica.crm.read.groups.DbGroupListView
import org.athletica.crm.read.groups.GroupDetailView
import org.athletica.crm.read.groups.GroupListView
import org.athletica.crm.read.home.DbTodayScheduleView
import org.athletica.crm.read.home.TodayScheduleView
import org.athletica.crm.read.tasks.DbTaskDetailView
import org.athletica.crm.read.tasks.DbTaskListView
import org.athletica.crm.read.tasks.TaskDetailView
import org.athletica.crm.read.tasks.TaskListView

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
    /** Список клиентов с фильтрами, сортировкой и пагинацией. */
    val clientList: ClientListView = DbClientListView(),
    /** Журнал операций по личному счёту клиента. */
    val clientBalanceHistory: ClientBalanceHistoryView = DbClientBalanceHistoryView(),
    /** Список групп с расписанием и тренерами. */
    val groupList: GroupListView = DbGroupListView(),
    /** Карточка группы с расписанием, дисциплинами, тренерами и участниками. */
    val groupDetail: GroupDetailView = DbGroupDetailView(),
    /** Список задач с именами исполнителя и клиента. */
    val taskList: TaskListView = DbTaskListView(),
    /** Карточка задачи со связанными именами и вложениями. */
    val taskDetail: TaskDetailView = DbTaskDetailView(),
    /** Расписание занятий на день для главной страницы. */
    val todaySchedule: TodayScheduleView = DbTodayScheduleView(),
)
