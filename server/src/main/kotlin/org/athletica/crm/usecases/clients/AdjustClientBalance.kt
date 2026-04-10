package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.uuid.Uuid

/**
 * Вносит административную корректировку баланса клиента.
 * Тип операции определяется знаком [AdjustBalanceRequest.amount]:
 * положительный → `admin_credit`, отрицательный → `admin_debit`.
 * Возвращает обновлённые данные клиента.
 */
context(db: Database, ctx: RequestContext)
suspend fun adjustClientBalance(request: AdjustBalanceRequest): Either<CommonDomainError, ClientDetailResponse> =
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
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { true }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        // Текущий баланс клиента
        val currentBalance =
            db
                .sql("SELECT COALESCE(SUM(amount), 0) AS balance FROM client_balance_journal WHERE client_id = :clientId")
                .bind("clientId", request.clientId)
                .firstOrNull { row -> row.get("balance", java.math.BigDecimal::class.java)!!.toDouble() } ?: 0.0

        val balanceAfter = currentBalance + request.amount
        val operationType = if (request.amount > 0) "admin_credit" else "admin_debit"

        db
            .sql(
                """
                INSERT INTO client_balance_journal
                    (id, org_id, client_id, amount, balance_after, operation_type, note, performed_by)
                VALUES
                    (:id, :orgId, :clientId, :amount, :balanceAfter, :operationType::balance_operation_type, :note, :performedBy)
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", ctx.orgId.value)
            .bind("clientId", request.clientId)
            .bind("amount", java.math.BigDecimal(request.amount.toString()))
            .bind("balanceAfter", java.math.BigDecimal(balanceAfter.toString()))
            .bind("operationType", operationType)
            .bind("note", request.note)
            .bind("performedBy", ctx.userId.value)
            .execute()

        clientDetail(request.clientId).bind()
    }
