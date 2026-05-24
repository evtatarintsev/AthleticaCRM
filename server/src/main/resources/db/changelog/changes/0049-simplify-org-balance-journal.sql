--liquibase formatted sql

--changeset dev:0049-simplify-org-balance-journal
-- Убираем performed_by и payment_method: баланс орга меняет система или платёжный сервис,
-- а не конкретный сотрудник; платёжной интеграции пока нет.
-- description становится обязательным — для ручных операций это всегда требуется.

ALTER TABLE org_balance_journal DROP CONSTRAINT IF EXISTS chk_payment_method_for_replenishment;
ALTER TABLE org_balance_journal DROP CONSTRAINT IF EXISTS org_balance_journal_performed_by_fkey;
DROP INDEX IF EXISTS idx_org_balance_journal_performed;

ALTER TABLE org_balance_journal DROP COLUMN IF EXISTS performed_by;
ALTER TABLE org_balance_journal DROP COLUMN IF EXISTS payment_method;

DROP TYPE IF EXISTS payment_method;

UPDATE org_balance_journal SET description = '' WHERE description IS NULL;
ALTER TABLE org_balance_journal ALTER COLUMN description SET NOT NULL;
