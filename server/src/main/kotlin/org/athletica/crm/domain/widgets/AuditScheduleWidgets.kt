package org.athletica.crm.domain.widgets

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.ScheduleWidgetId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [ScheduleWidgets], оборачивающий выдаваемые виджеты в [AuditScheduleWidget]. */
class AuditScheduleWidgets(private val delegate: ScheduleWidgets, private val audit: AuditLog) : ScheduleWidgets by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(id: ScheduleWidgetId, name: NonBlankName, settings: WidgetSettings) = AuditScheduleWidget(delegate.new(id, name, settings), audit, isNew = true)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: ScheduleWidgetId) = AuditScheduleWidget(delegate.byId(id), audit, isNew = false)
}

/**
 * Декоратор [ScheduleWidget], логирующий сохранение, перевыпуск ключа и удаление.
 * [isNew] — виджет ещё не сохранён, и [save] пишет событие создания, а не изменения.
 */
class AuditScheduleWidget(
    private val delegate: ScheduleWidget,
    private val audit: AuditLog,
    private val isNew: Boolean,
) : ScheduleWidget by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() =
        delegate.save().also {
            val data = Json.encodeToString(snapshot())
            if (isNew) {
                audit.logCreate("schedule_widget", id, data)
            } else {
                audit.logUpdate("schedule_widget", id, data)
            }
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun regenerateKey() =
        delegate.regenerateKey().also {
            audit.logUpdate("schedule_widget", id, Json.encodeToString(KeyRegeneratedAuditData(keyRegenerated = true)))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun delete() =
        delegate.delete().also {
            audit.logDelete("schedule_widget", id, Json.encodeToString(snapshot()))
        }

    override fun withNew(name: NonBlankName, settings: WidgetSettings) = AuditScheduleWidget(delegate.withNew(name, settings), audit, isNew)

    private fun snapshot() =
        ScheduleWidgetAuditData(
            name = name,
            enabled = settings.enabled,
            horizonDays = settings.horizon.days,
            showCoaches = settings.showCoaches,
            showHall = settings.showHall,
            showDisciplines = settings.showDisciplines,
            excludedGroups = settings.excludedGroups,
        )
}

/** Снимок виджета для журнала аудита; публичный ключ в журнал не пишется. */
@Serializable
private data class ScheduleWidgetAuditData(
    val name: String,
    val enabled: Boolean,
    val horizonDays: Int,
    val showCoaches: Boolean,
    val showHall: Boolean,
    val showDisciplines: Boolean,
    val excludedGroups: List<GroupId>,
)

/** Отметка о перевыпуске публичного ключа для журнала аудита. */
@Serializable
private data class KeyRegeneratedAuditData(val keyRegenerated: Boolean)
