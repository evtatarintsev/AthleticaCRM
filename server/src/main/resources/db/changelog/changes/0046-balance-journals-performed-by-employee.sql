--liquibase formatted sql

--changeset dev:0046-balance-journals-performed-by-employee
-- Переводим колонку performed_by с users(id) на employees(id) и делаем её NOT NULL.
-- Сотрудники не удаляются физически (soft-delete через employees.is_active),
-- поэтому ON DELETE RESTRICT гарантирует ссылочную целостность журналов навсегда.
-- Историю журналов очищаем — обратная совместимость данных не требуется.

DELETE FROM client_balance_journal;
DELETE FROM org_balance_journal;

ALTER TABLE client_balance_journal
    DROP CONSTRAINT client_balance_journal_performed_by_fkey;
ALTER TABLE client_balance_journal
    ALTER COLUMN performed_by SET NOT NULL;
ALTER TABLE client_balance_journal
    ADD CONSTRAINT client_balance_journal_performed_by_fkey
        FOREIGN KEY (performed_by) REFERENCES employees (id) ON DELETE RESTRICT;

ALTER TABLE org_balance_journal
    DROP CONSTRAINT org_balance_journal_performed_by_fkey;
ALTER TABLE org_balance_journal
    ALTER COLUMN performed_by SET NOT NULL;
ALTER TABLE org_balance_journal
    ADD CONSTRAINT org_balance_journal_performed_by_fkey
        FOREIGN KEY (performed_by) REFERENCES employees (id) ON DELETE RESTRICT;
