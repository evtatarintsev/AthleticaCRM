--liquibase formatted sql

--changeset dev:0024-create-notifications
CREATE TABLE notifications (
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id     UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL,
    body       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_org_id ON notifications (org_id);

CREATE TABLE notification_recipients (
    notification_id UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_read         BOOLEAN     NOT NULL DEFAULT false,
    read_at         TIMESTAMPTZ,
    PRIMARY KEY (notification_id, user_id)
);

CREATE INDEX idx_notification_recipients_user_id ON notification_recipients (user_id);
