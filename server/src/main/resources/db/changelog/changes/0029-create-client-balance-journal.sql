--liquibase formatted sql

--changeset dev:0029-create-client-balance-journal

CREATE TYPE balance_operation_type AS ENUM (
    'sale_overpayment',  -- сдача при продаже зачисляется на счёт клиента
    'sale_payment',      -- оплата продажи с баланса клиента (списание)
    'refund',            -- возврат средств на баланс при отмене продажи
    'admin_credit',      -- административное пополнение (бонус, корректировка)
    'admin_debit'        -- административное списание (корректировка)
);

CREATE TABLE client_balance_journal (
    id              UUID                   NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id          UUID                   NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id       UUID                   NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    amount          NUMERIC(10, 2)         NOT NULL,  -- положительное = пополнение, отрицательное = списание
    balance_after   NUMERIC(10, 2)         NOT NULL,  -- баланс клиента после операции
    operation_type  balance_operation_type NOT NULL,
    ref_entity_type VARCHAR(100),                     -- тип связанной сущности (например 'sale')
    ref_entity_id   UUID,                             -- id связанной сущности
    note            TEXT,                             -- комментарий, обязателен для admin_credit / admin_debit
    performed_by    UUID                   REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ            NOT NULL DEFAULT now(),
    CONSTRAINT chk_balance_amount_nonzero CHECK (amount <> 0)
);

CREATE INDEX idx_balance_journal_client  ON client_balance_journal (client_id, created_at DESC);
CREATE INDEX idx_balance_journal_org     ON client_balance_journal (org_id, created_at DESC);
CREATE INDEX idx_balance_journal_ref     ON client_balance_journal (ref_entity_type, ref_entity_id);
