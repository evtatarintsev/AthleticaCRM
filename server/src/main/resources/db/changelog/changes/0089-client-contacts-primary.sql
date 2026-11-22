--liquibase formatted sql

--changeset dev:0089-client-contacts-primary
-- Комментарий к контакту, основной контакт внутри типа и явный порядок контактов клиента.
ALTER TABLE client_contacts
    ADD COLUMN label      TEXT,
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN position   INT     NOT NULL DEFAULT 0;

UPDATE client_contacts cc
SET position = ordered.position
FROM (
    SELECT id, (row_number() OVER (PARTITION BY client_id ORDER BY created_at, id) - 1)::INT AS position
    FROM client_contacts
) ordered
WHERE ordered.id = cc.id;

UPDATE client_contacts cc
SET is_primary = true
FROM (
    SELECT DISTINCT ON (client_id, type) id
    FROM client_contacts
    ORDER BY client_id, type, position
) firsts
WHERE firsts.id = cc.id;

CREATE UNIQUE INDEX uq_client_contacts_primary
    ON client_contacts (client_id, type)
    WHERE is_primary;

--changeset dev:0089-client-contacts-one-primary splitStatements:false
-- Инвариант: у каждого типа контактов клиента ровно один основной. Проверка отложена до конца
-- транзакции, чтобы замена набора (удаление и вставка) не видела промежуточных состояний.
CREATE FUNCTION client_contacts_one_primary() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM client_contacts c
        WHERE (c.client_id, c.type) IN ((NEW.client_id, NEW.type), (OLD.client_id, OLD.type))
        GROUP BY c.client_id, c.type
        HAVING count(*) FILTER (WHERE c.is_primary) <> 1
    ) THEN
        RAISE EXCEPTION 'client contacts of one type must have exactly one primary'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_client_contacts_one_primary';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER ck_client_contacts_one_primary
    AFTER INSERT OR UPDATE OR DELETE ON client_contacts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION client_contacts_one_primary();
