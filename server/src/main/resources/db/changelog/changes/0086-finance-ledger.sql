--liquibase formatted sql

--changeset dev:0086-finance-ledger
-- До продакшена: тестовые абонементы и старый баланс не переносятся в главную книгу.
DELETE FROM memberships;
DROP TABLE client_balance_journal;
DROP TYPE balance_operation_type;

ALTER TABLE clients ADD CONSTRAINT uq_clients_org_id UNIQUE (org_id, id);
ALTER TABLE branches ADD CONSTRAINT uq_branches_org_id UNIQUE (org_id, id);
ALTER TABLE employees ADD CONSTRAINT uq_employees_org_id UNIQUE (org_id, id);

CREATE TABLE cash_accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(255) NOT NULL CHECK (btrim(name) <> ''),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('cash', 'bank', 'acquiring', 'online')),
    branch_id UUID,
    archived BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (org_id, id),
    FOREIGN KEY (org_id, branch_id) REFERENCES branches (org_id, id)
);
CREATE UNIQUE INDEX uq_cash_accounts_name ON cash_accounts (org_id, lower(name)) WHERE NOT archived;

CREATE TABLE cash_articles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(255) NOT NULL CHECK (btrim(name) <> ''),
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('income', 'expense')),
    parent_id UUID,
    is_group BOOLEAN NOT NULL DEFAULT false,
    system_code VARCHAR(32),
    archived BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (org_id, id),
    UNIQUE (org_id, system_code),
    FOREIGN KEY (org_id, parent_id) REFERENCES cash_articles (org_id, id),
    CHECK (parent_id IS DISTINCT FROM id),
    CHECK (system_code IS NULL OR (
        parent_id IS NULL AND NOT is_group AND NOT archived AND
        ((system_code = 'client_receipts' AND direction = 'income') OR
         (system_code = 'client_refunds' AND direction = 'expense'))
    ))
);
CREATE UNIQUE INDEX uq_cash_articles_name ON cash_articles (org_id, parent_id, lower(name)) NULLS NOT DISTINCT WHERE NOT archived;

CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id UUID NOT NULL REFERENCES organizations (id),
    client_id UUID NOT NULL,
    sold_on DATE NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancel_reason TEXT,
    UNIQUE (org_id, id),
    UNIQUE (org_id, id, client_id),
    FOREIGN KEY (org_id, client_id) REFERENCES clients (org_id, id),
    FOREIGN KEY (org_id, created_by) REFERENCES employees (org_id, id),
    FOREIGN KEY (org_id, cancelled_by) REFERENCES employees (org_id, id),
    CHECK ((cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL) OR
           (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND btrim(cancel_reason) <> '' AND cancel_reason IS NOT NULL))
);
CREATE INDEX idx_sales_client ON sales (org_id, client_id, sold_on DESC);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sale_id UUID NOT NULL REFERENCES sales (id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('membership', 'service')),
    name VARCHAR(255) NOT NULL CHECK (btrim(name) <> ''),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(18, 2) NOT NULL CHECK (unit_price >= 0),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount = quantity * unit_price)
);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);

ALTER TABLE memberships
    DROP COLUMN price,
    ADD COLUMN sale_item_id UUID NOT NULL UNIQUE REFERENCES sale_items (id);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id UUID NOT NULL REFERENCES organizations (id),
    kind VARCHAR(16) NOT NULL,
    cash_account_id UUID,
    client_id UUID,
    sale_id UUID,
    article_id UUID,
    UNIQUE (org_id, id),
    CONSTRAINT uq_ledger_account_owner UNIQUE NULLS NOT DISTINCT (org_id, kind, cash_account_id, client_id, sale_id, article_id),
    FOREIGN KEY (org_id, cash_account_id) REFERENCES cash_accounts (org_id, id),
    FOREIGN KEY (org_id, client_id) REFERENCES clients (org_id, id),
    FOREIGN KEY (org_id, sale_id, client_id) REFERENCES sales (org_id, id, client_id),
    FOREIGN KEY (org_id, article_id) REFERENCES cash_articles (org_id, id),
    CONSTRAINT chk_ledger_account_owner CHECK (
        (kind = 'cash' AND cash_account_id IS NOT NULL AND client_id IS NULL AND sale_id IS NULL AND article_id IS NULL) OR
        (kind = 'advance' AND cash_account_id IS NULL AND client_id IS NOT NULL AND sale_id IS NULL AND article_id IS NULL) OR
        (kind = 'receivable' AND cash_account_id IS NULL AND client_id IS NOT NULL AND sale_id IS NOT NULL AND article_id IS NULL) OR
        (kind IN ('income', 'expense') AND cash_account_id IS NULL AND client_id IS NULL AND sale_id IS NULL AND article_id IS NOT NULL) OR
        (kind IN ('revenue', 'adjustment', 'opening') AND cash_account_id IS NULL AND client_id IS NULL AND sale_id IS NULL AND article_id IS NULL)
    )
);
CREATE INDEX idx_ledger_accounts_client ON ledger_accounts (org_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id UUID NOT NULL REFERENCES organizations (id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('sale', 'sale_return', 'payment', 'payout', 'adjustment', 'income', 'expense', 'transfer', 'opening', 'reversal')),
    occurred_on DATE NOT NULL,
    advance_used NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (advance_used >= 0),
    sale_id UUID,
    reverses_id UUID UNIQUE,
    counterparty_employee_id UUID,
    counterparty_text TEXT,
    comment TEXT,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (org_id, id),
    FOREIGN KEY (org_id, sale_id) REFERENCES sales (org_id, id),
    FOREIGN KEY (org_id, reverses_id) REFERENCES ledger_transactions (org_id, id),
    FOREIGN KEY (org_id, counterparty_employee_id) REFERENCES employees (org_id, id),
    FOREIGN KEY (org_id, created_by) REFERENCES employees (org_id, id),
    CHECK ((kind = 'reversal') = (reverses_id IS NOT NULL)),
    CHECK (reverses_id IS DISTINCT FROM id),
    CHECK (counterparty_employee_id IS NULL OR counterparty_text IS NULL)
);
CREATE INDEX idx_ledger_transactions_date ON ledger_transactions (org_id, occurred_on DESC, id DESC);
CREATE INDEX idx_ledger_transactions_sale ON ledger_transactions (sale_id) WHERE sale_id IS NOT NULL;

