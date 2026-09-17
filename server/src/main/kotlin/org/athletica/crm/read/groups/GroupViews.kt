package org.athletica.crm.read.groups

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Параметры выборки списка групп. Пустой список означает отсутствие фильтра,
 * пустая строка поиска приводится к `null` вызывающим слоем.
 */
data class GroupListQuery(
    /** Поиск по подстроке в названии (регистронезависимо). `null` или пробельная строка — без поиска. */
    val nameQuery: String? = null,
    /** Оставить группы, у которых есть хотя бы одна из дисциплин. */
    val disciplineIds: List<DisciplineId> = emptyList(),
    /** Оставить группы, у которых есть хотя бы один из тренеров. */
    val employeeIds: List<EmployeeId> = emptyList(),
)

/**
 * Read-проекция списка групп: расписание с названиями залов и тренеры приходят
 * JSONB-агрегатами, без загрузки справочников сотрудников и залов целиком.
 */
interface GroupListView {
    /**
     * Возвращает группы, подходящие под [query].
     * `total` в ответе — общее число групп организации (и филиала, если он задан)
     * без учёта фильтров: список групп не постраничный.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(query: GroupListQuery): GroupListResponse

    /** Возвращает группы в сокращённом виде для выпадающих списков. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun forSelect(): List<GroupSelectItem>
}

/**
 * Read-проекция карточки группы: расписание с названиями залов, дисциплины,
 * тренеры и активные участники — одним запросом.
 */
interface GroupDetailView {
    /** Возвращает карточку группы [id]; ошибка, если группа не найдена в организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: GroupId): GroupDetailResponse
}
