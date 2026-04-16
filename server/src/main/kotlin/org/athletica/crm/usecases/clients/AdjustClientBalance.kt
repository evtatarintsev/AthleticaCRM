package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logBalanceAdjust
import org.athletica.crm.i18n.Messages
import kotlin.uuid.Uuid

/**
 * Вносит административную корректировку баланса клиента.
 * Тип операции определяется знаком [AdjustBalanceRequest.amount]:
 * положительный → `admin_credit`, отрицательный → `admin_debit`.
 * Возвращает обновлённые данные клиента.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun adjustClientBalance(request: AdjustBalanceRequest): Either<CommonDomainError, Unit> =
    either {
        if (request.amount == 0.0) {
            raise(CommonDomainError("BALANCE_AMOUNT_ZERO", Messages.BalanceAmountZero.localize()))
        }
        if (request.note.isBlank()) {
            raise(CommonDomainError("BALANCE_NOTE_REQUIRED", Messages.BalanceNoteRequired.localize()))
        }

        // Проверяем, что клиент принадлежит организации
        db
            .sql("SELECT 1 FROM clients WHERE id = :id AND org_id = :orgId")
            .bind("id", request.clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { true }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        val operationType = if (request.amount > 0) "admin_credit" else "admin_debit"

        // balance_after вычисляется прямо в INSERT подзапросом — атомарно на уровне БД,
        // без отдельного SELECT, который создавал бы race condition при конкурентных запросах.
        db
            .sql(
                """
                INSERT INTO client_balance_journal
                    (id, org_id, client_id, amount, balance_after, operation_type, note, performed_by)
                VALUES (
                    :id, :orgId, :clientId, :amount,
                    COALESCE((SELECT SUM(j.amount) FROM client_balance_journal j WHERE j.client_id = :clientId), 0) + :amount,
                    :operationType::balance_operation_type, :note, :performedBy
                )
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", ctx.orgId)
            .bind("clientId", request.clientId)
            .bind("amount", java.math.BigDecimal(request.amount.toString()))
            .bind("operationType", operationType)
            .bind("note", request.note)
            .bind("performedBy", ctx.userId)
            .execute()

        audit.logBalanceAdjust(
            clientId = request.clientId,
            amount = request.amount,
            operationType = operationType,
            note = request.note,
        )
    }
