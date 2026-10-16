--liquibase formatted sql

--changeset dev:0076-domain-events-retries
-- Повторы обработки доменных событий с нарастающей задержкой. Событие, исчерпавшее попытки,
-- помечается failed_at, а не processed_at: оно не выполнено и перезапускается администратором.
ALTER TABLE domain_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN failed_at       TIMESTAMPTZ;

DROP INDEX idx_domain_events_pending;

CREATE INDEX idx_domain_events_pending
    ON domain_events (next_attempt_at)
    WHERE processed_at IS NULL AND failed_at IS NULL;
