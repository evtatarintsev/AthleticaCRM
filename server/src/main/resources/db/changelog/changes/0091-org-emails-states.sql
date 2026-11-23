--liquibase formatted sql

--changeset dev:0091-org-emails-states
-- next_attempt_at: не раньше этого момента письмо берётся в отправку. Захват сдвигает его на
-- время аренды (защита от двойной отправки несколькими репликами), временная ошибка — на паузу повтора.
ALTER TABLE org_emails
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Время отправки есть только у SENT, ошибка — только у FAILED.
ALTER TABLE org_emails
    ADD CONSTRAINT ck_org_emails_state CHECK (
        (status = 'PENDING' AND sent_at IS NULL AND last_error IS NULL)
        OR (status = 'SENT' AND sent_at IS NOT NULL AND last_error IS NULL)
        OR (status = 'FAILED' AND sent_at IS NULL AND last_error IS NOT NULL)
    );

DROP INDEX idx_org_emails_pending;
CREATE INDEX idx_org_emails_pending ON org_emails (next_attempt_at)
    WHERE status = 'PENDING';
