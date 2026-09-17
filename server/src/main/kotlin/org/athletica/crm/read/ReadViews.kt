package org.athletica.crm.read

import org.athletica.crm.read.clients.ClientListView
import org.athletica.crm.read.clients.DbClientListView
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
    /** Список задач с именами исполнителя и клиента. */
    val taskList: TaskListView = DbTaskListView(),
    /** Карточка задачи со связанными именами и вложениями. */
    val taskDetail: TaskDetailView = DbTaskDetailView(),
    /** Расписание занятий на день для главной страницы. */
    val todaySchedule: TodayScheduleView = DbTodayScheduleView(),
)