CREATE TABLE ledger_postings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions (id),
    account_id UUID NOT NULL REFERENCES ledger_accounts (id),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount <> 0),
    article_id UUID REFERENCES cash_articles (id)
);
CREATE INDEX idx_ledger_postings_transaction ON ledger_postings (transaction_id);
CREATE INDEX idx_ledger_postings_account ON ledger_postings (account_id);

INSERT INTO cash_articles (org_id, name, direction, system_code)
SELECT o.id, a.name, a.direction, a.system_code
FROM organizations o CROSS JOIN (VALUES
    ('Поступления от клиентов', 'income', 'client_receipts'),
    ('Возвраты клиентам', 'expense', 'client_refunds'),
    ('Прочие доходы', 'income', NULL),
    ('Аренда', 'expense', NULL),
    ('Зарплата', 'expense', NULL),
    ('Налоги', 'expense', NULL),
    ('Реклама', 'expense', NULL),
    ('Инвентарь', 'expense', NULL),
    ('Комиссия эквайринга', 'expense', NULL),
    ('Прочие расходы', 'expense', NULL)
) AS a(name, direction, system_code);

--changeset dev:0086-finance-ledger-constraints splitStatements:false
CREATE FUNCTION check_ledger_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    entry_id UUID;
    entry ledger_transactions%ROWTYPE;
    posting_count BIGINT;
    total NUMERIC;
    operation_kind VARCHAR(16);
BEGIN
    IF TG_TABLE_NAME = 'ledger_transactions' THEN
        entry_id := NEW.id;
    ELSE
        entry_id := NEW.transaction_id;
    END IF;
    SELECT * INTO STRICT entry FROM ledger_transactions WHERE id = entry_id;
    SELECT count(*), coalesce(sum(amount), 0) INTO posting_count, total
      FROM ledger_postings WHERE transaction_id = entry_id;
    IF posting_count < 2 OR total <> 0 THEN
        RAISE EXCEPTION 'Ledger entry must have at least two postings with zero sum'
            USING ERRCODE = '23514', CONSTRAINT = 'chk_ledger_entry_balanced';
    END IF;
    operation_kind := entry.kind;
    IF entry.kind = 'reversal' THEN
        SELECT kind INTO operation_kind FROM ledger_transactions WHERE id = entry.reverses_id;
        IF operation_kind = 'reversal' THEN
            RAISE EXCEPTION 'Cannot reverse a reversal' USING ERRCODE = '23514', CONSTRAINT = 'chk_ledger_reversal';
        END IF;
    END IF;
    IF EXISTS (
        SELECT 1 FROM ledger_postings p JOIN ledger_accounts a ON a.id = p.account_id
        LEFT JOIN cash_articles c ON c.id = p.article_id
        WHERE p.transaction_id = entry_id AND (
            a.org_id <> entry.org_id OR
            (p.article_id IS NOT NULL AND c.org_id <> entry.org_id) OR
            (a.kind = 'cash' AND operation_kind NOT IN ('transfer', 'opening') AND p.article_id IS NULL)
        )
    ) THEN
        RAISE EXCEPTION 'Ledger posting has invalid organization or cash article'
            USING ERRCODE = '23514', CONSTRAINT = 'chk_ledger_posting_scope_article';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_entry_balanced
AFTER INSERT ON ledger_transactions DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION check_ledger_entry();

CREATE CONSTRAINT TRIGGER ledger_postings_balanced
AFTER INSERT ON ledger_postings DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION check_ledger_entry();

CREATE FUNCTION reject_ledger_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are immutable'
        USING ERRCODE = '23514', CONSTRAINT = 'chk_ledger_immutable';
END;
$$;

CREATE TRIGGER ledger_transactions_immutable BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
CREATE TRIGGER ledger_postings_immutable BEFORE UPDATE OR DELETE ON ledger_postings
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
CREATE TRIGGER ledger_accounts_immutable BEFORE UPDATE OR DELETE ON ledger_accounts
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
