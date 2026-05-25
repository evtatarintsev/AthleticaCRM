-- Статусы платёжной транзакции
CREATE TYPE payment_status AS ENUM ('pending', 'paid', 'cancelled');

-- История платёжных транзакций
CREATE TABLE payment_transactions (
    id                  UUID PRIMARY KEY,
    org_id              UUID NOT NULL REFERENCES organizations(id),
    gateway_name        VARCHAR(50) NOT NULL,        -- "yookassa", "cloudpayments", ...
    external_payment_id VARCHAR(255) NOT NULL,       -- ID платежа на стороне шлюза
    amount              NUMERIC(12, 2) NOT NULL,     -- в основных единицах (рубли), для совместимости с asMoney()
    currency            VARCHAR(3) NOT NULL,
    status              payment_status NOT NULL DEFAULT 'pending',
    description         VARCHAR(500) NOT NULL,
    created_by          UUID NOT NULL REFERENCES employees(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at        TIMESTAMPTZ,
    confirmation_url    VARCHAR(2048),

    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_gateway_external_id UNIQUE (gateway_name, external_payment_id)
);

CREATE INDEX idx_payment_transactions_lookup
    ON payment_transactions(gateway_name, external_payment_id);
CREATE INDEX idx_payment_transactions_org
    ON payment_transactions(org_id, created_at DESC);

COMMENT ON TABLE payment_transactions IS 'Платёжные транзакции (история пополнений баланса через внешние шлюзы)';
COMMENT ON COLUMN payment_transactions.gateway_name IS 'Идентификатор шлюза: yookassa, cloudpayments, ...';
COMMENT ON COLUMN payment_transactions.external_payment_id IS 'ID платежа на стороне шлюза (из webhook)';
