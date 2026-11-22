--liquibase formatted sql

--changeset dev:0089-schedule-widgets
-- Виджеты расписания филиала для встраивания на сайт клуба и во ВКонтакте.
CREATE TABLE schedule_widgets (
    id               UUID         NOT NULL PRIMARY KEY,
    org_id           UUID         NOT NULL REFERENCES organizations (id),
    branch_id        UUID         NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    public_key       VARCHAR(64)  NOT NULL,
    enabled          BOOLEAN      NOT NULL,
    horizon_days     INT          NOT NULL,
    show_coaches     BOOLEAN      NOT NULL,
    show_hall        BOOLEAN      NOT NULL,
    show_disciplines BOOLEAN      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_schedule_widgets_public_key UNIQUE (public_key),
    CONSTRAINT chk_schedule_widgets_horizon CHECK (horizon_days BETWEEN 7 AND 90)
);

CREATE INDEX idx_schedule_widgets_org_branch ON schedule_widgets (org_id, branch_id);

CREATE TABLE schedule_widget_excluded_groups (
    widget_id UUID NOT NULL REFERENCES schedule_widgets (id) ON DELETE CASCADE,
    group_id  UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    PRIMARY KEY (widget_id, group_id)
);

ALTER TABLE groups ADD COLUMN shown_in_widgets BOOLEAN NOT NULL DEFAULT TRUE;

--rollback ALTER TABLE groups DROP COLUMN shown_in_widgets; DROP TABLE schedule_widget_excluded_groups; DROP TABLE schedule_widgets;
