--liquibase formatted sql

--changeset dev:0034-create-org-balance-journal

CREATE TYPE org_balance_operation_type AS ENUM (
    'replenishment',  -- пополнение через платежную систему
    'bonus',          -- начисление бонусов
    'system_fee',     -- списание за использование системы
    'admin_credit',   -- административное пополнение
    'admin_debit'     -- административное списание
);

CREATE TYPE payment_method AS ENUM (
    'card',           -- платежная карта
    'paypal',         -- PayPal
    'bank_transfer',  -- банковский перевод
    'crypto',         -- криптовалюта
    'other'           -- другое
);

CREATE TABLE org_balance_journal (
    id              UUID                      NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id          UUID                      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    amount          NUMERIC(12, 2)            NOT NULL,  -- положительное = пополнение, отрицательное = списание
    balance_after   NUMERIC(12, 2)            NOT NULL,  -- баланс организации после операции
    operation_type  org_balance_operation_type NOT NULL,
    payment_method  payment_method,                      -- метод пополнения (заполняется только для replenishment)
    description     TEXT,                               -- текстовое описание операции
    performed_by    UUID                      REFERENCES users (id) ON DELETE SET NULL,  -- администратор, выполнивший операцию
    created_at      TIMESTAMPTZ               NOT NULL DEFAULT now(),
    CONSTRAINT chk_org_balance_amount_nonzero CHECK (amount <> 0),
    CONSTRAINT chk_payment_method_for_replenishment CHECK (
        (operation_type = 'replenishment' AND payment_method IS NOT NULL) OR
        (operation_type != 'replenishment' AND payment_method IS NULL)
    )
);

CREATE INDEX idx_org_balance_journal_org       ON org_balance_journal (org_id, created_at DESC);
CREATE INDEX idx_org_balance_journal_type      ON org_balance_journal (operation_type);
CREATE INDEX idx_org_balance_journal_performed ON org_balance_journal (performed_by);
